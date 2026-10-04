package com.clinic.opendental.service.Impl;

import com.clinic.opendental.client.OpenDentalClient;
import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.service.Impl.OdResourceCatalog.Resource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.client.ResourceAccessException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The resource sync against a real Postgres with the Supabase schema loaded
 * (supabase-multitenant-schema.sql, supabase-od-resource-records.sql,
 * supabase-od-sync-queue.sql, supabase-sync-v2.sql). Runs only when
 * SYNC_TEST_DB_URL is set, e.g. jdbc:postgresql://localhost:55432/postgres?user=postgres&password=x
 * (on Windows set -Duser.timezone=Asia/Kolkata: newer Postgres images reject "Asia/Calcutta").
 */
@EnabledIfEnvironmentVariable(named = "SYNC_TEST_DB_URL", matches = ".+")
class ResourceMirrorDatabaseTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Resource CARRIERS = new Resource("carriers", "/carriers", "CarrierNum", null, null);

    private JdbcTemplate jdbc;
    private OpenDentalClient client;
    private ResourceMirrorService service;
    private Clinic clinic;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(System.getenv("SYNC_TEST_DB_URL"));
        jdbc = new JdbcTemplate(dataSource);
        client = mock(OpenDentalClient.class);
        service = new ResourceMirrorService(client, jdbc, new DataSourceTransactionManager(dataSource), 0);
        clinic = Clinic.builder().id(UUID.randomUUID()).clinicCode("T" + System.nanoTime()).baseUrl("http://od").apiKey("k").build();
        jdbc.update("INSERT INTO clinics (id, clinic_name, clinic_code, base_url) VALUES (?, 'Test', ?, 'http://od')",
                clinic.getId(), clinic.getClinicCode());
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM od_sync_queue WHERE clinic_id = ?", clinic.getId());
        jdbc.update("DELETE FROM clinics WHERE id = ?", clinic.getId());
    }

    @Test
    void unchangedDataIsNotWrittenAgain() {
        openDentalReturns(carrier(1, "Delta"), carrier(2, "Aetna"), carrier(3, "Cigna"));
        ResourceMirrorService.Result first = sync();
        assertThat(first.inserted()).isEqualTo(3);
        Timestamp savedAt = jdbc.queryForObject(
                "SELECT max(synced_at) FROM od_resource_records WHERE clinic_id = ?", Timestamp.class, clinic.getId());

        ResourceMirrorService.Result second = sync();

        assertThat(second.records()).isEqualTo(3);
        assertThat(second.inserted()).isZero();
        assertThat(second.updated()).isZero();
        assertThat(second.unchanged()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT max(synced_at) FROM od_resource_records WHERE clinic_id = ?",
                Timestamp.class, clinic.getId())).isEqualTo(savedAt);
    }

    @Test
    void onlyChangedRecordsAreUpdatedAndMissingOnesRemoved() {
        openDentalReturns(carrier(1, "Delta"), carrier(2, "Aetna"), carrier(3, "Cigna"));
        sync();

        openDentalReturns(carrier(1, "Delta Dental"), carrier(2, "Aetna"));
        ResourceMirrorService.Result result = sync();

        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.unchanged()).isEqualTo(1);
        assertThat(result.removed()).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT data ->> 'CarrierName' FROM od_resource_records WHERE clinic_id = ? ORDER BY record_key",
                String.class, clinic.getId())).containsExactly("Delta Dental", "Aetna");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM od_sync_staging WHERE clinic_id = ?", Integer.class, clinic.getId()))
                .as("staging is cleared after the run").isZero();
    }

    @Test
    void aChangeWaitingForOpenDentalIsNotOverwrittenOrRemoved() {
        openDentalReturns(carrier(1, "Delta"), carrier(2, "Aetna"));
        sync();
        jdbc.update("UPDATE od_resource_records SET data = jsonb_set(data, '{CarrierName}', '\"Edited here\"') "
                + "WHERE clinic_id = ? AND record_key = '1'", clinic.getId());
        jdbc.update("INSERT INTO od_sync_queue (clinic_id, entity_type, operation, local_id) VALUES (?, 'resource:carriers', 'UPDATE', 1)",
                clinic.getId());

        openDentalReturns(carrier(1, "Delta (from Open Dental)"));
        sync();

        assertThat(jdbc.queryForObject("SELECT data ->> 'CarrierName' FROM od_resource_records WHERE clinic_id = ? AND record_key = '1'",
                String.class, clinic.getId())).isEqualTo("Edited here");
    }

    @Test
    void aFailedFetchRemovesNothing() {
        openDentalReturns(carrier(1, "Delta"), carrier(2, "Aetna"));
        sync();
        when(client.getRaw(eq("/carriers"), anyMap(), any(), any())).thenThrow(new ResourceAccessException("timeout"));

        ResourceMirrorService.Result result = sync();

        assertThat(result.ok()).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM od_resource_records WHERE clinic_id = ?", Integer.class, clinic.getId()))
                .isEqualTo(2);
    }

    @Test
    void openDentalsChangeTimeIsKept() {
        openDentalReturns(JSON.createObjectNode().put("CarrierNum", 9).put("DateTStamp", "2026-10-04 10:15:30"));

        sync();

        assertThat(jdbc.queryForObject("SELECT od_tstamp FROM od_resource_records WHERE clinic_id = ?", Timestamp.class, clinic.getId()))
                .isEqualTo(Timestamp.valueOf("2026-10-04 10:15:30"));
    }

    @Test
    void nightlyPerPatientReadsOnlyRecentPatientsAndRemovesOnlyTheirRecords() {
        int today = jdbc.queryForObject("SELECT extract(dow FROM now())::int", Integer.class);
        long active = 700 + (today + 1) % 7;    // edited today
        long booked = 700 + (today + 2) % 7;    // appointment tomorrow
        long dormant = 700 + (today + 3) % 7;   // nothing recent, not in today's seventh
        for (long patNum : new long[]{active, booked, dormant}) {
            jdbc.update("INSERT INTO patients (clinic_id, pat_num, l_name, f_name, created_at, updated_at) VALUES (?, ?, 'P', 'Q', "
                    + "now() - interval '1 year', now() - interval '1 year')", clinic.getId(), patNum);
        }
        jdbc.update("UPDATE patients SET updated_at = now() WHERE clinic_id = ? AND pat_num = ?", clinic.getId(), active);
        jdbc.update("INSERT INTO appointments (clinic_id, apt_num, pat_num, apt_date_time) VALUES (?, 1, ?, now() + interval '1 day')",
                clinic.getId(), booked);
        Resource allergies = new Resource("allergies", "/allergies", "AllergyNum", OdResourceCatalog.PATIENTS, "PatNum");
        // Stored from earlier nights: one allergy per patient.
        for (long patNum : new long[]{active, booked, dormant}) {
            jdbc.update("INSERT INTO od_resource_records (clinic_id, resource, record_key, pat_num, data, synced_at) "
                    + "VALUES (?, 'allergies', ?, ?, '{}'::jsonb, now() - interval '1 day')", clinic.getId(), "A" + patNum, patNum);
        }
        // Open Dental now: the active patient's allergy was deleted, the booked patient has one.
        when(client.getRaw(eq("/allergies"), anyMap(), any(), any())).thenAnswer(inv -> {
            java.util.Map<String, String> params = inv.getArgument(1);
            return Long.parseLong(params.get("PatNum")) == booked
                    ? JSON.createArrayNode().add(JSON.createObjectNode().put("AllergyNum", "A" + booked).put("PatNum", booked))
                    : JSON.createArrayNode();
        });

        ResourceMirrorService.Result result = service.sync(clinic, allergies, Timestamp.from(Instant.now()), ResourceMirrorService.Scope.RECENT);

        assertThat(result.ok()).isTrue();
        verify(client, times(2)).getRaw(eq("/allergies"), anyMap(), any(), any());
        verify(client, never()).getRaw(eq("/allergies"), eq(java.util.Map.of("PatNum", String.valueOf(dormant))), any(), any());
        assertThat(jdbc.queryForList("SELECT pat_num FROM od_resource_records WHERE clinic_id = ? AND resource = 'allergies' ORDER BY pat_num",
                Long.class, clinic.getId())).containsExactlyInAnyOrder(booked, dormant);
        jdbc.update("DELETE FROM appointments WHERE clinic_id = ?", clinic.getId());
        jdbc.update("DELETE FROM patients WHERE clinic_id = ?", clinic.getId());
    }

    private ResourceMirrorService.Result sync() {
        return service.sync(clinic, CARRIERS, Timestamp.from(Instant.now()));
    }

    private void openDentalReturns(com.fasterxml.jackson.databind.JsonNode... rows) {
        ArrayNode array = JSON.createArrayNode();
        for (var row : rows) array.add(row);
        reset(client);
        when(client.getRaw(eq("/carriers"), anyMap(), any(), any())).thenReturn(array);
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode carrier(int num, String name) {
        return JSON.createObjectNode().put("CarrierNum", num).put("CarrierName", name);
    }
}
