package com.clinic.opendental.service.Impl;

import com.clinic.opendental.client.OpenDentalClient;
import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.service.Impl.OdResourceCatalog.Resource;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Copies Open Dental resources that have no dedicated table into
 * {@code od_resource_records}, one JSON row per Open Dental record.
 */
@Service
@Slf4j
public class ResourceMirrorService {

    /** Stop calling a resource's parents after this many failures in a row (Open Dental is likely down). */
    private static final int MAX_CONSECUTIVE_FAILURES = 20;

    /** Changes made in the dashboard that have not reached Open Dental yet; the sync leaves those rows alone. */
    private static final String QUEUED = """
            SELECT 1 FROM od_sync_queue q
            WHERE q.clinic_id = od_resource_records.clinic_id
              AND q.entity_type = 'resource:' || od_resource_records.resource
              AND q.status IN ('PENDING', 'IN_PROGRESS', 'FAILED')
            """;

    private static final String UPSERT = """
            INSERT INTO od_resource_records (clinic_id, resource, record_key, pat_num, data, synced_at)
            VALUES (?, ?, ?, ?, ?::jsonb, ?)
            ON CONFLICT (clinic_id, resource, record_key)
            DO UPDATE SET pat_num = EXCLUDED.pat_num, data = EXCLUDED.data, synced_at = EXCLUDED.synced_at
            WHERE NOT EXISTS (""" + QUEUED + " AND q.local_id::text = od_resource_records.record_key)";

    /** Removes rows Open Dental no longer returns, except dashboard changes still on their way there. */
    private static final String PRUNE = "DELETE FROM od_resource_records WHERE clinic_id = ? AND resource = ? AND synced_at < ?"
            + " AND record_key NOT LIKE '-%' AND NOT EXISTS (" + QUEUED + " AND q.local_id::text = od_resource_records.record_key)";

    private final OpenDentalClient client;
    private final JdbcTemplate jdbc;
    private final long requestDelayMs;
    /**
     * The pool runs with auto-commit off, so every write needs a transaction. Each page is
     * its own short one: no connection is held while waiting on Open Dental.
     */
    private final TransactionTemplate tx;

    public ResourceMirrorService(OpenDentalClient client, JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                                 @Value("${resource-sync.request-delay-ms:0}") long requestDelayMs) {
        this.client = client;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
        this.requestDelayMs = requestDelayMs;
    }

    /** Outcome of syncing one resource for one clinic. */
    public record Result(String resource, int records, int failedCalls, String error) {
        public boolean ok() {
            return error == null && failedCalls == 0;
        }
    }

    /**
     * Copies every row of one resource. Rows Open Dental no longer returns are removed,
     * but only when every call for the resource succeeded.
     */
    public Result sync(Clinic clinic, Resource resource, Timestamp runStart) {
        try {
            Counter counter = new Counter();
            if (resource.isList()) {
                List<JsonNode> rows = fetchList(clinic, resource.path(), Map.of());
                save(clinic, resource, rows, null, runStart);
                counter.records = rows.size();
            } else {
                syncPerParent(clinic, resource, runStart, counter);
            }
            if (counter.failedCalls == 0) {
                Integer removed = tx.execute(status -> jdbc.update(PRUNE, clinic.getId(), resource.resource(), runStart));
                if (removed != null && removed > 0) {
                    log.info("Removed {} {} row(s) no longer in Open Dental (clinic {})",
                            removed, resource.resource(), clinic.getClinicCode());
                }
            }
            return new Result(resource.resource(), counter.records, counter.failedCalls, counter.lastError);
        } catch (Exception e) {
            log.warn("Syncing {} for clinic {} failed: {}", resource.resource(), clinic.getClinicCode(), e.getMessage());
            return new Result(resource.resource(), 0, 1, e.getMessage());
        }
    }

    private void syncPerParent(Clinic clinic, Resource resource, Timestamp runStart, Counter counter) {
        int consecutiveFailures = 0;
        for (Long parentId : parentIds(clinic, resource)) {
            try {
                List<JsonNode> rows = resource.parentInPath()
                        ? rows(get(clinic, resource.path().replace("{id}", String.valueOf(parentId)), Map.of()))
                        : fetchList(clinic, resource.path(), Map.of(resource.parentField(), String.valueOf(parentId)));
                save(clinic, resource, rows, parentId, runStart);
                counter.records += rows.size();
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

    /** Ids to query a per-parent resource by, taken from what is already in our database. */
    List<Long> parentIds(Clinic clinic, Resource resource) {
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

    private List<JsonNode> fetchList(Clinic clinic, String path, Map<String, String> params) {
        return ReconciliationSyncService.fetchAllPages(params, pageParams -> rows(get(clinic, path, pageParams)));
    }

    private JsonNode get(Clinic clinic, String path, Map<String, String> params) {
        if (requestDelayMs > 0) {
            try {
                Thread.sleep(requestDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Sync interrupted", e);
            }
        }
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

    private void save(Clinic clinic, Resource resource, List<JsonNode> rows, Long parentId, Timestamp runStart) {
        if (rows.isEmpty()) {
            return;
        }
        List<Object[]> batch = new ArrayList<>(rows.size());
        for (JsonNode row : rows) {
            batch.add(new Object[]{
                    clinic.getId(),
                    resource.resource(),
                    recordKey(resource, row, parentId),
                    patNum(resource, row, parentId),
                    row.toString(),
                    runStart});
        }
        tx.executeWithoutResult(status -> jdbc.batchUpdate(UPSERT, batch));
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
        int records;
        int failedCalls;
        String lastError;
    }
}
