package com.clinic.opendental.service.Impl;

import com.clinic.opendental.client.OpenDentalClient;
import com.clinic.opendental.dto.patient.PatientResponse;
import com.clinic.opendental.model.Clinic;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Keeps patients, appointments and procedure logs (their own tables) fresh between nightly
 * full syncs, asking Open Dental only for what changed since the last pull.
 *
 * <ul>
 *   <li><b>Appointments, procedure logs:</b> {@code ?DateTStamp=} on their list endpoint.
 *       (Open Dental documents the procedure log filter as "created after", so edits to
 *       older procedures arrive by webhook or the nightly full read.)</li>
 *   <li><b>Patients:</b> {@code /patients/Simple?DateTStamp=} lists who changed; each is then
 *       read in full, at most {@value #MAX_PATIENTS_PER_PULL} per pull.</li>
 *   <li><b>Today's schedules, providers, operatories:</b> one call each, read in full.</li>
 * </ul>
 * Pulls resume from the newest DateTStamp seen (Open Dental's clock), minus an overlap.
 * Before the first pull, the newest DateTStamp already stored is the starting point; with
 * an empty table there is nothing to resume from, and the nightly full sync fills it first.
 */
@Service
@Slf4j
public class CoreChangeSync {

    static final int MAX_PATIENTS_PER_PULL = 100;
    private static final DateTimeFormatter OD = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ObjectMapper JSON = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final ReconciliationSyncService reconciliation;
    private final OpenDentalClient client;
    private final SyncCursors cursors;
    private final JdbcTemplate jdbc;

    public CoreChangeSync(ReconciliationSyncService reconciliation, OpenDentalClient client, SyncCursors cursors, JdbcTemplate jdbc) {
        this.reconciliation = reconciliation;
        this.client = client;
        this.cursors = cursors;
        this.jdbc = jdbc;
    }

    /** One catch-up pass for a clinic. */
    public void pull(Clinic clinic) {
        String since = since(clinic, "appointments", "appointments");
        if (since != null) {
            ReconciliationSyncService.Stats stats = reconciliation.reconcileAppointments(clinic, Map.of("DateTStamp", since));
            advance(clinic, "appointments", stats);
        }
        since = since(clinic, "procedurelogs", "procedure_logs");
        if (since != null) {
            ReconciliationSyncService.Stats stats = reconciliation.reconcileProcedureLogs(clinic, Map.of("DateTStamp", since));
            advance(clinic, "procedurelogs", stats);
        }
        // Patients have no DateTStamp column of their own here: start from the appointments' clock.
        since = since(clinic, "patients", "appointments");
        if (since != null) {
            pullPatients(clinic, since);
        }
        reconciliation.reconcileSchedules(clinic);
        reconciliation.reconcileProviders(clinic);
        reconciliation.reconcileOperatories(clinic);
    }

    private void pullPatients(Clinic clinic, String since) {
        List<JsonNode> changed = new ArrayList<>();
        try {
            ReconciliationSyncService.fetchAllPages(Map.of("DateTStamp", since), params ->
                    ResourceMirrorService.rows(client.getRaw("/patients/Simple", params, clinic.getBaseUrl(), clinic.getApiKey())))
                    .forEach(changed::add);
        } catch (Exception e) {
            log.warn("Could not list changed patients for clinic {}: {}", clinic.getClinicCode(), e.getMessage());
            return;
        }
        if (changed.isEmpty()) {
            return;
        }
        // Oldest change first, so the watermark only moves past patients actually read.
        changed.sort(Comparator.comparing(n -> n.path("DateTStamp").asText("")));
        List<JsonNode> batch = changed.subList(0, Math.min(MAX_PATIENTS_PER_PULL, changed.size()));
        List<PatientResponse> patients = new ArrayList<>();
        String latest = null;
        for (JsonNode simple : batch) {
            long patNum = simple.path("PatNum").asLong();
            if (patNum <= 0) continue;
            try {
                JsonNode full = client.getRaw("/patients/" + patNum, Map.of(), clinic.getBaseUrl(), clinic.getApiKey());
                patients.add(JSON.treeToValue(full, PatientResponse.class));
                latest = simple.path("DateTStamp").asText(null);
            } catch (Exception e) {
                log.warn("Could not read changed patient {} (clinic {}): {}", patNum, clinic.getClinicCode(), e.getMessage());
                break; // resume from here next time
            }
        }
        ReconciliationSyncService.Stats stats = reconciliation.reconcilePatients(clinic, patients);
        if (stats.failed == 0) {
            cursors.coreWatermark(clinic, "patients", parse(latest));
        }
        if (changed.size() > batch.size()) {
            log.info("{} changed patients waiting in Open Dental (clinic {}); read {} this pull",
                    changed.size(), clinic.getClinicCode(), patients.size());
        }
    }

    /** Where to resume, minus the overlap, in Open Dental's format; null when there is no starting point yet. */
    private String since(Clinic clinic, String table, String startFromTable) {
        LocalDateTime watermark = cursors.coreWatermark(clinic, table);
        if (watermark == null) {
            Timestamp newest = jdbc.queryForObject(
                    "SELECT max(date_t_stamp) FROM " + startFromTable + " WHERE clinic_id = ?", Timestamp.class, clinic.getId());
            if (newest == null) {
                return null;
            }
            watermark = newest.toLocalDateTime();
            cursors.coreWatermark(clinic, table, watermark);
        }
        return watermark.minus(IncrementalSyncService.OVERLAP).format(OD);
    }

    private void advance(Clinic clinic, String table, ReconciliationSyncService.Stats stats) {
        // A failure (fetch or a single record) keeps the watermark, so the next pull retries.
        if (stats.failed == 0) {
            cursors.coreWatermark(clinic, table, parse(stats.latestStamp));
        }
    }

    static LocalDateTime parse(String odTimestamp) {
        if (odTimestamp == null || odTimestamp.isBlank() || odTimestamp.startsWith("0001")) {
            return null;
        }
        try {
            return LocalDateTime.parse(odTimestamp.trim().replace('T', ' '), OD);
        } catch (Exception e) {
            return null;
        }
    }
}
