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
import java.util.PriorityQueue;

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
 *   <li><b>Providers:</b> {@code ?DateTStamp=} once a first full read has set the checkpoint.</li>
 *   <li><b>Today's schedules, operatories:</b> one call each, read in full.</li>
 * </ul>
 * Each table's checkpoint is a {@code core:<table>} row of sync_cursors, with what the last
 * pull fetched, inserted and updated ({@link SyncCursors#coreRan}); it only moves forward
 * when every record the pull fetched was saved.
 * Pulls resume from the newest DateTStamp seen (Open Dental's clock), minus an overlap.
 * Before the first pull, the newest DateTStamp already stored is the starting point; with
 * an empty table there is nothing to resume from, and the nightly full sync fills it first.
 */
@Service
@Slf4j
public class CoreChangeSync {

    static final int MAX_PATIENTS_PER_PULL = 100;
    /** Changed patients are saved (and the checkpoint moved) every this many, not all at the end. */
    static final int SAVE_BATCH_SIZE = 25;
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
        pull(clinic, table -> true);
    }

    /** One catch-up pass for a clinic, for the tables {@code include} accepts. */
    public void pull(Clinic clinic, java.util.function.Predicate<String> include) {
        String since = include.test("appointments") ? since(clinic, "appointments", "appointments") : null;
        if (since != null) {
            Timestamp started = now();
            ReconciliationSyncService.Stats stats = reconciliation.reconcileAppointments(clinic, changedSince("appointments", since));
            advance(clinic, "appointments", started, stats);
        }
        since = include.test("procedurelogs") ? since(clinic, "procedurelogs", "procedure_logs") : null;
        if (since != null) {
            Timestamp started = now();
            ReconciliationSyncService.Stats stats = reconciliation.reconcileProcedureLogs(clinic, changedSince("procedurelogs", since));
            advance(clinic, "procedurelogs", started, stats);
        }
        // Patients have no DateTStamp column of their own here: start from the appointments' clock.
        since = include.test("patients") ? since(clinic, "patients", "appointments") : null;
        if (since != null) {
            pullPatients(clinic, since);
        }
        if (include.test("schedules")) reconciliation.reconcileSchedules(clinic);
        if (include.test("providers")) pullProviders(clinic);
        if (include.test("operatories")) reconciliation.reconcileOperatories(clinic);
    }

    /** The documented change filter of a core table, from {@code since}. */
    private static Map<String, String> changedSince(String table, String since) {
        return Map.of(OdChangeFilters.filter(table).param(), since);
    }

    /**
     * Providers: changes only ({@code ?DateTStamp=}) once a full read has set the checkpoint.
     * The providers table keeps no DateTStamp, so the first pull (and one after a reset) reads
     * every provider and starts from the newest DateTStamp among them.
     */
    private void pullProviders(Clinic clinic) {
        Timestamp started = now();
        LocalDateTime watermark = cursors.coreWatermark(clinic, "providers");
        ReconciliationSyncService.Stats stats = watermark == null
                ? reconciliation.reconcileProviders(clinic)
                : reconciliation.reconcileProviders(clinic,
                        changedSince("providers", watermark.minus(IncrementalSyncService.OVERLAP).format(OD)));
        advance(clinic, "providers", started, stats);
    }

    private void pullPatients(Clinic clinic, String since) {
        Timestamp started = now();
        // Only the oldest MAX_PATIENTS_PER_PULL changes are kept while the list is paged through,
        // so memory stays bounded however many patients changed.
        Comparator<JsonNode> oldestFirst = Comparator.comparing((JsonNode n) -> n.path("DateTStamp").asText(""))
                .thenComparingLong(n -> n.path("PatNum").asLong());
        PriorityQueue<JsonNode> oldest = new PriorityQueue<>(oldestFirst.reversed());
        int[] changed = {0};
        try {
            ReconciliationSyncService.fetchPages(changedSince("patients", since), 0, params ->
                    ResourceMirrorService.rows(client.getRaw("/patients/Simple", params, clinic.getBaseUrl(), clinic.getApiKey())),
                    page -> {
                        for (JsonNode simple : page) {
                            changed[0]++;
                            oldest.add(simple);
                            if (oldest.size() > MAX_PATIENTS_PER_PULL) oldest.poll();
                        }
                    });
        } catch (Exception e) {
            log.warn("Could not list changed patients for clinic {}: {}", clinic.getClinicCode(), e.getMessage());
            ran(clinic, "patients", started, 0, null, "listing changed patients failed: " + e.getMessage());
            return;
        }
        if (oldest.isEmpty()) {
            ran(clinic, "patients", started, 0, null, null);
            return;
        }
        // Oldest change first, so the watermark only moves past patients actually saved.
        List<JsonNode> toRead = new ArrayList<>(oldest);
        toRead.sort(oldestFirst);
        ReconciliationSyncService.Stats total = new ReconciliationSyncService.Stats();
        List<PatientResponse> batch = new ArrayList<>(SAVE_BATCH_SIZE);
        String batchLatest = null;
        int read = 0;
        String error = null;
        boolean saveFailed = false;
        for (JsonNode simple : toRead) {
            long patNum = simple.path("PatNum").asLong();
            if (patNum <= 0) continue;
            try {
                JsonNode full = client.getRaw("/patients/" + patNum, Map.of(), clinic.getBaseUrl(), clinic.getApiKey());
                batch.add(JSON.treeToValue(full, PatientResponse.class));
                batchLatest = simple.path("DateTStamp").asText(null);
                read++;
            } catch (Exception e) {
                log.warn("Could not read changed patient {} (clinic {}): {}", patNum, clinic.getClinicCode(), e.getMessage());
                error = "a changed patient could not be read; resumes from it next pull";
                break; // save what was read, resume from here next time
            }
            if (batch.size() >= SAVE_BATCH_SIZE) {
                if (!savePatients(clinic, batch, batchLatest, total)) {
                    saveFailed = true;
                    break;
                }
                batch.clear();
            }
        }
        if (!saveFailed && !savePatients(clinic, batch, batchLatest, total)) {
            saveFailed = true;
        }
        if (saveFailed) {
            error = total.failed + " patient(s) failed to save; checkpoint kept at the last saved batch";
        }
        ran(clinic, "patients", started, read, total, error);
        if (changed[0] > toRead.size()) {
            log.info("{} changed patients waiting in Open Dental (clinic {}); read {} this pull",
                    changed[0], clinic.getClinicCode(), read);
        }
    }

    /**
     * Saves one batch of changed patients and, when all of them were saved, moves the checkpoint
     * to the last one's DateTStamp. A later batch failing then keeps what earlier ones saved.
     *
     * @return false when a patient failed to save (the checkpoint stays put)
     */
    private boolean savePatients(Clinic clinic, List<PatientResponse> batch, String latest, ReconciliationSyncService.Stats total) {
        if (batch.isEmpty()) {
            return true;
        }
        ReconciliationSyncService.Stats stats = reconciliation.reconcilePatients(clinic, new ArrayList<>(batch));
        total.add(stats);
        if (stats.failed > 0) {
            return false;
        }
        cursors.coreWatermark(clinic, "patients", parse(latest));
        return true;
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

    private void advance(Clinic clinic, String table, Timestamp started, ReconciliationSyncService.Stats stats) {
        // A failure (fetch or a single record) keeps the watermark, so the next pull retries.
        if (stats.failed == 0) {
            cursors.coreWatermark(clinic, table, parse(stats.latestStamp));
        }
        ran(clinic, table, started, stats.fetched(), stats,
                stats.failed == 0 ? null : stats.failed + " call(s) or record(s) failed; checkpoint kept");
    }

    /** The checkpoint's run statistics; failing to write them must not stop the pull. */
    private void ran(Clinic clinic, String table, Timestamp started, int fetched, ReconciliationSyncService.Stats stats, String error) {
        try {
            cursors.coreRan(clinic, table, started, fetched,
                    stats == null ? 0 : stats.inserted, stats == null ? 0 : stats.updated, error);
        } catch (Exception e) {
            log.warn("Could not record the {} change pull of clinic {}: {}", table, clinic.getClinicCode(), e.getMessage());
        }
    }

    private static Timestamp now() {
        return new Timestamp(System.currentTimeMillis());
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
