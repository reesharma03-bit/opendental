package com.clinic.opendental.service.Impl;

import com.clinic.opendental.client.OpenDentalClient;
import com.clinic.opendental.dto.procedurelog.ProcedureLogResponse;
import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.model.ProcedureLog;
import com.clinic.opendental.model.ProcedureLogId;
import com.clinic.opendental.repository.AppointmentRepository;
import com.clinic.opendental.repository.ClinicRepository;
import com.clinic.opendental.repository.DocumentRepository;
import com.clinic.opendental.repository.OdClinicRepository;
import com.clinic.opendental.repository.ref.PatFieldRepository;
import com.clinic.opendental.repository.PatientRepository;
import com.clinic.opendental.repository.ProcedureLogRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Full syncs read and write one page at a time, so memory doesn't grow with the practice. */
class ReconciliationPagedTest {

    private final Clinic clinic = Clinic.builder().id(UUID.randomUUID()).clinicCode("C1").baseUrl("http://od").apiKey("key").build();
    private final OpenDentalClient client = mock(OpenDentalClient.class);
    private final ProcedureLogRepository logs = mock(ProcedureLogRepository.class);
    private final ReconciliationSyncService service = new ReconciliationSyncService(
            mock(ClinicRepository.class), mock(PatientRepository.class), mock(AppointmentRepository.class),
            mock(DocumentRepository.class), logs, mock(PatFieldRepository.class), mock(OdClinicRepository.class),
            null, null, null, null, client);

    private static List<ProcedureLogResponse> procs(long from, long to) {
        return LongStream.range(from, to).mapToObj(n -> ProcedureLogResponse.builder().ProcNum(n).PatNum(1L).build()).toList();
    }

    private void pages() {
        when(client.getProcedureLogs(eq(Map.of()), any(), any())).thenReturn(procs(1, 101));       // 100 = a full page
        when(client.getProcedureLogs(eq(Map.of("Offset", "100")), any(), any())).thenReturn(procs(101, 131));
    }

    @Test
    void eachPageIsWrittenBeforeTheNextIsRead() {
        pages();
        // Stored: procedure 999, no longer in Open Dental.
        when(logs.findByIdClinicId(clinic.getId())).thenReturn(List.of(ProcedureLog.builder().id(new ProcedureLogId(clinic.getId(), 999L)).build()));

        ReconciliationSyncService.Stats stats = service.reconcileProcedureLogs(clinic, Map.of());

        assertThat(stats.inserted).isEqualTo(130);
        assertThat(stats.stale).isEqualTo(1);
        InOrder order = inOrder(client, logs);
        order.verify(client).getProcedureLogs(eq(Map.of()), any(), any());
        order.verify(logs, times(100)).save(any());
        order.verify(client).getProcedureLogs(eq(Map.of("Offset", "100")), any(), any());
        order.verify(logs, times(30)).save(any());
    }

    @Test
    void aStoppedTableContinuesAfterTheRowsItHadDone() {
        when(client.getProcedureLogs(eq(Map.of("Offset", "100")), any(), any())).thenReturn(procs(101, 131));
        ReconciliationSyncService.CoreProgress progress =
                new ReconciliationSyncService.CoreProgress(() -> false, Map.of("procedurelogs", 100));

        ReconciliationSyncService.Stats stats = ReconciliationSyncService.withProgress(progress,
                () -> service.reconcileProcedureLogs(clinic, Map.of()));

        assertThat(stats.inserted).isEqualTo(30);
        assertThat(progress.done).isEqualTo(130);
        verify(client, never()).getProcedureLogs(eq(Map.of()), any(), any());
        verify(logs, never()).findByIdClinicId(any()); // deleted records wait for a complete read
    }

    @Test
    @SuppressWarnings("unchecked")
    void recordsWhosePatientIsMissingAreSkippedAndAMissingAppointmentIsUnlinked() throws Exception {
        jakarta.persistence.EntityManager em = mock(jakarta.persistence.EntityManager.class);
        java.util.function.Function<List<Long>, jakarta.persistence.TypedQuery<Long>> query = ids -> {
            jakarta.persistence.TypedQuery<Long> q = mock(jakarta.persistence.TypedQuery.class);
            when(q.setParameter(any(String.class), any())).thenReturn(q);
            when(q.getResultList()).thenReturn(ids);
            return q;
        };
        jakarta.persistence.TypedQuery<Long> patients = query.apply(List.of(1L)); // patient 2 isn't stored
        jakarta.persistence.TypedQuery<Long> appointments = query.apply(List.of()); // appointment 50 isn't either
        jakarta.persistence.TypedQuery<Long> storedIds = query.apply(List.of());
        when(em.createQuery(org.mockito.ArgumentMatchers.contains("from Patient"), eq(Long.class))).thenReturn(patients);
        when(em.createQuery(org.mockito.ArgumentMatchers.contains("from Appointment"), eq(Long.class))).thenReturn(appointments);
        when(em.createQuery(org.mockito.ArgumentMatchers.contains("from ProcedureLog"), eq(Long.class))).thenReturn(storedIds);
        java.lang.reflect.Field field = ReconciliationSyncService.class.getDeclaredField("entityManager");
        field.setAccessible(true);
        field.set(service, em);
        when(client.getProcedureLogs(eq(Map.of()), any(), any())).thenReturn(List.of(
                ProcedureLogResponse.builder().ProcNum(1L).PatNum(1L).AptNum(50L).build(),
                ProcedureLogResponse.builder().ProcNum(2L).PatNum(2L).build()));

        ReconciliationSyncService.Stats stats = service.reconcileProcedureLogs(clinic, Map.of());

        assertThat(stats.inserted).isEqualTo(1);
        assertThat(stats.failed).isEqualTo(1);
        verify(logs).save(argThat(pl -> pl.getId().getProcNum() == 1L && pl.getAptNum() == null));
        verify(logs, never()).save(argThat(pl -> pl.getId().getProcNum() == 2L));
    }

    @Test
    void aChangePullNeverReportsStaleRows() {
        when(client.getProcedureLogs(eq(Map.of("DateTStamp", "2026-10-24 08:00:00")), any(), any())).thenReturn(procs(5, 7));

        ReconciliationSyncService.Stats stats = service.reconcileProcedureLogs(clinic, Map.of("DateTStamp", "2026-10-24 08:00:00"));

        assertThat(stats.inserted).isEqualTo(2);
        verify(logs, never()).findByIdClinicId(any());
        verify(logs).findAllById(argThat(ids -> ids.iterator().hasNext()));
    }
}
