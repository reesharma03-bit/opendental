package com.clinic.opendental.service.Impl;

import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.repository.ClinicRepository;
import com.clinic.opendental.service.Impl.OdResourceCatalog.Resource;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Copies Open Dental into Supabase.
 *
 * <ul>
 *   <li><b>Resource sync</b> (every 15 min by default): every resource Open Dental lists
 *       in one paged call.</li>
 *   <li><b>Full sync</b> (nightly by default, and the Force Sync button): the tables with
 *       their own schema (patients, appointments, ...), every listed resource, and the
 *       resources Open Dental only returns per patient or per parent record.</li>
 * </ul>
 *
 * Only one of these runs at a time; the per-minute reconciliation also waits for a full
 * sync to finish.
 */
@Service
@Slf4j
public class FullSyncService {

    private final ClinicRepository clinicRepository;
    private final ReconciliationSyncService reconciliationSyncService;
    private final ResourceMirrorService mirror;
    private final JdbcTemplate jdbc;
    /** The pool runs with auto-commit off, so the sync_runs rows need a transaction to stick. */
    private final TransactionTemplate tx;
    private final boolean enabled;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "od-full-sync");
        thread.setDaemon(true);
        return thread;
    });
    private volatile Run lastRun;

    public FullSyncService(ClinicRepository clinicRepository,
                           ReconciliationSyncService reconciliationSyncService,
                           ResourceMirrorService mirror,
                           JdbcTemplate jdbc,
                           PlatformTransactionManager transactionManager,
                           @Value("${resource-sync.enabled:true}") boolean enabled) {
        this.clinicRepository = clinicRepository;
        this.reconciliationSyncService = reconciliationSyncService;
        this.mirror = mirror;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
        this.enabled = enabled;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * Starts a full sync in the background (the Force Sync button).
     *
     * @return the status of the run that is now in progress (a new one, or one already running)
     */
    public Map<String, Object> startFullSync(String trigger) {
        if (!running.compareAndSet(false, true)) {
            return status();
        }
        Run run = new Run(trigger, true);
        lastRun = run;
        executor.submit(() -> execute(run));
        return status();
    }

    public Map<String, Object> status() {
        Run run = lastRun;
        return run == null ? Map.of("state", "idle") : run.snapshot();
    }

    @Scheduled(cron = "${resource-sync.cron:0 */15 * * * *}")
    public void scheduledResourceSync() {
        if (!enabled || !running.compareAndSet(false, true)) {
            return;
        }
        Run run = new Run("scheduled", false);
        lastRun = run;
        // Off the scheduler thread, which also drives the Open Dental push queue.
        executor.submit(() -> execute(run));
    }

    @Scheduled(cron = "${resource-sync.full-cron:0 0 2 * * *}")
    public void scheduledFullSync() {
        if (enabled) {
            startFullSync("nightly");
        }
    }

    /** {@code running} must already be held. */
    private void execute(Run run) {
        try {
            List<Clinic> clinics = clinicRepository.findByIsActiveTrue();
            if (clinics.isEmpty()) {
                run.fail("No active clinics configured");
                return;
            }
            List<Resource> resources = new ArrayList<>(OdResourceCatalog.LISTS);
            if (run.full) {
                resources.addAll(OdResourceCatalog.PER_PARENT);
            }
            run.total = clinics.size() * (resources.size() + (run.full ? 1 : 0));
            for (Clinic clinic : clinics) {
                syncClinic(run, clinic, resources);
            }
            run.finish();
        } catch (Exception e) {
            log.error("Open Dental sync failed: {}", e.getMessage(), e);
            run.fail(e.getMessage());
        } finally {
            running.set(false);
            log.info("Open Dental {} sync {}: {} record(s), {} failure(s)",
                    run.full ? "full" : "resource", run.state, run.records, run.failures);
        }
    }

    private void syncClinic(Run run, Clinic clinic, List<Resource> resources) {
        UUID runId = recordStart(clinic, run);
        int records = 0;
        int failures = 0;
        Timestamp runStart = Timestamp.from(Instant.now());

        if (run.full) {
            run.step(clinic, "patients, appointments, documents, procedure logs, ...");
            ReconciliationSyncService.ReconciliationResult core = reconciliationSyncService.reconcileClinic(clinic);
            run.add(clinic, "core tables", core.syncedCount(), core.failedCount(),
                    core.failedCount() > 0 ? core.message() : null);
            records += core.syncedCount();
            failures += core.failedCount();
        }
        for (Resource resource : resources) {
            run.step(clinic, resource.resource());
            ResourceMirrorService.Result result = mirror.sync(clinic, resource, runStart);
            run.add(clinic, result.resource(), result.records(), result.ok() ? 0 : 1, result.error());
            records += result.records();
            failures += result.ok() ? 0 : 1;
        }
        recordEnd(runId, records, failures);
    }

    // sync_runs is an audit log; failing to write it must not stop the sync.

    private UUID recordStart(Clinic clinic, Run run) {
        UUID id = UUID.randomUUID();
        try {
            tx.executeWithoutResult(status -> jdbc.update(
                    "INSERT INTO sync_runs (id, clinic_id, sync_type, entity_type, status, started_at) "
                            + "VALUES (?, ?, ?, 'all', 'running', now())",
                    id, clinic.getId(), run.full ? "full:" + run.trigger : "resources:" + run.trigger));
            return id;
        } catch (Exception e) {
            log.warn("Could not record sync run: {}", e.getMessage());
            return null;
        }
    }

    private void recordEnd(UUID id, int records, int failures) {
        if (id == null) {
            return;
        }
        try {
            tx.executeWithoutResult(status -> jdbc.update(
                    "UPDATE sync_runs SET total_found = ?, total_synced = ?, total_failed = ?, status = ?, "
                            + "completed_at = now() WHERE id = ?",
                    records, records, failures, failures == 0 ? "completed" : "completed_with_errors", id));
        } catch (Exception e) {
            log.warn("Could not record sync run: {}", e.getMessage());
        }
    }

    /** Progress of one run, read by the status endpoint while the run is going. */
    private static final class Run {
        final String trigger;
        final boolean full;
        final Instant startedAt = Instant.now();
        volatile Instant finishedAt;
        volatile String state = "running";
        volatile String currentStep = "starting";
        volatile String error;
        volatile int total;
        volatile int done;
        volatile int records;
        volatile int failures;
        final List<Map<String, Object>> results = new ArrayList<>();

        Run(String trigger, boolean full) {
            this.trigger = trigger;
            this.full = full;
        }

        void step(Clinic clinic, String what) {
            currentStep = clinic.getClinicCode() + ": " + what;
        }

        synchronized void add(Clinic clinic, String resource, int count, int failed, String problem) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("clinic", clinic.getClinicCode());
            result.put("resource", resource);
            result.put("records", count);
            result.put("status", failed == 0 ? "ok" : "failed");
            if (problem != null) {
                result.put("error", problem);
            }
            results.add(result);
            done++;
            records += count;
            failures += failed;
        }

        void finish() {
            finishedAt = Instant.now();
            currentStep = null;
            state = failures == 0 ? "completed" : "completed_with_errors";
        }

        void fail(String message) {
            finishedAt = Instant.now();
            error = message;
            state = "failed";
        }

        synchronized Map<String, Object> snapshot() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("state", state);
            map.put("type", full ? "full" : "resources");
            map.put("trigger", trigger);
            map.put("startedAt", startedAt.toString());
            map.put("finishedAt", finishedAt == null ? null : finishedAt.toString());
            map.put("currentStep", currentStep);
            map.put("done", done);
            map.put("total", total);
            map.put("records", records);
            map.put("failures", failures);
            map.put("error", error);
            map.put("results", new ArrayList<>(results));
            return map;
        }
    }
}
