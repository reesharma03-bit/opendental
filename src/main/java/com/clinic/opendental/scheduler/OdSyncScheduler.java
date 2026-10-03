package com.clinic.opendental.scheduler;

import com.clinic.opendental.service.Impl.OdSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Pushes changes saved in our database to Open Dental when the immediate push did
 * not go through (Open Dental unreachable, credentials wrong, ...).
 *
 * Default: every 30 seconds. Override with OD_SYNC_INTERVAL_MS; disable pushing
 * with OD_SYNC_ENABLED=false (changes then stay queued in od_sync_queue).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OdSyncScheduler {

    private final OdSyncService odSyncService;

    @Scheduled(fixedDelayString = "${od-sync.interval-ms:30000}", initialDelayString = "${od-sync.interval-ms:30000}")
    public void pushQueuedChanges() {
        try {
            int pushed = odSyncService.pushDue();
            if (pushed > 0) {
                log.info("Pushed {} queued change(s) to Open Dental", pushed);
            }
        } catch (Exception e) {
            log.error("Pushing queued changes to Open Dental failed: {}", e.getMessage(), e);
        }
    }
}
