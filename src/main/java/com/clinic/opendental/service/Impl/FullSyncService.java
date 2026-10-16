package com.clinic.opendental.service.Impl;

import com.clinic.opendental.exception.ApiException;
import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.repository.ClinicRepository;
import com.clinic.opendental.service.Impl.OdResourceCatalog.Resource;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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
 *   <li><b>Full sync</b> (nightly by default, and the Force Sync button): the tables with
 *       their own schema (patients, appointments, ...), every listed resource, and the
 *       resources Open Dental only returns per patient or per parent record. It is the
 *       only place deletes in Open Dental are noticed for resources using change pulls.</li>
 *   <li>Between full syncs, {@link IncrementalSyncService} keeps the listed resources
 *       fresh, asking Open Dental only for what changed.</li>
 * </ul>
 *
 * Only one of these runs at a time; the per-minute reconciliation also waits for a full
 * sync to finish. Each clinic syncs only what it has chosen ({@link SyncControl}); pausing
 * stops a running full sync after the resource it is on.
 */
@Service
@Slf4j
public class FullSyncService {

    private final ClinicRepository clinicRepository;
    private final ReconciliationSyncService reconciliationSyncService;
    private final ResourceMirrorService mirror;
    private final SyncCursors cursors;
    private final JdbcTemplate jdbc;
    /** The pool runs with auto-commit off, so the sync_runs rows need a transaction to stick. */
    private final TransactionTemplate tx;
    private final SyncControl control;
    private final boolean enabled;

    private final AtomicBoolean running = new AtomicBoolean(false);
    /** Set by {@link #stop}: the running sync ends after the resource it is on. */
    private volatile String stopReason;
    /** Clinics the running sync must leave alone from now on (disconnected while it runs). */
    private final java.util.Set<UUID> skipped = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** One-clinic syncs asked for while another sync was running; started when it ends. */
    private final java.util.Queue<UUID> waiting = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "od-full-sync");
        thread.setDaemon(true);
        return thread;
    });
    private volatile Run lastRun;

    public FullSyncService(ClinicRepository clinicRepository,
                           ReconciliationSyncService reconciliationSyncService,
                           ResourceMirrorService mirror,
                           SyncCursors cursors,
                           JdbcTemplate jdbc,
                           PlatformTransactionManager transactionManager,
                           SyncControl control,
                           @Value("${resource-sync.enabled:true}") boolean enabled) {
        this.clinicRepository = clinicRepository;
        this.reconciliationSyncService = reconciliationSyncService;
        this.mirror = mirror;
        this.cursors = cursors;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
        this.control = control;
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
     * @throws ApiException 409 while sync is paused
     */
    public Map<String, Object> startFullSync(String trigger) {
        return startFullSync(trigger, null);
    }

    /**
     * As {@link #startFullSync(String)}, for one clinic only ({@code clinicId}; null = every
     * connected clinic). A one-clinic sync asked for while another sync runs (a newly registered
     * clinic, say) waits and starts as soon as that one ends.
     */
    public Map<String, Object> startFullSync(String trigger, UUID clinicId) {
        if (control.paused()) {
            throw new ApiException(HttpStatus.CONFLICT, "Sync is paused. Resume it on the Sync settings screen first.");
        }
        if (!running.compareAndSet(false, true)) {
            if (clinicId != null && !clinicId.equals(lastRun == null ? null : lastRun.clinicId) && !waiting.contains(clinicId)) {
                waiting.add(clinicId);
            }
            return status();
        }
        stopReason = null;
        skipped.clear();
        Run run = new Run(trigger, true, clinicId);
        lastRun = run;
        executor.submit(() -> execute(run));
        return status();
    }

    /** Stops a running full sync after the resource it is on. */
    public void stop(String reason) {
        if (running.get()) {
            stopReason = reason;
        }
    }

    /**
     * A clinic was disconnected: a sync of just that clinic stops after the resource it is on;
     * a sync of every clinic leaves it out from now on. A waiting sync of it is dropped.
     */
    public void stop(UUID clinicId, String reason) {
        waiting.remove(clinicId);
        Run run = lastRun;
        if (!running.get() || run == null) return;
        if (clinicId.equals(run.clinicId)) {
            stopReason = reason;
        } else {
            skipped.add(clinicId);
        }
    }

    /**
     * What {@code viewer}'s clinic may see: its own part of a sync (results, step, progress), that
     * its sync is waiting for another clinic's to end, or nothing (another clinic's sync).
     */
    public Map<String, Object> status(Clinic viewer) {
        if (viewer == null) return status();
        if (waiting.contains(viewer.getId())) return Map.of("state", "queued");
        Run run = lastRun;
        if (run == null || (run.clinicId != null && !run.clinicId.equals(viewer.getId()))) {
            return Map.of("state", "idle");
        }
        return run.snapshotFor(viewer.getClinicCode(), viewer.getId().equals(run.clinicId));
    }

    public Map<String, Object> status() {
        Run run = lastRun;
        return run == null ? Map.of("state", "idle") : run.snapshot();
    }

    @Scheduled(cron = "${resource-sync.full-cron:0 0 2 * * *}")
    public void scheduledFullSync() {
        if (enabled && !control.paused()) {
            startFullSync("nightly");
        }
    }

    /** {@code running} must already be held. */
    private void execute(Run run) {
        try {
            List<Clinic> clinics = clinicRepository.findByIsActiveTrue().stream()
                    .filter(c -> run.clinicId == null || run.clinicId.equals(c.getId()))
                    .toList();
            if (clinics.isEmpty()) {
                run.fail(run.clinicId == null ? "No connected clinics" : "This clinic isn't connected");
                return;
            }
            List<Resource> resources = new ArrayList<>(OdResourceCatalog.LISTS);
            if (run.full) {
                resources.addAll(OdResourceCatalog.PER_PARENT);
            }
            Map<Clinic, List<Resource>> chosen = new LinkedHashMap<>();
            for (Clinic clinic : clinics) {
                chosen.put(clinic, resources.stream().filter(r -> !control.skips(clinic, r.resource())).toList());
            }
            run.total = chosen.values().stream().mapToInt(r -> r.size() + (run.full ? 1 : 0)).sum();
            for (Map.Entry<Clinic, List<Resource>> entry : chosen.entrySet()) {
                if (!syncClinic(run, entry.getKey(), entry.getValue())) {
                    run.stop(stopReason != null ? stopReason : "Paused" + pausedBy());
                    return;
                }
            }
            run.finish();
        } catch (Exception e) {
            log.error("Open Dental sync failed: {}", e.getMessage(), e);
            run.fail(e.getMessage());
        } finally {
            running.set(false);
            log.info("Open Dental {} sync {}: {} record(s), {} failure(s)",
                    run.full ? "full" : "resource", run.state, run.records, run.failures);
            UUID next = waiting.poll();
            if (next != null) {
                try {
                    startFullSync("connect", next);
                } catch (Exception e) {
                    log.warn("Could not start the waiting sync of clinic {}: {}", next, e.getMessage());
                }
            }
        }
    }

    /** @return false when sync was paused part-way */
    private boolean syncClinic(Run run, Clinic clinic, List<Resource> resources) {
        UUID runId = recordStart(clinic, run);
        int records = 0;
        int failures = 0;
        Timestamp runStart = Timestamp.from(Instant.now());

        if (run.full) {
            run.step(clinic, "patients, appointments, documents, procedure logs, ...");
            ReconciliationSyncService.ReconciliationResult core =
                    reconciliationSyncService.reconcileClinic(clinic, table -> !control.skips(clinic, table));
            run.add(clinic, "core tables", core.syncedCount(), core.failedCount(),
                    core.failedCount() > 0 ? core.message() : null);
            records += core.syncedCount();
            failures += core.failedCount();
        }
        for (Resource resource : resources) {
            if (control.paused() || stopReason != null) {
                recordEnd(runId, records, failures, "stopped");
                return false;
            }
            if (skipped.contains(clinic.getId())) {
                recordEnd(runId, records, failures, "stopped");
                return true; // disconnected: carry on with the other clinics
            }
            run.step(clinic, resource.resource());
            // Nightly: per-patient resources for recently active patients plus a rolling seventh of
            // the rest (Open Dental's ~1 call/second can't cover every patient every night).
            // Force Sync: everyone.
            ResourceMirrorService.Result result = mirror.sync(clinic, resource, runStart,
                    "nightly".equals(run.trigger) ? ResourceMirrorService.Scope.RECENT : ResourceMirrorService.Scope.ALL);
            run.add(clinic, result.resource(), result.records(), result.ok() ? 0 : 1, result.error());
            recordResource(clinic, run, result);
            if (resource.isList() && result.ok()) {
                updateCursor(clinic, resource, result);
            }
            records += result.records();
            failures += result.ok() ? 0 : 1;
        }
        recordEnd(runId, records, failures, failures == 0 ? "completed" : "completed_with_errors");
        return true;
    }

    private String pausedBy() {
        String by = control.state().pausedBy();
        return by == null ? "" : " by " + by;
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

    /** A clean full read restarts the resource's change pull from the newest change it saw. */
    private void updateCursor(Clinic clinic, Resource resource, ResourceMirrorService.Result result) {
        try {
            cursors.fullPassDone(clinic, resource, result, mirror.acceptsChangedSince(clinic, resource));
        } catch (Exception e) {
            log.warn("Could not update the sync cursor of {}: {}", resource.resource(), e.getMessage());
        }
    }

    /** One row per resource: what Open Dental returned and what was actually written. */
    private void recordResource(Clinic clinic, Run run, ResourceMirrorService.Result result) {
        try {
            tx.executeWithoutResult(status -> jdbc.update("""
                            INSERT INTO sync_runs (id, clinic_id, sync_type, entity_type, resource, mode, status,
                                total_found, total_synced, total_failed, rows_fetched, rows_inserted, rows_updated,
                                rows_unchanged, rows_deleted, error_message, started_at, completed_at)
                            VALUES (?, ?, 'resource', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                            """,
                    UUID.randomUUID(), clinic.getId(), result.resource(), result.resource(),
                    run.full ? "full" : "resources", result.ok() ? "completed" : "failed",
                    result.records(), result.inserted() + result.updated(), result.ok() ? 0 : 1,
                    result.records(), result.inserted(), result.updated(), result.unchanged(), result.removed(),
                    result.error() == null ? "" : result.error()));
        } catch (Exception e) {
            log.warn("Could not record sync of {}: {}", result.resource(), e.getMessage());
        }
    }

    private void recordEnd(UUID id, int records, int failures, String outcome) {
        if (id == null) {
            return;
        }
        try {
            tx.executeWithoutResult(status -> jdbc.update(
                    "UPDATE sync_runs SET total_found = ?, total_synced = ?, total_failed = ?, status = ?, "
                            + "completed_at = now() WHERE id = ?",
                    records, records, failures, outcome, id));
        } catch (Exception e) {
            log.warn("Could not record sync run: {}", e.getMessage());
        }
    }

    /** Progress of one run, read by the status endpoint while the run is going. */
    private static final class Run {
        final String trigger;
        final boolean full;
        /** The one clinic this run syncs; null = every connected clinic. */
        final UUID clinicId;
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

        Run(String trigger, boolean full, UUID clinicId) {
            this.trigger = trigger;
            this.full = full;
            this.clinicId = clinicId;
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

        /** Paused part-way: what was synced is kept, the rest is picked up after resuming. */
        void stop(String message) {
            finishedAt = Instant.now();
            currentStep = null;
            error = message;
            state = "paused";
        }

        void fail(String message) {
            finishedAt = Instant.now();
            error = message;
            state = "failed";
        }

        /** The part of this run about one clinic (a run of every clinic shows only that clinic's results). */
        synchronized Map<String, Object> snapshotFor(String clinicCode, boolean wholeRun) {
            if (wholeRun) return snapshot();
            List<Map<String, Object>> mine = results.stream().filter(r -> clinicCode.equals(r.get("clinic"))).toList();
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("state", state);
            map.put("type", full ? "full" : "resources");
            map.put("trigger", trigger);
            map.put("startedAt", startedAt.toString());
            map.put("finishedAt", finishedAt == null ? null : finishedAt.toString());
            map.put("currentStep", currentStep != null && currentStep.startsWith(clinicCode + ":") ? currentStep : null);
            map.put("done", mine.size());
            map.put("total", null);
            map.put("records", mine.stream().mapToInt(r -> ((Number) r.get("records")).intValue()).sum());
            map.put("failures", (int) mine.stream().filter(r -> "failed".equals(r.get("status"))).count());
            map.put("error", error);
            map.put("results", mine);
            return map;
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
