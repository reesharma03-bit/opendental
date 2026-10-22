package com.clinic.opendental.service.Impl;

import com.clinic.opendental.model.Clinic;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What is syncing for a clinic, for the Sync activity view: the full sync in progress (step,
 * progress, records), the background work (per-minute change pull, live updates from Open Dental,
 * edits waiting to be sent) and, per table or screen, how many records SmileOS holds and when they
 * were last synced, are due next, or failed. Read from the sync's own tables, plus two moments the
 * sync doesn't store (last change pull, last webhook), kept here in memory.
 */
@Service
@Slf4j
public class SyncActivity {

    /** Tables with their own schema and where their rows are. */
    private static final Map<String, String> CORE_TABLES = new LinkedHashMap<>();

    static {
        CORE_TABLES.put("patients", "patients");
        CORE_TABLES.put("appointments", "appointments");
        CORE_TABLES.put("procedurelogs", "procedure_logs");
        CORE_TABLES.put("documents", "documents");
    }

    private record Moment(Instant at, String detail) {
    }

    private final JdbcTemplate jdbc;
    private final FullSyncService fullSync;
    private final Map<UUID, Moment> lastChangePull = new ConcurrentHashMap<>();
    private final Map<UUID, Moment> lastWebhook = new ConcurrentHashMap<>();

    public SyncActivity(JdbcTemplate jdbc, FullSyncService fullSync) {
        this.jdbc = jdbc;
        this.fullSync = fullSync;
    }

    /** The per-minute change pull ran for a clinic. */
    public void changePullRan(UUID clinicId) {
        lastChangePull.put(clinicId, new Moment(Instant.now(), null));
    }

    /** Open Dental sent a live update (webhook) for a clinic, e.g. "appointment". */
    public void webhookArrived(UUID clinicId, String table) {
        lastWebhook.put(clinicId, new Moment(Instant.now(), table));
    }

    public Map<String, Object> activity(Clinic clinic, boolean wholeFullSync) {
        UUID id = clinic.getId();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("clinicCode", clinic.getClinicCode());
        out.put("clinicName", clinic.getClinicName());
        out.put("connected", Boolean.TRUE.equals(clinic.getIsActive()));
        out.put("fullSync", fullSync.status(clinic));
        // Admins: a sync of another clinic running ahead of this one.
        Map<String, Object> whole = fullSync.status();
        Object step = whole.get("currentStep");
        out.put("otherClinicSyncing", wholeFullSync && "running".equals(whole.get("state")) && step instanceof String s
                && !s.startsWith(clinic.getClinicCode() + ":") ? s.substring(0, Math.max(0, s.indexOf(':'))) : null);

        Map<String, Object> background = new LinkedHashMap<>();
        Moment pull = lastChangePull.get(id);
        background.put("lastChangePullAt", pull == null ? null : pull.at().toString());
        Moment hook = lastWebhook.get(id);
        background.put("lastWebhookAt", hook == null ? null : hook.at().toString());
        background.put("lastWebhookTable", hook == null ? null : hook.detail());
        Map<String, Long> queue = queueCounts(id);
        background.put("editsWaiting", queue.getOrDefault("PENDING", 0L) + queue.getOrDefault("IN_PROGRESS", 0L));
        background.put("editsFailed", queue.getOrDefault("FAILED", 0L));
        out.put("background", background);

        out.put("tables", tables(id));
        return out;
    }

    /** Core tables first, then every Open Dental resource SmileOS copies. */
    private List<Map<String, Object>> tables(UUID clinicId) {
        Map<String, Map<String, Object>> cursors = cursors(clinicId);
        Map<String, Long> resourceCounts = counts(
                "SELECT resource, count(*) FROM od_resource_records WHERE clinic_id = ? AND deleted_at IS NULL GROUP BY resource", clinicId);
        Timestamp lastFull = lastFullSync(clinicId);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, String> core : CORE_TABLES.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", core.getKey());
            row.put("kind", "core");
            row.put("records", count("SELECT count(*) FROM " + core.getValue() + " WHERE clinic_id = ?", clinicId));
            Map<String, Object> cursor = cursors.get("core:" + core.getKey());
            Object changed = cursor == null ? null : cursor.get("updatedAt");
            row.put("lastSyncedAt", latest(changed, lastFull));
            row.put("nextDueAt", null);
            row.put("status", "idle");
            row.put("error", null);
            rows.add(row);
        }
        List<String> names = new ArrayList<>();
        OdResourceCatalog.LISTS.forEach(r -> names.add(r.resource()));
        OdResourceCatalog.PER_PARENT.forEach(r -> { if (!names.contains(r.resource())) names.add(r.resource()); });
        for (String name : names) {
            Map<String, Object> cursor = cursors.getOrDefault(name, Map.of());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", name);
            row.put("kind", OdResourceCatalog.find(name) != null && !OdResourceCatalog.find(name).isList() ? "per-patient" : "list");
            row.put("records", resourceCounts.getOrDefault(name, 0L));
            row.put("lastSyncedAt", latest(latest(cursor.get("lastFullAt"), cursor.get("lastIncrementalAt")),
                    "per-patient".equals(row.get("kind")) ? lastFull : null));
            row.put("nextDueAt", cursor.get("nextDueAt"));
            row.put("status", cursor.getOrDefault("status", "waiting"));
            row.put("error", cursor.get("lastError"));
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Map<String, Object>> cursors(UUID clinicId) {
        Map<String, Map<String, Object>> out = new HashMap<>();
        try {
            jdbc.query("""
                    SELECT resource, status, last_full_at, last_incremental_at, next_due_at, last_error, updated_at
                    FROM sync_cursors WHERE clinic_id = ?
                    """, rs -> {
                Map<String, Object> c = new HashMap<>();
                c.put("status", rs.getString("status"));
                c.put("lastFullAt", text(rs.getTimestamp("last_full_at")));
                c.put("lastIncrementalAt", text(rs.getTimestamp("last_incremental_at")));
                c.put("nextDueAt", text(rs.getTimestamp("next_due_at")));
                c.put("lastError", rs.getString("last_error"));
                c.put("updatedAt", text(rs.getTimestamp("updated_at")));
                out.put(rs.getString("resource"), c);
            }, clinicId);
        } catch (Exception e) {
            log.debug("Sync cursors unavailable: {}", e.getMessage());
        }
        return out;
    }

    private Timestamp lastFullSync(UUID clinicId) {
        try {
            List<Timestamp> found = jdbc.queryForList("""
                    SELECT max(coalesce(completed_at, started_at)) FROM sync_runs
                    WHERE clinic_id = ? AND entity_type = 'all'
                    """, Timestamp.class, clinicId);
            return found.isEmpty() ? null : found.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Long> queueCounts(UUID clinicId) {
        return counts("SELECT status, count(*) FROM od_sync_queue WHERE clinic_id = ? GROUP BY status", clinicId);
    }

    private Map<String, Long> counts(String sql, UUID clinicId) {
        Map<String, Long> out = new HashMap<>();
        try {
            jdbc.query(sql, rs -> { out.put(rs.getString(1), rs.getLong(2)); }, clinicId);
        } catch (Exception e) {
            log.debug("Count unavailable: {}", e.getMessage());
        }
        return out;
    }

    private long count(String sql, UUID clinicId) {
        try {
            Long n = jdbc.queryForObject(sql, Long.class, clinicId);
            return n == null ? 0 : n;
        } catch (Exception e) {
            return 0;
        }
    }

    private static String text(Timestamp t) {
        return t == null ? null : t.toInstant().toString();
    }

    /** The later of two moments (ISO strings or timestamps), as an ISO string. */
    private static String latest(Object a, Object b) {
        Instant x = instant(a);
        Instant y = instant(b);
        if (x == null) return y == null ? null : y.toString();
        if (y == null) return x.toString();
        return (x.isAfter(y) ? x : y).toString();
    }

    private static Instant instant(Object value) {
        if (value instanceof Timestamp t) return t.toInstant();
        if (value instanceof String s && !s.isBlank()) return Instant.parse(s);
        return null;
    }
}
