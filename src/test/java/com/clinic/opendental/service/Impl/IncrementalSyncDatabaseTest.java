package com.clinic.opendental.service.Impl;

import com.clinic.opendental.client.OpenDentalClient;
import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.repository.ClinicRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.client.HttpClientErrorException;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Change pulls driven by sync_cursors, against a real Postgres with the Supabase schema
 * (see {@link ResourceMirrorDatabaseTest} for setup). Open Dental is mocked: carriers carry
 * a DateTStamp and Open Dental honours the DateTStamp filter.
 */
@EnabledIfEnvironmentVariable(named = "SYNC_TEST_DB_URL", matches = ".+")
class IncrementalSyncDatabaseTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String FAR_FUTURE = "2099-01-01 00:00:00";

    private JdbcTemplate jdbc;
    private OpenDentalClient client;
    private SyncCursors cursors;
    private IncrementalSyncService service;
    private Clinic clinic;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(System.getenv("SYNC_TEST_DB_URL"));
        DataSourceTransactionManager transactions = new DataSourceTransactionManager(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        client = mock(OpenDentalClient.class);
        ResourceMirrorService mirror = new ResourceMirrorService(client, jdbc, transactions, 0);
        cursors = new SyncCursors(jdbc, transactions);
        FullSyncService fullSync = mock(FullSyncService.class);
        clinic = Clinic.builder().id(UUID.randomUUID()).clinicCode("T" + System.nanoTime()).baseUrl("http://od").apiKey("k").build();
        jdbc.update("INSERT INTO clinics (id, clinic_name, clinic_code, base_url) VALUES (?, 'Test', ?, 'http://od')",
                clinic.getId(), clinic.getClinicCode());
        service = new IncrementalSyncService(mock(ClinicRepository.class), mirror, cursors, fullSync, true);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM clinics WHERE id = ?", clinic.getId());
    }

    @Test
    void firstFullReadThenOnlyChanges() {
        openDental(List.of(carrier(1, "Delta", "2026-10-04 09:00:00"), carrier(2, "Aetna", "2026-10-04 09:30:00")),
                List.of(carrier(2, "Aetna Dental", "2026-10-04 10:15:00")));

        runUntilCarriersRead();

        assertThat(cursor("supports_tstamp")).isEqualTo(true);
        assertThat(cursor("watermark")).isEqualTo(Timestamp.valueOf("2026-10-04 09:30:00"));
        assertThat(storedNames()).containsExactly("Delta", "Aetna");

        dueNow();
        service.runDue(clinic);

        // Asked only for changes since the watermark, minus the overlap.
        verify(client).getRaw(eq("/carriers"), eq(Map.of("DateTStamp", "2026-10-04 09:25:00")), any(), any());
        assertThat(storedNames()).containsExactly("Delta", "Aetna Dental");
        assertThat(cursor("watermark")).isEqualTo(Timestamp.valueOf("2026-10-04 10:15:00"));
        assertThat(cursor("last_incremental_at")).isNotNull();
        assertThat(cursor("status")).isEqualTo("idle");
    }

    @Test
    void endpointThatIgnoresTheFilterKeepsFullReads() {
        // Asked for changes after 2099, this endpoint still returns everything.
        ArrayNode all = array(List.of(carrier(1, "Delta", "2026-10-04 09:00:00")));
        when(client.getRaw(eq("/carriers"), anyMap(), any(), any())).thenReturn(all);

        runUntilCarriersRead();

        assertThat(cursor("supports_tstamp")).isEqualTo(false);
        Timestamp nextDue = (Timestamp) cursor("next_due_at");
        assertThat(nextDue.toInstant()).isAfter(java.time.Instant.now().plusSeconds(50 * 60)); // WARM refresh: 1 hour
    }

    @Test
    void refusedFilterFallsBackToFullReads() {
        openDental(List.of(carrier(1, "Delta", "2026-10-04 09:00:00")), List.of());
        runUntilCarriersRead();
        when(client.getRaw(eq("/carriers"), argThat(p -> p != null && p.containsKey("DateTStamp") && !FAR_FUTURE.equals(p.get("DateTStamp"))),
                any(), any())).thenThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, new byte[0], null));

        dueNow();
        service.runDue(clinic);

        assertThat(cursor("supports_tstamp")).isEqualTo(false);
        assertThat(cursor("status")).isEqualTo("failed");
        assertThat(cursor("consecutive_failures")).isEqualTo(1);
    }

    @Test
    void aClaimedResourceIsNotClaimedAgain() {
        cursors.ensure(clinic);

        List<SyncCursors.Cursor> first = cursors.claimDue(clinic, 500);
        List<SyncCursors.Cursor> second = cursors.claimDue(clinic, 500);

        assertThat(first).hasSize(OdResourceCatalog.LISTS.size());
        assertThat(second).isEmpty();
    }

    /** One tick handles a few resources; tick until carriers had its first full read. */
    private void runUntilCarriersRead() {
        for (int i = 0; i < 20 && cursor("last_full_at") == null; i++) {
            service.runDue(clinic);
        }
        assertThat(cursor("last_full_at")).as("carriers was read").isNotNull();
    }

    private void openDental(List<JsonNode> all, List<JsonNode> changed) {
        when(client.getRaw(eq("/carriers"), anyMap(), any(), any())).thenAnswer(inv -> {
            Map<String, String> params = inv.getArgument(1);
            if (!params.containsKey("DateTStamp")) return array(all);
            return FAR_FUTURE.equals(params.get("DateTStamp")) ? array(List.of()) : array(changed);
        });
    }

    private void dueNow() {
        jdbc.update("UPDATE sync_cursors SET next_due_at = now() - interval '1 second' WHERE clinic_id = ? AND resource = 'carriers'",
                clinic.getId());
    }

    private Object cursor(String column) {
        List<Object> values = jdbc.queryForList("SELECT " + column + " FROM sync_cursors WHERE clinic_id = ? AND resource = 'carriers'",
                Object.class, clinic.getId());
        return values.isEmpty() ? null : values.get(0);
    }

    private List<String> storedNames() {
        return jdbc.queryForList("SELECT data ->> 'CarrierName' FROM od_resource_records WHERE clinic_id = ? AND resource = 'carriers' "
                + "ORDER BY record_key", String.class, clinic.getId());
    }

    private static ArrayNode array(List<JsonNode> rows) {
        ArrayNode array = JSON.createArrayNode();
        rows.forEach(array::add);
        return array;
    }

    private static JsonNode carrier(int num, String name, String stamp) {
        return JSON.createObjectNode().put("CarrierNum", num).put("CarrierName", name).put("DateTStamp", stamp);
    }
}
