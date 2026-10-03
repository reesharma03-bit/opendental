package com.clinic.opendental.service.Impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Single-row reads and writes on od_resource_records, as the dashboard uses them. */
@Component
public class ResourceRecordStore {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Numeric keys sort as numbers (temporary negative keys first), others as text. */
    private static final String ORDER = " ORDER BY CASE WHEN record_key ~ '^-?[0-9]+$' THEN record_key::numeric END, record_key";

    private final JdbcTemplate jdbc;
    /** The pool runs with auto-commit off: writes join the caller's transaction, or commit on their own. */
    private final TransactionTemplate tx;

    public ResourceRecordStore(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public List<JsonNode> list(UUID clinicId, String resource, Long patNum, int limit, int offset) {
        String sql = "SELECT data FROM od_resource_records WHERE clinic_id = ? AND resource = ?"
                + (patNum != null ? " AND pat_num = ?" : "") + ORDER + " LIMIT ? OFFSET ?";
        Object[] args = patNum != null
                ? new Object[]{clinicId, resource, patNum, limit, offset}
                : new Object[]{clinicId, resource, limit, offset};
        return jdbc.query(sql, (rs, i) -> parse(rs.getString(1)), args);
    }

    public Optional<ObjectNode> find(UUID clinicId, String resource, String key) {
        return jdbc.query("SELECT data FROM od_resource_records WHERE clinic_id = ? AND resource = ? AND record_key = ?",
                        (rs, i) -> parse(rs.getString(1)), clinicId, resource, key)
                .stream().findFirst()
                .filter(JsonNode::isObject)
                .map(ObjectNode.class::cast);
    }

    public void save(UUID clinicId, String resource, String key, ObjectNode data) {
        tx.executeWithoutResult(status -> jdbc.update("""
                        INSERT INTO od_resource_records (clinic_id, resource, record_key, pat_num, data, synced_at)
                        VALUES (?, ?, ?, ?, ?::jsonb, now())
                        ON CONFLICT (clinic_id, resource, record_key)
                        DO UPDATE SET pat_num = EXCLUDED.pat_num, data = EXCLUDED.data, synced_at = EXCLUDED.synced_at
                        """,
                clinicId, resource, key, patNum(data), data.toString()));
    }

    /**
     * Copies the request's fields onto the stored record. Open Dental is not consistent
     * about field casing (StatusIsActive vs statusIsActive), so a field is replaced
     * whatever its casing.
     */
    public void merge(UUID clinicId, String resource, String key, Map<String, Object> changes) {
        tx.executeWithoutResult(status -> mergeInTransaction(clinicId, resource, key, changes));
    }

    private void mergeInTransaction(UUID clinicId, String resource, String key, Map<String, Object> changes) {
        ObjectNode data = find(clinicId, resource, key)
                .orElseThrow(() -> new IllegalStateException(resource + " " + key + " is not stored"));
        changes.forEach((field, value) -> {
            List<String> sameField = new java.util.ArrayList<>();
            data.fieldNames().forEachRemaining(name -> {
                if (name.equalsIgnoreCase(field)) {
                    sameField.add(name);
                }
            });
            data.remove(sameField);
            data.set(field, JSON.valueToTree(value));
        });
        save(clinicId, resource, key, data);
    }

    public void delete(UUID clinicId, String resource, String key) {
        tx.executeWithoutResult(status -> jdbc.update(
                "DELETE FROM od_resource_records WHERE clinic_id = ? AND resource = ? AND record_key = ?",
                clinicId, resource, key));
    }

    /** A patient moved from a temporary PatNum to the one Open Dental assigned. */
    public void movePatient(UUID clinicId, long tempPatNum, long patNum) {
        tx.executeWithoutResult(status -> jdbc.update(
                "UPDATE od_resource_records SET pat_num = ?, data = jsonb_set(data, '{PatNum}', to_jsonb(?::bigint)) "
                        + "WHERE clinic_id = ? AND pat_num = ?", patNum, patNum, clinicId, tempPatNum));
    }

    static Long patNum(JsonNode data) {
        JsonNode patNum = data.get("PatNum");
        if (patNum == null || patNum.isNull()) {
            return null;
        }
        if (patNum.canConvertToLong()) {
            return patNum.asLong();
        }
        return patNum.asText().matches("-?[0-9]+") ? Long.parseLong(patNum.asText()) : null;
    }

    private static JsonNode parse(String json) {
        try {
            return JSON.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Stored record is not valid JSON", e);
        }
    }
}
