package com.clinic.opendental.scheduler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.clinic.opendental.service.Impl.FullSyncService;
import com.clinic.opendental.service.Impl.ReconciliationSyncService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheduled Reconciliation Job.
 *
 * Even with webhooks, a scheduled job runs every night (or every few hours)
 * to catch any events that might have been missed due to temporary outages.
 *
 * Flow:
 *   2:00 AM
 *       │
 *       ▼
 *   GET patients modified today
 *       │
 *       ▼
 *   Compare with Supabase
 *       │
 *       ▼
 *   Fix any missing records
 *
 * Default cron: "0 * * * * *"  → every minute
 * Override with env var: RECONCILIATION_CRON
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReconciliationScheduler {

    private final ReconciliationSyncService reconciliationSyncService;
    private final FullSyncService fullSyncService;

    @Value("${reconciliation.enabled:true}")
    private boolean enabled;

    /**
     * Run reconciliation.
     *
     * Default: every minute.
     * Configurable via `reconciliation.cron` in application.yaml or
     * RECONCILIATION_CRON environment variable.
     */
    @Scheduled(cron = "${reconciliation.cron:0 * * * * *}")
    public void reconcileAll() {
        if (!enabled) {
            log.info("Scheduled reconciliation is disabled. Skipping.");
            return;
        }
        if (fullSyncService.isRunning()) {
            log.info("Full Open Dental sync in progress; skipping this reconciliation.");
            return;
        }

        log.info("=== Scheduled Reconciliation Started ===");
        long start = System.currentTimeMillis();

        try {
            ReconciliationSyncService.ReconciliationResult result = reconciliationSyncService.reconcileAll();
            log.info("=== Scheduled Reconciliation Complete: {}", result.message());
        } catch (Exception e) {
            log.error("Scheduled reconciliation failed: {}", e.getMessage(), e);
        }

        long duration = System.currentTimeMillis() - start;
        log.info("Scheduled reconciliation took {} ms", duration);
    }
}