package com.clinic.opendental.service.Impl;

import com.clinic.opendental.client.OpenDentalClient;
import com.clinic.opendental.dto.patient.PatientResponse;
import com.clinic.opendental.model.Clinic;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The every-minute catch-up asks Open Dental only for changes, and never skips past a failure. */
class CoreChangeSyncTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Clinic CLINIC = Clinic.builder().id(UUID.randomUUID()).clinicCode("A").baseUrl("http://od").apiKey("k").build();

    private ReconciliationSyncService reconciliation;
    private OpenDentalClient client;
    private SyncCursors cursors;
    private JdbcTemplate jdbc;
    private CoreChangeSync sync;

    @BeforeEach
    void setUp() {
        reconciliation = mock(ReconciliationSyncService.class);
        client = mock(OpenDentalClient.class);
        cursors = mock(SyncCursors.class);
        jdbc = mock(JdbcTemplate.class);
        sync = new CoreChangeSync(reconciliation, client, cursors, jdbc);
        when(reconciliation.reconcileAppointments(any(), anyMap())).thenReturn(new ReconciliationSyncService.Stats());
        when(reconciliation.reconcileProcedureLogs(any(), anyMap())).thenReturn(new ReconciliationSyncService.Stats());
        when(reconciliation.reconcilePatients(any(), anyList())).thenReturn(new ReconciliationSyncService.Stats());
        when(reconciliation.reconcileProviders(any())).thenReturn(new ReconciliationSyncService.Stats());
        when(reconciliation.reconcileProviders(any(), anyMap())).thenReturn(new ReconciliationSyncService.Stats());
        when(client.getRaw(eq("/patients/Simple"), anyMap(), any(), any())).thenReturn(JSON.createArrayNode());
    }

    @Test
    void asksOnlyForChangesSinceTheWatermarkMinusTheOverlap() {
        when(cursors.coreWatermark(CLINIC, "appointments")).thenReturn(LocalDateTime.of(2026, 10, 4, 10, 0, 0));
        ReconciliationSyncService.Stats stats = new ReconciliationSyncService.Stats();
        stats.seen("2026-10-04 10:07:30");
        when(reconciliation.reconcileAppointments(any(), anyMap())).thenReturn(stats);

        sync.pull(CLINIC);

        verify(reconciliation).reconcileAppointments(CLINIC, Map.of("DateTStamp", "2026-10-04 09:55:00"));
        verify(cursors).coreWatermark(CLINIC, "appointments", LocalDateTime.of(2026, 10, 4, 10, 7, 30));
    }

    @Test
    void aFailedPullKeepsTheWatermark() {
        when(cursors.coreWatermark(CLINIC, "procedurelogs")).thenReturn(LocalDateTime.of(2026, 10, 4, 10, 0, 0));
        ReconciliationSyncService.Stats failed = new ReconciliationSyncService.Stats();
        failed.failed = 1;
        failed.seen("2026-10-04 10:30:00");
        when(reconciliation.reconcileProcedureLogs(any(), anyMap())).thenReturn(failed);

        sync.pull(CLINIC);

        verify(cursors, never()).coreWatermark(eq(CLINIC), eq("procedurelogs"), any(LocalDateTime.class));
    }

    @Test
    void firstPullStartsFromTheNewestStoredChangeAndAnEmptyTableWaitsForTheFullSync() {
        when(jdbc.queryForObject(contains("FROM appointments"), eq(Timestamp.class), any()))
                .thenReturn(Timestamp.valueOf("2026-10-01 08:00:00"));
        when(jdbc.queryForObject(contains("FROM procedure_logs"), eq(Timestamp.class), any())).thenReturn(null);

        sync.pull(CLINIC);

        verify(reconciliation).reconcileAppointments(CLINIC, Map.of("DateTStamp", "2026-10-01 07:55:00"));
        verify(reconciliation, never()).reconcileProcedureLogs(any(), anyMap());
    }

    @Test
    void changedPatientsAreReadOldestFirstAndCappedPerPull() throws Exception {
        when(cursors.coreWatermark(CLINIC, "patients")).thenReturn(LocalDateTime.of(2026, 10, 4, 9, 0, 0));
        ArrayNode simple = JSON.createArrayNode();
        for (int i = CoreChangeSync.MAX_PATIENTS_PER_PULL + 20; i >= 1; i--) { // newest first, to check sorting
            simple.add(JSON.createObjectNode().put("PatNum", i).put("DateTStamp", String.format("2026-10-04 10:%02d:%02d", i / 60, i % 60)));
        }
        when(client.getRaw(eq("/patients/Simple"), anyMap(), any(), any())).thenReturn(simple);
        when(client.getRaw(startsWith("/patients/"), eq(Map.of()), any(), any()))
                .thenAnswer(inv -> JSON.createObjectNode().put("PatNum", Long.parseLong(((String) inv.getArgument(0)).substring(10))));

        sync.pull(CLINIC);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PatientResponse>> read = ArgumentCaptor.forClass(List.class);
        // Saved in batches as they are read, not all at the end.
        int batches = CoreChangeSync.MAX_PATIENTS_PER_PULL / CoreChangeSync.SAVE_BATCH_SIZE;
        verify(reconciliation, times(batches)).reconcilePatients(eq(CLINIC), read.capture());
        assertThat(read.getAllValues()).allSatisfy(batch -> assertThat(batch).hasSize(CoreChangeSync.SAVE_BATCH_SIZE));
        assertThat(read.getAllValues().get(0).get(0).getPatNum()).isEqualTo(1L);
        // The checkpoint follows each saved batch, up to the last patient read (number 100), not past the 20 left.
        verify(cursors).coreWatermark(CLINIC, "patients", LocalDateTime.of(2026, 10, 4, 10, 0, 25));
        verify(cursors).coreWatermark(CLINIC, "patients", LocalDateTime.of(2026, 10, 4, 10, 1, 40));
    }

    @Test
    void aBatchThatFailsToSaveKeepsTheCheckpointOfTheBatchesBeforeIt() throws Exception {
        when(cursors.coreWatermark(CLINIC, "patients")).thenReturn(LocalDateTime.of(2026, 10, 4, 9, 0, 0));
        ArrayNode simple = JSON.createArrayNode();
        for (int i = 1; i <= 2 * CoreChangeSync.SAVE_BATCH_SIZE; i++) {
            simple.add(JSON.createObjectNode().put("PatNum", i).put("DateTStamp", String.format("2026-10-04 10:00:%02d", i)));
        }
        when(client.getRaw(eq("/patients/Simple"), anyMap(), any(), any())).thenReturn(simple);
        when(client.getRaw(startsWith("/patients/"), eq(Map.of()), any(), any()))
                .thenAnswer(inv -> JSON.createObjectNode().put("PatNum", Long.parseLong(((String) inv.getArgument(0)).substring(10))));
        ReconciliationSyncService.Stats failed = new ReconciliationSyncService.Stats();
        failed.failed = 1;
        when(reconciliation.reconcilePatients(any(), anyList()))
                .thenReturn(new ReconciliationSyncService.Stats()).thenReturn(failed);

        sync.pull(CLINIC, table -> table.equals("patients"));

        verify(cursors).coreWatermark(CLINIC, "patients", LocalDateTime.of(2026, 10, 4, 10, 0, 25));
        verify(cursors, never()).coreWatermark(CLINIC, "patients", LocalDateTime.of(2026, 10, 4, 10, 0, 50));
        verify(cursors).coreRan(eq(CLINIC), eq("patients"), any(), anyInt(), anyInt(), anyInt(), contains("failed to save"));
    }

    @Test
    void aPullRecordsWhatItFetchedInsertedAndUpdatedAndAFailureItsError() {
        when(cursors.coreWatermark(CLINIC, "appointments")).thenReturn(LocalDateTime.of(2026, 10, 4, 10, 0, 0));
        when(cursors.coreWatermark(CLINIC, "procedurelogs")).thenReturn(LocalDateTime.of(2026, 10, 4, 10, 0, 0));
        ReconciliationSyncService.Stats appointments = new ReconciliationSyncService.Stats();
        appointments.inserted = 2;
        appointments.updated = 3;
        appointments.unchanged = 1;
        when(reconciliation.reconcileAppointments(any(), anyMap())).thenReturn(appointments);
        ReconciliationSyncService.Stats procedures = new ReconciliationSyncService.Stats();
        procedures.failed = 1;
        when(reconciliation.reconcileProcedureLogs(any(), anyMap())).thenReturn(procedures);

        sync.pull(CLINIC);

        verify(cursors).coreRan(eq(CLINIC), eq("appointments"), any(), eq(6), eq(2), eq(3), isNull());
        verify(cursors).coreRan(eq(CLINIC), eq("procedurelogs"), any(), eq(0), eq(0), eq(0), contains("checkpoint kept"));
    }

    @Test
    void providersAreReadInFullOnceThenOnlyTheirChanges() {
        ReconciliationSyncService.Stats full = new ReconciliationSyncService.Stats();
        full.seen("2026-10-04 08:00:00");
        when(reconciliation.reconcileProviders(any())).thenReturn(full);

        sync.pull(CLINIC, table -> table.equals("providers"));

        verify(reconciliation).reconcileProviders(CLINIC);
        verify(cursors).coreWatermark(CLINIC, "providers", LocalDateTime.of(2026, 10, 4, 8, 0, 0));

        when(cursors.coreWatermark(CLINIC, "providers")).thenReturn(LocalDateTime.of(2026, 10, 4, 8, 0, 0));
        sync.pull(CLINIC, table -> table.equals("providers"));

        verify(reconciliation).reconcileProviders(CLINIC, Map.of("DateTStamp", "2026-10-04 07:55:00"));
        verify(reconciliation, times(1)).reconcileProviders(CLINIC);
    }
}
