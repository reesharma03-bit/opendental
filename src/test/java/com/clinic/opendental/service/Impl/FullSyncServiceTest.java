package com.clinic.opendental.service.Impl;

import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.repository.ClinicRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The Force Sync button: one background run at a time, covering every table and resource. */
class FullSyncServiceTest {

    private static final Clinic CLINIC = Clinic.builder().id(UUID.randomUUID()).clinicCode("CLINIC_A").build();

    @Test
    void forceSyncCopiesCoreTablesAndEveryResource() throws Exception {
        ClinicRepository clinics = mock(ClinicRepository.class);
        when(clinics.findByIsActiveTrue()).thenReturn(List.of(CLINIC));
        ReconciliationSyncService reconciliation = mock(ReconciliationSyncService.class);
        when(reconciliation.reconcileClinic(CLINIC))
                .thenReturn(new ReconciliationSyncService.ReconciliationResult(12, 0, 3, "ok"));
        ResourceMirrorService mirror = mock(ResourceMirrorService.class);
        when(mirror.sync(any(), any(), any())).thenAnswer(inv -> new ResourceMirrorService.Result(
                ((OdResourceCatalog.Resource) inv.getArgument(1)).resource(), 2, 0, null));
        FullSyncService service = new FullSyncService(clinics, reconciliation, mirror, mock(JdbcTemplate.class), mock(org.springframework.transaction.PlatformTransactionManager.class), true);

        Map<String, Object> started = service.startFullSync("force");
        assertThat(started.get("state")).isEqualTo("running");
        Map<String, Object> done = waitUntilFinished(service);

        int resources = OdResourceCatalog.LISTS.size() + OdResourceCatalog.PER_PARENT.size();
        assertThat(done.get("state")).isEqualTo("completed");
        assertThat(done.get("done")).isEqualTo(resources + 1);
        assertThat(done.get("records")).isEqualTo(12 + resources * 2);
        verify(reconciliation).reconcileClinic(CLINIC);
        verify(mirror, times(resources)).sync(eq(CLINIC), any(), any());
    }

    @Test
    void secondClickWhileRunningJoinsTheRunningSync() throws Exception {
        ClinicRepository clinics = mock(ClinicRepository.class);
        CountDownLatch release = new CountDownLatch(1);
        when(clinics.findByIsActiveTrue()).thenAnswer(inv -> {
            release.await(5, TimeUnit.SECONDS);
            return List.of();
        });
        FullSyncService service = new FullSyncService(clinics, mock(ReconciliationSyncService.class),
                mock(ResourceMirrorService.class), mock(JdbcTemplate.class), mock(org.springframework.transaction.PlatformTransactionManager.class), true);

        service.startFullSync("force");
        service.startFullSync("force");
        release.countDown();
        Map<String, Object> done = waitUntilFinished(service);

        verify(clinics, times(1)).findByIsActiveTrue();
        assertThat(done.get("state")).isEqualTo("failed");
        assertThat(done.get("error")).isEqualTo("No active clinics configured");
    }

    @Test
    void scheduledResourceSyncSkipsPerPatientResources() throws Exception {
        ClinicRepository clinics = mock(ClinicRepository.class);
        when(clinics.findByIsActiveTrue()).thenReturn(List.of(CLINIC));
        ReconciliationSyncService reconciliation = mock(ReconciliationSyncService.class);
        ResourceMirrorService mirror = mock(ResourceMirrorService.class);
        when(mirror.sync(any(), any(), any())).thenReturn(new ResourceMirrorService.Result("x", 0, 0, null));
        FullSyncService service = new FullSyncService(clinics, reconciliation, mirror, mock(JdbcTemplate.class), mock(org.springframework.transaction.PlatformTransactionManager.class), true);

        service.scheduledResourceSync();
        waitUntilFinished(service);

        verify(mirror, times(OdResourceCatalog.LISTS.size())).sync(any(), any(), any());
        verifyNoInteractions(reconciliation);
    }

    private static Map<String, Object> waitUntilFinished(FullSyncService service) throws InterruptedException {
        for (int i = 0; i < 200 && service.isRunning(); i++) {
            Thread.sleep(25);
        }
        assertThat(service.isRunning()).isFalse();
        return service.status();
    }
}
