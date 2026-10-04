package com.clinic.opendental.service.Impl;

import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.repository.ClinicRepository;
import com.clinic.opendental.service.Impl.OdResourceCatalog.Resource;
import com.clinic.opendental.service.Impl.SyncCursors.Cursor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Keeps the listed Open Dental resources fresh between nightly full syncs, asking Open
 * Dental only for what changed.
 *
 * <p>Every tick it claims the resources that are due (sync_cursors) and, per resource:</p>
 * <ul>
 *   <li><b>change pull</b> when Open Dental filters it by DateTStamp: only records changed
 *       since the last pull (minus a small overlap), on the tier's short interval;</li>
 *   <li><b>full read</b> otherwise, or the first time: every record, removing the ones Open
 *       Dental no longer has, then checking whether the change filter works.</li>
 * </ul>
 * Deletes are only seen by full reads: the nightly full sync covers resources that use
 * change pulls. Resources Open Dental only returns per patient are left to the nightly sync.
 */
@Service
@Slf4j
public class IncrementalSyncService {

    /** Re-read a little before the watermark: records saved in the same second, clock rounding. */
    static final Duration OVERLAP = Duration.ofMinutes(5);
    /** Resources handled per clinic per tick, so one tick never runs for long. */
    private static final int PER_TICK = 10;

    private static final Map<String, Resource> LISTED = OdResourceCatalog.LISTS.stream()
            .collect(Collectors.toMap(Resource::resource, Function.identity()));

    private final ClinicRepository clinicRepository;
    private final ResourceMirrorService mirror;
    private final SyncCursors cursors;
    private final FullSyncService fullSync;
    private final boolean enabled;
    private final AtomicBoolean ticking = new AtomicBoolean(false);

    public IncrementalSyncService(ClinicRepository clinicRepository, ResourceMirrorService mirror, SyncCursors cursors,
                                  FullSyncService fullSync, @Value("${resource-sync.enabled:true}") boolean enabled) {
        this.clinicRepository = clinicRepository;
        this.mirror = mirror;
        this.cursors = cursors;
        this.fullSync = fullSync;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${resource-sync.tick-ms:60000}", initialDelayString = "${resource-sync.tick-ms:60000}")
    public void tick() {
        // A full sync re-reads everything anyway; wait for it rather than compete for Open Dental.
        if (!enabled || fullSync.isRunning() || !ticking.compareAndSet(false, true)) {
            return;
        }
        try {
            for (Clinic clinic : clinicRepository.findByIsActiveTrue()) {
                runDue(clinic);
            }
        } catch (Exception e) {
            log.warn("Incremental sync tick failed: {}", e.getMessage());
        } finally {
            ticking.set(false);
        }
    }

    void runDue(Clinic clinic) {
        cursors.ensure(clinic);
        for (Cursor cursor : cursors.claimDue(clinic, PER_TICK)) {
            Resource resource = LISTED.get(cursor.resource());
            if (resource == null) {
                // No longer in the catalog: park it.
                cursors.succeeded(cursor, null, false, Duration.ofDays(365));
                continue;
            }
            run(clinic, resource, cursor);
        }
    }

    private void run(Clinic clinic, Resource resource, Cursor cursor) {
        long started = System.currentTimeMillis();
        if (cursor.pullChanges()) {
            try {
                ResourceMirrorService.Result result = mirror.syncChanged(clinic, resource, cursor.watermark().minus(OVERLAP));
                cursors.succeeded(cursor, result.latestChange(), true, cursor.tier().changesEvery);
                if (result.inserted() + result.updated() > 0) {
                    log.info("{} (clinic {}): {} changed record(s) from Open Dental, {} new, {} updated, in {} ms",
                            resource.resource(), clinic.getClinicCode(), result.records(), result.inserted(),
                            result.updated(), System.currentTimeMillis() - started);
                }
            } catch (HttpClientErrorException.BadRequest e) {
                // Open Dental refused the filter after all: fall back to full reads.
                cursors.failed(cursor, "DateTStamp filter refused: " + e.getMessage(), cursor.tier().changesEvery, true);
            } catch (Exception e) {
                log.warn("Change pull of {} (clinic {}) failed: {}", resource.resource(), clinic.getClinicCode(), e.getMessage());
                cursors.failed(cursor, e.getMessage(), cursor.tier().changesEvery, false);
            }
            return;
        }

        ResourceMirrorService.Result result = mirror.sync(clinic, resource, Timestamp.from(Instant.now()));
        if (!result.ok()) {
            cursors.failed(cursor, result.error(), cursor.tier().refreshEvery, false);
            return;
        }
        boolean supportsChanges = cursor.supportsChanges() || probe(clinic, resource);
        cursors.fullPassDone(clinic, resource, result, supportsChanges);
        cursors.succeeded(cursor, result.latestChange(), false,
                supportsChanges ? cursor.tier().changesEvery : cursor.tier().refreshEvery);
        log.info("{} (clinic {}): full read of {} record(s), {} new, {} updated, {} removed; change pull {}",
                resource.resource(), clinic.getClinicCode(), result.records(), result.inserted(), result.updated(),
                result.removed(), supportsChanges ? "on" : "off");
    }

    private boolean probe(Clinic clinic, Resource resource) {
        try {
            return mirror.acceptsChangedSince(clinic, resource);
        } catch (Exception e) {
            log.debug("Could not check the change filter of {}: {}", resource.resource(), e.getMessage());
            return false;
        }
    }
}
