package com.clinic.opendental.service.Impl;

import com.clinic.opendental.client.OpenDentalClient;
import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.service.Impl.OdResourceCatalog.Resource;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Copies Open Dental resources that have no dedicated table into
 * {@code od_resource_records}, one JSON row per Open Dental record.
 *
 * <p>Built for millions of rows: each page lands in the unlogged {@code od_sync_staging}
 * table and is merged with one statement that only writes rows whose content hash
 * changed, so re-syncing unchanged data writes nothing. The keys seen during the run
 * stay in staging (without their JSON) and decide which rows Open Dental no longer has.</p>
 */
@Service
@Slf4j
public class ResourceMirrorService {

    /** Stop calling a resource's parents after this many failures in a row (Open Dental is likely down). */
    private static final int MAX_CONSECUTIVE_FAILURES = 20;

    /** Changes made in the dashboard that have not reached Open Dental yet; the sync leaves those records alone. */
    private static final String QUEUED = """
            SELECT 1 FROM od_sync_queue q
            WHERE q.clinic_id = %1$s.clinic_id
              AND q.entity_type = 'resource:' || %1$s.resource
              AND q.local_id::text = %1$s.record_key
              AND q.status IN ('PENDING', 'IN_PROGRESS', 'FAILED')
            """;

    /** One fetched page into staging; the hash is taken over the normalised JSON. */
    private static final String STAGE = """
            INSERT INTO od_sync_staging (batch_id, clinic_id, resource, record_key, pat_num, od_tstamp, data, data_hash)
            VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, md5(?::jsonb::text))
            ON CONFLICT DO NOTHING
            """;

    /**
     * Inserts new records and updates only those whose hash changed (or that had been
     * removed). Returns one row per record written: true when it was inserted.
     */
    private static final String MERGE = """
            INSERT INTO od_resource_records AS t
                (clinic_id, resource, record_key, pat_num, data, data_hash, od_tstamp, synced_at, seen_at)
            SELECT s.clinic_id, s.resource, s.record_key, s.pat_num, s.data, s.data_hash, s.od_tstamp, now(), now()
            FROM od_sync_staging s
            WHERE s.batch_id = ? AND NOT EXISTS (%s)
            ON CONFLICT (clinic_id, resource, record_key) DO UPDATE
            SET pat_num = EXCLUDED.pat_num, data = EXCLUDED.data, data_hash = EXCLUDED.data_hash,
                od_tstamp = EXCLUDED.od_tstamp, synced_at = now(), seen_at = now(), deleted_at = NULL
            WHERE t.data_hash IS DISTINCT FROM EXCLUDED.data_hash OR t.deleted_at IS NOT NULL
            RETURNING (xmax = 0)
            """.formatted(QUEUED.formatted("s"));

    /** After merging a page, keep only its keys under the run's batch (for pruning), without the JSON. */
    private static final String KEEP_KEYS = """
            INSERT INTO od_sync_staging (batch_id, clinic_id, resource, record_key, data, data_hash)
            SELECT ?, clinic_id, resource, record_key, '{}'::jsonb, '' FROM od_sync_staging WHERE batch_id = ?
            ON CONFLICT DO NOTHING
            """;

    private static final String DROP_BATCH = "DELETE FROM od_sync_staging WHERE batch_id = ?";

    /**
     * Removes records this run did not see, after a clean fetch. Spared: dashboard changes
     * still on their way to Open Dental, temporary keys, and rows saved after the run began.
     */
    private static final String PRUNE = """
            DELETE FROM od_resource_records r
            WHERE r.clinic_id = ? AND r.resource = ? AND r.synced_at < ? AND r.record_key NOT LIKE '-%%'
              AND NOT EXISTS (SELECT 1 FROM od_sync_staging k
                              WHERE k.batch_id = ? AND k.resource = r.resource AND k.record_key = r.record_key)
              AND NOT EXISTS (%s)
            """.formatted(QUEUED.formatted("r"));

    /** A run that only visited some patients / appointments may only remove what belongs to them. */
    private static final String PRUNE_FOR_PATIENTS = PRUNE + " AND r.pat_num = ANY(?::bigint[])";
    private static final String PRUNE_FOR_APPOINTMENTS = PRUNE + " AND (r.data ->> 'AptNum') = ANY(?::text[])";

    /**
     * Patients worth a per-patient call tonight: changed or added in the last week, seen or
     * booked within 30 days, plus a seventh of everyone else (by PatNum), so the whole
     * practice is still covered once a week.
     */
    private static final String RECENT_PATIENTS = """
            SELECT p.pat_num FROM patients p
            WHERE p.clinic_id = ? AND p.pat_num > 0
              AND (p.updated_at >= now() - interval '7 days'
                   OR p.created_at >= now() - interval '7 days'
                   OR p.pat_num % 7 = extract(dow FROM now())::int
                   OR EXISTS (SELECT 1 FROM appointments a
                              WHERE a.clinic_id = p.clinic_id AND a.pat_num = p.pat_num
                                AND a.apt_date_time BETWEEN now() - interval '30 days' AND now() + interval '30 days'))
            ORDER BY p.pat_num
            """;

    /** Appointments within 30 days of today, plus a seventh of the rest. */
    private static final String RECENT_APPOINTMENTS = """
            SELECT apt_num FROM appointments
            WHERE clinic_id = ? AND apt_num > 0
              AND (apt_date_time BETWEEN now() - interval '30 days' AND now() + interval '30 days'
                   OR apt_num % 7 = extract(dow FROM now())::int)
            ORDER BY apt_num
            """;

    /**
     * Which parents a per-patient / per-appointment resource is fetched for. Open Dental
     * allows about one call per second, so calling it for every patient every night does
     * not fit in a night for a large practice.
     */
    public enum Scope {
        /** Every patient / appointment (Force Sync). */
        ALL,
        /** Recently active ones plus a rolling seventh of the rest (nightly). */
        RECENT
    }

    private static final DateTimeFormatter OD_TIMESTAMP =DateTimeFormatter.ofPattern("yyyy-MM-dd[ ]['T']HH:mm:ss");
    private static final DateTimeFormatter OD_TIMESTAMP_OUT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_PAGES = 10_000;

    private final OpenDentalClient client;
    private final JdbcTemplate jdbc;
    /**
     * The pool runs with auto-commit off, so every write needs a transaction. Each page is
     * its own short one: no connection is held while waiting on Open Dental.
     */
    private final TransactionTemplate tx;

    /** Calls are paced to Open Dental's rate limit by the client (see OpenDentalRateLimiter). */
    public ResourceMirrorService(OpenDentalClient client, JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.client = client;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /**
     * Outcome of syncing one resource for one clinic. {@code records} is what Open Dental
     * returned; {@code inserted}/{@code updated} what was actually written; {@code removed}
     * what Open Dental no longer has.
     */
    public record Result(String resource, int records, int failedCalls, String error,
                         int inserted, int updated, int removed, Timestamp latestChange) {
        public Result(String resource, int records, int failedCalls, String error) {
            this(resource, records, failedCalls, error, 0, 0, 0, null);
        }

        public boolean ok() {
            return error == null && failedCalls == 0;
        }

        public int unchanged() {
            return Math.max(0, records - inserted - updated);
        }
    }

    /**
     * Copies every row of one resource. Rows Open Dental no longer returns are removed,
     * but only when every call for the resource succeeded.
     */
    public Result sync(Clinic clinic, Resource resource, Timestamp runStart) {
        return sync(clinic, resource, runStart, Scope.ALL);
    }

    /** As {@link #sync(Clinic, Resource, Timestamp)}, fetching per-parent resources for {@code scope}'s parents. */
    public Result sync(Clinic clinic, Resource resource, Timestamp runStart, Scope scope) {
        Counter counter = new Counter(UUID.randomUUID());
        try {
            List<Long> parents = null;
            if (resource.isList()) {
                for (Map<String, String> pass : resource.passes()) {
                    forEachPage(clinic, resource.path(), pass, rows -> save(clinic, resource, rows, null, counter));
                }
            } else {
                parents = parentIds(clinic, resource, scope);
                syncPerParent(clinic, resource, parents, counter);
            }
            if (counter.failedCalls == 0) {
                String prune = PRUNE;
                List<Object> args = new ArrayList<>(List.of(clinic.getId(), resource.resource(), runStart, counter.runBatch));
                if (scope == Scope.RECENT && parents != null && scoped(resource)) {
                    prune = OdResourceCatalog.PATIENTS.equals(resource.parent()) ? PRUNE_FOR_PATIENTS : PRUNE_FOR_APPOINTMENTS;
                    args.add(parents.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",", "{", "}")));
                }
                String sql = prune;
                Integer removed = tx.execute(status -> jdbc.update(sql, args.toArray()));
                counter.removed = removed == null ? 0 : removed;
                if (counter.removed > 0) {
                    log.info("Removed {} {} row(s) no longer in Open Dental (clinic {})",
                            counter.removed, resource.resource(), clinic.getClinicCode());
                }
            }
            refreshTyped(clinic, resource.resource());
            return counter.result(resource.resource());
        } catch (Exception e) {
            log.warn("Syncing {} for clinic {} failed: {}", resource.resource(), clinic.getClinicCode(), e.getMessage());
            return new Result(resource.resource(), counter.records, counter.failedCalls + 1, e.getMessage(),
                    counter.inserted, counter.updated, 0, counter.latestChange);
        } finally {
            dropBatch(counter, resource);
        }
    }

    /**
     * Copies only the records Open Dental changed since {@code since} (its DateTStamp
     * filter). Nothing is removed: deletes are only detected by a full {@link #sync}.
     * Throws when Open Dental refuses the call, so the caller can back off.
     */
    public Result syncChanged(Clinic clinic, Resource resource, LocalDateTime since) {
        if (!resource.isList()) {
            throw new IllegalArgumentException(resource.resource() + " is fetched per parent and has no change filter");
        }
        Counter counter = new Counter(UUID.randomUUID());
        try {
            for (Map<String, String> pass : resource.passes()) {
                forEachPage(clinic, resource.path(), with(pass, "DateTStamp", since.format(OD_TIMESTAMP_OUT)),
                        rows -> save(clinic, resource, rows, null, counter));
            }
            refreshTyped(clinic, resource.resource());
            return counter.result(resource.resource());
        } finally {
            dropBatch(counter, resource);
        }
    }

    /**
     * Records Open Dental pushed by webhook (full rows): merged like a sync page, so only
     * real changes are written and a change still waiting to go to Open Dental is kept.
     *
     * @return records written
     */
    public int applyWebhookRows(Clinic clinic, String resourceName, List<JsonNode> rows) {
        Resource resource = OdResourceCatalog.find(resourceName);
        if (resource == null || rows.isEmpty()) {
            return 0;
        }
        Counter counter = new Counter(UUID.randomUUID());
        try {
            save(clinic, resource, rows, null, counter);
            refreshTyped(clinic, resource.resource());
            return counter.inserted + counter.updated;
        } finally {
            dropBatch(counter, resource);
        }
    }

    /**
     * Records Open Dental reported deleted by webhook. A deletion is skipped for a record with
     * a dashboard change still waiting to go to Open Dental.
     *
     * @return records removed
     */
    public int removeWebhookRows(Clinic clinic, String resourceName, List<JsonNode> rows) {
        Resource resource = OdResourceCatalog.find(resourceName);
        if (resource == null || rows.isEmpty()) {
            return 0;
        }
        List<String> keys = new ArrayList<>();
        for (JsonNode row : rows) {
            JsonNode key = row.get(resource.keyField());
            if (key != null && !key.isNull() && !key.asText().isBlank()) keys.add(key.asText());
        }
        if (keys.isEmpty()) {
            log.warn("{} deletion webhook without {}: nothing removed", resourceName, resource.keyField());
            return 0;
        }
        String array = keys.stream().map(k -> "\"" + k.replace("\"", "") + "\"").collect(java.util.stream.Collectors.joining(",", "{", "}"));
        Integer removed = tx.execute(status -> jdbc.update("""
                        DELETE FROM od_resource_records r
                        WHERE r.clinic_id = ? AND r.resource = ? AND r.record_key = ANY(?::text[])
                          AND NOT EXISTS (%s)
                        """.formatted(QUEUED.formatted("r")), clinic.getId(), resource.resource(), array));
        refreshTyped(clinic, resource.resource());
        return removed == null ? 0 : removed;
    }

    /** Resources that also have an own table (lab_cases, medication_pats): bring it in step. */
    private void refreshTyped(Clinic clinic, String resource) {
        if (TypedTableRefresh.covers(resource)) {
            TypedTableRefresh.refresh(jdbc, tx, clinic.getId(), resource);
        }
    }

    /**
     * Whether Open Dental filters this resource by DateTStamp: asked for changes after a
     * date far in the future it must answer with nothing, and the records we hold must
     * carry a DateTStamp. An endpoint that ignores the filter returns everything.
     */
    public boolean acceptsChangedSince(Clinic clinic, Resource resource) {
        if (!resource.isList()) {
            return false;
        }
        Boolean stamped = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM od_resource_records WHERE clinic_id = ? AND resource = ? AND od_tstamp IS NOT NULL)",
                Boolean.class, clinic.getId(), resource.resource());
        if (!Boolean.TRUE.equals(stamped)) {
            return false;
        }
        for (Map<String, String> pass : resource.passes()) {
            try {
                if (!rows(get(clinic, resource.path(), with(pass, "DateTStamp", "2099-01-01 00:00:00"))).isEmpty()) {
                    return false; // the filter is ignored
                }
            } catch (HttpClientErrorException.NotFound e) {
                // some endpoints answer "nothing found" with 404
            } catch (HttpClientErrorException e) {
                return false; // the filter is not accepted
            }
        }
        return true;
    }

    private static Map<String, String> with(Map<String, String> params, String name, String value) {
        Map<String, String> all = new HashMap<>(params);
        all.put(name, value);
        return all;
    }

    private void dropBatch(Counter counter, Resource resource) {
        try {
            tx.executeWithoutResult(status -> jdbc.update(DROP_BATCH, counter.runBatch));
        } catch (Exception e) {
            log.warn("Could not clear sync staging for {}: {}", resource.resource(), e.getMessage());
        }
    }

    private void syncPerParent(Clinic clinic, Resource resource, List<Long> parents, Counter counter) {
        int consecutiveFailures = 0;
        for (Long parentId : parents) {
            try {
                if (resource.parentInPath()) {
                    save(clinic, resource, rows(get(clinic, resource.path().replace("{id}", String.valueOf(parentId)), Map.of())),
                            parentId, counter);
                } else {
                    forEachPage(clinic, resource.path(), Map.of(resource.parentField(), String.valueOf(parentId)),
                            rows -> save(clinic, resource, rows, parentId, counter));
                }
                consecutiveFailures = 0;
            } catch (HttpClientErrorException.NotFound e) {
                consecutiveFailures = 0; // nothing for this parent
            } catch (Exception e) {
                counter.failedCalls++;
                counter.lastError = resource.parentField() + " " + parentId + ": " + e.getMessage();
                if (++consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    counter.lastError = "stopped after " + MAX_CONSECUTIVE_FAILURES
                            + " failed calls in a row; last: " + counter.lastError;
                    return;
                }
            }
        }
    }

    /** Fetched per patient or per appointment: the resources a RECENT run narrows down. */
    private static boolean scoped(Resource resource) {
        return OdResourceCatalog.PATIENTS.equals(resource.parent()) || OdResourceCatalog.APPOINTMENTS.equals(resource.parent());
    }

    /** Ids to query a per-parent resource by, taken from what is already in our database. */
    List<Long> parentIds(Clinic clinic, Resource resource, Scope scope) {
        if (scope == Scope.RECENT && OdResourceCatalog.PATIENTS.equals(resource.parent())) {
            return jdbc.queryForList(RECENT_PATIENTS, Long.class, clinic.getId());
        }
        if (scope == Scope.RECENT && OdResourceCatalog.APPOINTMENTS.equals(resource.parent())) {
            return jdbc.queryForList(RECENT_APPOINTMENTS, Long.class, clinic.getId());
        }
        return switch (resource.parent()) {
            case OdResourceCatalog.PATIENTS -> jdbc.queryForList(
                    "SELECT pat_num FROM patients WHERE clinic_id = ? AND pat_num > 0 ORDER BY pat_num",
                    Long.class, clinic.getId());
            case OdResourceCatalog.APPOINTMENTS -> jdbc.queryForList(
                    "SELECT apt_num FROM appointments WHERE clinic_id = ? AND apt_num > 0 ORDER BY apt_num",
                    Long.class, clinic.getId());
            default -> jdbc.queryForList(
                    "SELECT DISTINCT (data ->> ?)::bigint FROM od_resource_records "
                            + "WHERE clinic_id = ? AND resource = ? AND (data ->> ?) ~ '^[0-9]+$' ORDER BY 1",
                    Long.class, resource.parentField(), clinic.getId(), resource.parent(), resource.parentField());
        };
    }

    /**
     * Reads a list page by page (Open Dental's Offset paging) and hands each page over as
     * it arrives, so a resource with millions of rows is never held in memory at once.
     * Stops on an empty or short page, or when an endpoint ignores Offset and repeats itself.
     */
    private void forEachPage(Clinic clinic, String path, Map<String, String> params, Consumer<List<JsonNode>> page) {
        List<JsonNode> previous = null;
        int offset = 0;
        for (int i = 0; i < MAX_PAGES; i++) {
            Map<String, String> pageParams = new HashMap<>(params);
            if (offset > 0) {
                pageParams.put("Offset", String.valueOf(offset));
            }
            List<JsonNode> rows = rows(get(clinic, path, pageParams));
            if (rows.isEmpty() || rows.equals(previous)) {
                return;
            }
            page.accept(rows);
            if (rows.size() < ReconciliationSyncService.OD_PAGE_SIZE) {
                return;
            }
            offset += rows.size();
            previous = rows;
        }
    }

    private JsonNode get(Clinic clinic, String path, Map<String, String> params) {
        return client.getRaw(path, params, clinic.getBaseUrl(), clinic.getApiKey());
    }

    /** Open Dental answers a list with an array and a single record with an object. */
    static List<JsonNode> rows(JsonNode body) {
        List<JsonNode> rows = new ArrayList<>();
        if (body == null || body.isNull() || body.isMissingNode()) {
            return rows;
        }
        if (body.isArray()) {
            body.forEach(rows::add);
        } else if (body.isObject()) {
            rows.add(body);
        }
        return rows;
    }

    /** Stages one page, merges what changed and keeps the page's keys for pruning, in one short transaction. */
    private void save(Clinic clinic, Resource resource, List<JsonNode> rows, Long parentId, Counter counter) {
        if (rows.isEmpty()) {
            return;
        }
        UUID page = UUID.randomUUID();
        List<Object[]> batch = new ArrayList<>(rows.size());
        for (JsonNode row : rows) {
            String json = row.toString();
            batch.add(new Object[]{
                    page,
                    clinic.getId(),
                    resource.resource(),
                    recordKey(resource, row, parentId),
                    patNum(resource, row, parentId),
                    odTimestamp(row),
                    json,
                    json});
        }
        tx.executeWithoutResult(status -> {
            jdbc.batchUpdate(STAGE, batch);
            List<Boolean> written = jdbc.queryForList(MERGE, Boolean.class, page);
            jdbc.update(KEEP_KEYS, counter.runBatch, page);
            jdbc.update(DROP_BATCH, page);
            for (Boolean inserted : written) {
                if (Boolean.TRUE.equals(inserted)) counter.inserted++;
                else counter.updated++;
            }
        });
        counter.records += rows.size();
        for (Object[] row : batch) {
            Timestamp stamp = (Timestamp) row[5];
            if (stamp != null && (counter.latestChange == null || stamp.after(counter.latestChange))) {
                counter.latestChange = stamp;
            }
        }
    }

    /** Open Dental's last-change time (DateTStamp), when the record carries one. */
    static Timestamp odTimestamp(JsonNode row) {
        JsonNode stamp = row.get("DateTStamp");
        if (stamp == null || !stamp.isTextual() || stamp.asText().isBlank()) {
            return null;
        }
        try {
            return Timestamp.valueOf(LocalDateTime.parse(stamp.asText().trim(), OD_TIMESTAMP));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    static String recordKey(Resource resource, JsonNode row, Long parentId) {
        JsonNode key = row.get(resource.keyField());
        String value = key == null || key.isNull() || key.asText().isEmpty() ? sha256(row.toString()) : key.asText();
        return resource.parentInPath() ? parentId + ":" + value : value;
    }

    private static Long patNum(Resource resource, JsonNode row, Long parentId) {
        JsonNode patNum = row.get("PatNum");
        if (patNum != null && patNum.canConvertToLong() && patNum.asLong() > 0) {
            return patNum.asLong();
        }
        if (patNum != null && patNum.isTextual() && patNum.asText().matches("[0-9]+")) {
            return Long.parseLong(patNum.asText());
        }
        return OdResourceCatalog.PATIENTS.equals(resource.parent()) ? parentId : null;
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class Counter {
        /** Staging batch holding the keys seen during this run. */
        final UUID runBatch;
        int records;
        int failedCalls;
        String lastError;
        int inserted;
        int updated;
        int removed;
        /** Newest DateTStamp among the records read: where the next change pull resumes. */
        Timestamp latestChange;

        Counter(UUID runBatch) {
            this.runBatch = runBatch;
        }

        Result result(String resource) {
            return new Result(resource, records, failedCalls, lastError, inserted, updated, removed, latestChange);
        }
    }
}
