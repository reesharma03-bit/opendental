package com.clinic.opendental.service.Impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the own tables of resources that are also copied into od_resource_records
 * (lab_cases, medication_pats) in step with that copy: after every sync or webhook change,
 * one statement updates the rows that differ, and rows Open Dental no longer has are
 * soft-deleted. No extra calls to Open Dental.
 */
@Slf4j
final class TypedTableRefresh {

    private static String num(String field) {
        return "CASE WHEN r.data->>'" + field + "' ~ '^-?[0-9]+$' THEN (r.data->>'" + field + "')::bigint END";
    }

    private static String ts(String field) {
        return "CASE WHEN r.data->>'" + field + "' ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}' AND r.data->>'" + field
                + "' NOT LIKE '0001-01-01%' THEN replace(r.data->>'" + field + "', 'T', ' ')::timestamp END";
    }

    private static String date(String field) {
        return "CASE WHEN r.data->>'" + field + "' ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}' AND r.data->>'" + field
                + "' NOT LIKE '0001-01-01%' THEN left(r.data->>'" + field + "', 10)::date END";
    }

    private static String text(String field) {
        return "coalesce(r.data->>'" + field + "', '')";
    }

    /** resource -> {table, key column, column -> expression over the od_resource_records row r}. */
    private record Target(String table, String keyColumn, Map<String, String> columns) {
    }

    private static final Map<String, Target> TARGETS = Map.of(
            "labcases", new Target("lab_cases", "lab_case_num", ordered(
                    "pat_num", num("PatNum"), "laboratory_num", num("LaboratoryNum"), "apt_num", num("AptNum"),
                    "planned_apt_num", num("PlannedAptNum"), "date_time_due", ts("DateTimeDue"),
                    "date_time_created", ts("DateTimeCreated"), "date_time_sent", ts("DateTimeSent"),
                    "date_time_recd", ts("DateTimeRecd"), "date_time_checked", ts("DateTimeChecked"),
                    "prov_num", num("ProvNum"), "instructions", text("Instructions"),
                    "lab_fee", "CASE WHEN r.data->>'LabFee' ~ '^-?[0-9]+(\\.[0-9]+)?$' THEN (r.data->>'LabFee')::numeric END",
                    "invoice_num", text("InvoiceNum"), "date_t_stamp", ts("DateTStamp"))),
            "medicationpats", new Target("medication_pats", "medication_pat_num", ordered(
                    "pat_num", num("PatNum"), "medication_num", num("MedicationNum"), "med_name", text("medName"),
                    "pat_note", text("PatNote"), "date_start", date("DateStart"), "date_stop", date("DateStop"),
                    "prov_num", num("ProvNum"))));

    private static Map<String, String> ordered(String... pairs) {
        Map<String, String> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put(pairs[i], pairs[i + 1]);
        return map;
    }

    static boolean covers(String resource) {
        return TARGETS.containsKey(resource);
    }

    /** Tables found missing (script not run yet): warned about once, then skipped. */
    private static final Set<String> missing = ConcurrentHashMap.newKeySet();

    private TypedTableRefresh() {
    }

    static void refresh(JdbcTemplate jdbc, TransactionTemplate tx, UUID clinicId, String resource) {
        Target target = TARGETS.get(resource);
        if (target == null || missing.contains(target.table())) {
            return;
        }
        String columns = String.join(", ", target.columns().keySet());
        String values = String.join(", ", target.columns().values());
        String updates = String.join(", ", target.columns().keySet().stream().map(c -> c + " = EXCLUDED." + c).toList());
        String changed = target.columns().keySet().stream().map(c -> "t." + c).collect(java.util.stream.Collectors.joining(", "))
                + ") IS DISTINCT FROM (" + target.columns().keySet().stream().map(c -> "EXCLUDED." + c).collect(java.util.stream.Collectors.joining(", "));
        String upsert = """
                INSERT INTO %1$s AS t (clinic_id, %2$s, %3$s, is_deleted, deleted_at, updated_at)
                SELECT r.clinic_id, r.record_key::bigint, %4$s, false, NULL, now()
                FROM od_resource_records r
                WHERE r.clinic_id = ? AND r.resource = ? AND r.record_key ~ '^[0-9]+$'
                ON CONFLICT (clinic_id, %2$s) DO UPDATE
                SET %5$s, is_deleted = false, deleted_at = NULL, updated_at = now()
                WHERE (%6$s) OR t.is_deleted
                """.formatted(target.table(), target.keyColumn(), columns, values, updates, changed);
        String softDelete = """
                UPDATE %1$s t SET is_deleted = true, deleted_at = now(), updated_at = now()
                WHERE t.clinic_id = ? AND NOT coalesce(t.is_deleted, false)
                  AND NOT EXISTS (SELECT 1 FROM od_resource_records r
                                  WHERE r.clinic_id = t.clinic_id AND r.resource = ? AND r.record_key = t.%2$s::text)
                """.formatted(target.table(), target.keyColumn());
        try {
            tx.executeWithoutResult(status -> {
                jdbc.update(upsert, clinicId, resource);
                jdbc.update(softDelete, clinicId, resource);
            });
        } catch (Exception e) {
            String message = String.valueOf(e.getMessage());
            if (message.contains("does not exist")) {
                missing.add(target.table());
                log.warn("Table {} doesn't exist yet: run supabase-lab-cases-medication-pats.sql. Until then only od_resource_records is kept.",
                        target.table());
            } else {
                log.warn("Could not update {} from od_resource_records: {}", target.table(), message);
            }
        }
    }
}
