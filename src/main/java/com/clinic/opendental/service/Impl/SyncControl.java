package com.clinic.opendental.service.Impl;

import com.clinic.opendental.exception.ApiException;
import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.service.Impl.OdResourceCatalog.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Array;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Pause / resume of the Open Dental sync, and which screens' data is synced per clinic
 * (tables sync_control and clinic_sync_resources, see supabase-sync-control.sql).
 *
 * <p>While paused nothing is read from Open Dental and dashboard edits wait in
 * od_sync_queue. Webhooks are still saved: Open Dental sends them anyway, and they cost
 * no API calls. A clinic with a selection only syncs those resources, plus the ones they
 * are read through (e.g. allergies are read per patient, so patients are synced too).</p>
 *
 * <p>Settings are re-read every few seconds, so every backend instance follows them. Until
 * the tables exist, everything is synced and pausing is not possible.</p>
 */
@Service
@Slf4j
public class SyncControl {

    /** Tables with their own schema, synced by ReconciliationSyncService / CoreChangeSync. */
    static final List<String> CORE = List.of("patients", "appointments", "procedurelogs", "documents");

    /** Everything a clinic can choose to sync. */
    public static final List<String> SYNCABLE = Stream.of(
                    CORE.stream(),
                    OdResourceCatalog.LISTS.stream().map(Resource::resource),
                    OdResourceCatalog.PER_PARENT.stream().map(Resource::resource))
            .flatMap(s -> s)
            .distinct()
            .sorted()
            .toList();

    private static final long CACHE_MS = 5_000;

    public record State(boolean paused, String pausedBy, Timestamp pausedAt, String reason) {
        static final State RUNNING = new State(false, null, null, null);
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    private volatile State state = State.RUNNING;
    private volatile long stateAt;
    /** Clinic id -> its choice (null resources = everything), with when it was read. */
    private final Map<UUID, Cached> selections = new ConcurrentHashMap<>();
    private volatile boolean warned;

    private record Cached(Set<String> resources, long at) {
    }

    public SyncControl(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ------------------------------------------------------------------ pause

    public boolean paused() {
        return state().paused();
    }

    public State state() {
        if (System.currentTimeMillis() - stateAt > CACHE_MS) {
            try {
                List<State> rows = jdbc.query(
                        "SELECT paused, paused_by, paused_at, reason FROM sync_control WHERE id = 1",
                        (rs, i) -> new State(rs.getBoolean("paused"), rs.getString("paused_by"),
                                rs.getTimestamp("paused_at"), rs.getString("reason")));
                state = rows.isEmpty() ? State.RUNNING : rows.get(0);
            } catch (Exception e) {
                missingTables(e);
                state = State.RUNNING;
            }
            stateAt = System.currentTimeMillis();
        }
        return state;
    }

    public State pause(String by, String reason) {
        write(() -> jdbc.update("""
                INSERT INTO sync_control (id, paused, paused_by, paused_at, reason, updated_at)
                VALUES (1, true, ?, now(), ?, now())
                ON CONFLICT (id) DO UPDATE SET paused = true, paused_by = EXCLUDED.paused_by,
                    paused_at = EXCLUDED.paused_at, reason = EXCLUDED.reason, updated_at = now()
                """, by, reason == null || reason.isBlank() ? null : reason.trim()));
        stateAt = 0;
        log.warn("Open Dental sync paused by {}{}", by, reason == null || reason.isBlank() ? "" : ": " + reason);
        return state();
    }

    public State resume(String by) {
        write(() -> jdbc.update("""
                INSERT INTO sync_control (id, paused, updated_at) VALUES (1, false, now())
                ON CONFLICT (id) DO UPDATE SET paused = false, paused_by = NULL, paused_at = NULL,
                    reason = NULL, updated_at = now()
                """));
        stateAt = 0;
        log.info("Open Dental sync resumed by {}", by);
        return state();
    }

    // -------------------------------------------------------------- selection

    /** True when the clinic has chosen not to sync this resource. */
    public boolean skips(Clinic clinic, String resource) {
        Set<String> chosen = chosen(clinic.getId());
        return chosen != null && !withDependencies(chosen).contains(resource);
    }

    /** The clinic's own choice; null = everything. */
    public Set<String> chosen(UUID clinicId) {
        Cached cached = selections.get(clinicId);
        if (cached == null || System.currentTimeMillis() - cached.at() > CACHE_MS) {
            Set<String> resources;
            try {
                List<Set<String>> rows = jdbc.query(
                        "SELECT resources FROM clinic_sync_resources WHERE clinic_id = ?",
                        (rs, i) -> toSet(rs.getArray("resources")), clinicId);
                resources = rows.isEmpty() ? null : rows.get(0);
            } catch (Exception e) {
                missingTables(e);
                resources = null;
            }
            cached = new Cached(resources, System.currentTimeMillis());
            selections.put(clinicId, cached);
        }
        return cached.resources();
    }

    /**
     * Saves a clinic's choice. {@code resources} null = sync everything.
     *
     * @throws IllegalArgumentException for a name that cannot be synced
     */
    public void choose(UUID clinicId, Collection<String> resources, String by) {
        String[] names = null;
        if (resources != null) {
            List<String> unknown = resources.stream().filter(r -> !SYNCABLE.contains(r)).toList();
            if (!unknown.isEmpty()) {
                throw new IllegalArgumentException("Not something that can be synced: " + String.join(", ", unknown));
            }
            names = resources.stream().distinct().sorted().toArray(String[]::new);
        }
        String[] value = names;
        write(() -> jdbc.update("""
                INSERT INTO clinic_sync_resources (clinic_id, resources, updated_by, updated_at)
                VALUES (?, ?, ?, now())
                ON CONFLICT (clinic_id) DO UPDATE SET resources = EXCLUDED.resources,
                    updated_by = EXCLUDED.updated_by, updated_at = now()
                """, clinicId, value, by));
        selections.remove(clinicId);
    }

    /** What is actually synced for a choice: the chosen resources and the ones they are read through. */
    static Set<String> withDependencies(Set<String> chosen) {
        Set<String> all = new LinkedHashSet<>(chosen);
        boolean grew = true;
        while (grew) {
            grew = false;
            for (Resource r : OdResourceCatalog.PER_PARENT) {
                if (all.contains(r.resource()) && all.add(r.parent())) {
                    grew = true;
                }
            }
        }
        return all;
    }

    /** Synced only because something chosen needs it. */
    public static List<String> addedForDependencies(Set<String> chosen) {
        List<String> added = new ArrayList<>(withDependencies(chosen));
        added.removeAll(chosen);
        return added;
    }

    /** Saves a setting; a clear message when the tables have not been created yet. */
    private void write(Runnable update) {
        try {
            tx.executeWithoutResult(s -> update.run());
        } catch (BadSqlGrammarException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Sync settings need their tables: run supabase-sync-control.sql in Supabase first.");
        }
    }

    private static Set<String> toSet(Array array) throws java.sql.SQLException {
        if (array == null) {
            return null;
        }
        return new LinkedHashSet<>(Arrays.asList((String[]) array.getArray()));
    }

    private void missingTables(Exception e) {
        if (!warned) {
            warned = true;
            log.warn("Sync settings unavailable (run supabase-sync-control.sql): syncing everything. {}", e.getMessage());
        }
    }
}
