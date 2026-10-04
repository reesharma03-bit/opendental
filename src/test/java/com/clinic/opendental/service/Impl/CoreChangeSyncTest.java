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
        verify(reconciliation).reconcilePatients(eq(CLINIC), read.capture());
        assertThat(read.getValue()).hasSize(CoreChangeSync.MAX_PATIENTS_PER_PULL);
        assertThat(read.getValue().get(0).getPatNum()).isEqualTo(1L);
        // The watermark moves only to the last patient actually read (number 100), not past the 20 left.
        verify(cursors).coreWatermark(CLINIC, "patients", LocalDateTime.of(2026, 10, 4, 10, 1, 40));
    }
}
