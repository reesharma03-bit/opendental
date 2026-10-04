package com.clinic.opendental.scheduler;

import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.repository.ClinicRepository;
import com.clinic.opendental.service.Impl.CoreChangeSync;
import com.clinic.opendental.service.Impl.FullSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every minute (by default): catch up patients, appointments and procedure logs with what
 * changed in Open Dental since the last pull ({@link CoreChangeSync}).
 *
 * <p>This used to re-read everything, including every patient's documents, each minute,
 * which a practice of any size can't do within Open Dental's ~1 call per second. Full
 * re-reads now happen in the nightly full sync and the Force Sync button.</p>
 *
 * Override the interval with RECONCILIATION_CRON; turn off with RECONCILIATION_ENABLED=false.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReconciliationScheduler {

    private final ClinicRepository clinicRepository;
    private final CoreChangeSync coreChangeSync;
    private final FullSyncService fullSyncService;

    @Value("${reconciliation.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${reconciliation.cron:0 * * * * *}")
    public void reconcileAll() {
        if (!enabled || fullSyncService.isRunning()) {
            return;
        }
        long start = System.currentTimeMillis();
        for (Clinic clinic : clinicRepository.findByIsActiveTrue()) {
            try {
                coreChangeSync.pull(clinic);
            } catch (Exception e) {
                log.warn("Change pull for clinic {} failed: {}", clinic.getClinicCode(), e.getMessage());
            }
        }
        log.debug("Change pull took {} ms", System.currentTimeMillis() - start);
    }
}
