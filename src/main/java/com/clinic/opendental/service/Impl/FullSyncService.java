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
    /**
     * Where a per-patient / per-parent resource stopped part-way, per clinic: a continued sync
     * picks it up after the last patient done. In memory only: after a restart of the backend that
     * one resource starts again from its first patient (the finished resources are still skipped).
     */
    private final Map<UUID, Partial> partials = new java.util.concurrent.ConcurrentHashMap<>();

    private record Partial(String resource, Long lastParent) {
    }

    /** Triggers that continue an interrupted sync instead of starting over. */
    private static boolean continues(String trigger) {
        return "connect".equals(trigger) || "resume".equals(trigger);
    }

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
            Run current = lastRun;
            // Already being synced by the running sync (and not on its way out): nothing to add.
            boolean covered = current != null && clinicId != null && stopReason == null && !skipped.contains(clinicId)
                    && (clinicId.equals(current.clinicId) || current.clinicId == null);
            // Reconnected while its stopped sync is still finishing: it continues right after.
            if (clinicId != null && !covered && !waiting.contains(clinicId)) {
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

    /**
     * Sync was resumed: every sync that was stopped part-way (by the pause, or by a restart of the
     * backend) continues from where it stopped. What it had finished is not copied again.
     */
    public void resumeInterrupted() {
        if (control.paused()) return;
        for (Clinic clinic : clinicRepository.findByIsActiveTrue()) {
            if (interruptedSince(clinic.getId()) == null) continue;
            try {
                startFullSync("resume", clinic.getId());
            } catch (Exception e) {
                log.warn("Could not continue the sync of {}: {}", clinic.getClinicCode(), e.getMessage());
            }
        }
    }

    /**
     * When the clinic's interrupted full sync began: the first full sync since its last finished
     * one that was stopped (or cut off by a restart). Null when there is nothing to continue.
     */
    Timestamp interruptedSince(UUID clinicId) {
        try {
            List<Timestamp> found = jdbc.queryForList("""
                    SELECT min(started_at) FROM sync_runs
                    WHERE clinic_id = ? AND entity_type = 'all' AND sync_type LIKE 'full:%' AND status IN ('stopped', 'running')
                      AND started_at > coalesce((SELECT max(started_at) FROM sync_runs
                                                 WHERE clinic_id = ? AND entity_type = 'all' AND sync_type LIKE 'full:%'
                                                   AND status IN ('completed', 'completed_with_errors')), '-infinity')
                    """, Timestamp.class, clinicId, clinicId);
            return found.isEmpty() ? null : found.get(0);
        } catch (Exception e) {
            log.debug("Could not read interrupted syncs: {}", e.getMessage());
            return null;
        }
    }

    /** What the interrupted sync since {@code since} already finished ("core tables" and resource names). */
    java.util.Set<String> finishedSince(UUID clinicId, Timestamp since) {
        try {
            return new java.util.HashSet<>(jdbc.queryForList("""
                    SELECT DISTINCT resource FROM sync_runs
                    WHERE clinic_id = ? AND sync_type = 'resource' AND status = 'completed' AND started_at >= ?
                    """, String.class, clinicId, since));
        } catch (Exception e) {
            log.debug("Could not read finished resources: {}", e.getMessage());
            return java.util.Set.of();
        }
    }

    /**
     * How far the interrupted sync since {@code since} got into each core table it didn't finish
     * (rows read and written), so it continues after them instead of reading them again.
     */
    Map<String, Integer> coreRowsDoneSince(UUID clinicId, Timestamp since) {
        try {
            Map<String, Integer> done = new java.util.HashMap<>();
            jdbc.query("""
                    SELECT resource, max(rows_fetched) AS rows_done FROM sync_runs
                    WHERE clinic_id = ? AND sync_type = 'resource' AND status = 'stopped' AND resource LIKE 'core:%' AND started_at >= ?
                    GROUP BY resource
                    """, rs -> {
                done.put(rs.getString("resource").substring(CORE_TABLE.length()), rs.getInt("rows_done"));
            }, clinicId, since);
            return done;
        } catch (Exception e) {
            log.debug("Could not read where the core tables stopped: {}", e.getMessage());
            return Map.of();
        }
    }

    /** A core table stopped part-way: the rows done so far, where the continued sync carries on. */
    private void recordCoreRowsDone(Clinic clinic, String table, int rowsDone) {
        try {
            tx.executeWithoutResult(status -> jdbc.update("""
                            INSERT INTO sync_runs (id, clinic_id, sync_type, entity_type, resource, mode, status, rows_fetched,
                                started_at, completed_at)
                            VALUES (?, ?, 'resource', ?, ?, 'full', 'stopped', ?, now(), now())
                            """,
                    UUID.randomUUID(), clinic.getId(), CORE_TABLE + table, CORE_TABLE + table, rowsDone));
        } catch (Exception e) {
            log.warn("Could not record where {} stopped: {}", table, e.getMessage());
        }
    }

    /** A sync cut off by a restart of the backend continues once it is up again. */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void continueAfterRestart() {
        if (!enabled) return;
        try {
            resumeInterrupted();
        } catch (Exception e) {
            log.warn("Could not continue interrupted syncs: {}", e.getMessage());
        }
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
            chosen.forEach((clinic, list) -> run.totals.put(clinic.getClinicCode(), list.size() + (run.full ? 1 : 0)));
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
        // Reconnected or resumed: continue the interrupted sync, skipping what it finished.
        java.util.Set<String> finished = java.util.Set.of();
        Map<String, Integer> resumeAt = Map.of();
        if (continues(run.trigger)) {
            Timestamp since = interruptedSince(clinic.getId());
            if (since != null) {
                finished = finishedSince(clinic.getId(), since);
                resumeAt = coreRowsDoneSince(clinic.getId(), since);
            }
        } else {
            partials.remove(clinic.getId());
        }
        Partial partial = continues(run.trigger) ? partials.get(clinic.getId()) : null;

        UUID runId = recordStart(clinic, run);
        int records = 0;
        int failures = 0;
        Timestamp runStart = Timestamp.from(Instant.now());
        java.util.function.BooleanSupplier stopNow =
                () -> control.paused() || stopReason != null || skipped.contains(clinic.getId());

        if (run.full) {
            if (finished.contains(CORE)) {
                run.skip(clinic, CORE);
            } else {
                run.step(clinic, "patients, appointments, documents, procedure logs, ...");
                // Stops when paused or disconnected; each finished table is recorded, so a continued
                // sync skips it (it is the step's slowest part on a large practice).
                java.util.Set<String> done = finished;
                ReconciliationSyncService.CoreProgress progress = new ReconciliationSyncService.CoreProgress(stopNow, resumeAt);
                run.coreProgress = progress;
                ReconciliationSyncService.ReconciliationResult core;
                try {
                    core = ReconciliationSyncService.withProgress(progress, () -> reconciliationSyncService.reconcileClinic(clinic,
                            table -> {
                                boolean included = !control.skips(clinic, table) && !done.contains(CORE_TABLE + table);
                                if (included) {
                                    run.coreTable = table;
                                    progress.reset();
                                }
                                return included;
                            },
                            (table, stats) -> recordResource(clinic, run, new ResourceMirrorService.Result(CORE_TABLE + table,
                                    stats.processed(), stats.failed, stats.failed > 0 ? stats.failed + " record(s) failed" : null,
                                    stats.inserted, stats.updated, 0, null))));
                } catch (SyncStoppedException e) {
                    if (run.coreTable != null && progress.done > 0) recordCoreRowsDone(clinic, run.coreTable, progress.done);
                    recordEnd(runId, records, failures, "stopped");
                    return !(control.paused() || stopReason != null);
                } finally {
                    run.coreTable = null;
                    run.coreProgress = null;
                }
                run.add(clinic, CORE, core.syncedCount(), core.failedCount(),
                        core.failedCount() > 0 ? core.message() : null);
                recordResource(clinic, run, new ResourceMirrorService.Result(CORE, core.syncedCount(), core.failedCount(),
                        core.failedCount() > 0 ? core.message() : null));
                records += core.syncedCount();
                failures += core.failedCount();
            }
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
            if (finished.contains(resource.resource())) {
                run.skip(clinic, resource.resource());
                continue;
            }
            run.step(clinic, resource.resource());
            Long startAfter = partial != null && partial.resource().equals(resource.resource()) ? partial.lastParent() : null;
            ResourceMirrorService.Progress progress = new ResourceMirrorService.Progress(stopNow, startAfter);
            run.progress = progress;
            // Nightly: per-patient resources for recently active patients plus a rolling seventh of
            // the rest (Open Dental's ~1 call/second can't cover every patient every night).
            // Force Sync: everyone.
            ResourceMirrorService.Result result = mirror.sync(clinic, resource, runStart,
                    "nightly".equals(run.trigger) ? ResourceMirrorService.Scope.RECENT : ResourceMirrorService.Scope.ALL, progress);
            run.progress = null;
            if (progress.stopped) {
                // Stopped part-way: kept as is; the next continued sync carries on after the last patient done.
                if (!resource.isList() && progress.lastParent != null) {
                    partials.put(clinic.getId(), new Partial(resource.resource(), progress.lastParent));
                }
                records += result.records();
                recordEnd(runId, records, failures, "stopped");
                return !(control.paused() || stopReason != null);
            }
            partials.remove(clinic.getId());
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

    /** The step that copies the tables with their own schema (patients, appointments, ...). */
    static final String CORE = "core tables";

    /** sync_runs resource of one finished table of that step, e.g. "core:procedurelogs". */
    static final String CORE_TABLE = "core:";

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
        /** Steps skipped because an interrupted sync had finished them. */
        volatile int resumed;
        /** Core table being copied in the first step (patients, appointments, ...). */
        volatile String coreTable;
        /** Rows of that core table read from Open Dental and written to Supabase so far. */
        volatile ReconciliationSyncService.CoreProgress coreProgress;
        /** Progress inside the resource being copied. */
        volatile ResourceMirrorService.Progress progress;
        final Map<String, Integer> totals = new java.util.concurrent.ConcurrentHashMap<>();
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

        /** Finished by the interrupted sync this one continues. */
        synchronized void skip(Clinic clinic, String resource) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("clinic", clinic.getClinicCode());
            result.put("resource", resource);
            result.put("records", 0);
            result.put("status", "done-earlier");
            results.add(result);
            done++;
            resumed++;
        }

        /** Live detail of the step: core table, or patients done in a per-patient resource. */
        void live(Map<String, Object> map) {
            map.put("resumed", resumed);
            map.put("coreTable", coreTable);
            ReconciliationSyncService.CoreProgress core = coreProgress;
            if (coreTable != null && core != null && "running".equals(state)) {
                map.put("stepRecords", core.read);
                map.put("stepWritten", core.written);
            }
            ResourceMirrorService.Progress p = progress;
            if (p != null && "running".equals(state)) {
                map.put("stepRecords", p.records);
                map.put("parentsDone", p.parentsTotal > 0 ? p.parentsDone : null);
                map.put("parentsTotal", p.parentsTotal > 0 ? p.parentsTotal : null);
            }
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
            map.put("total", totals.get(clinicCode));
            map.put("records", mine.stream().mapToInt(r -> ((Number) r.get("records")).intValue()).sum());
            map.put("failures", (int) mine.stream().filter(r -> "failed".equals(r.get("status"))).count());
            map.put("error", error);
            map.put("results", mine);
            if (map.get("currentStep") != null) {
                live(map);
            } else {
                map.put("resumed", (int) mine.stream().filter(r -> "done-earlier".equals(r.get("status"))).count());
            }
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
            live(map);
            return map;
        }
    }
}
