package com.clinic.opendental.service.Impl;

import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.service.Impl.OdResourceCatalog.Resource;
import com.clinic.opendental.service.Impl.OdResourceCatalog.Tier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.management.ManagementFactory;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * sync_cursors: per clinic and listed resource, where the change pull resumes (the
 * newest DateTStamp merged), whether Open Dental can filter it by DateTStamp, when it is
 * next due, and a lease so only one backend instance works on it at a time.
 */
@Component
@Slf4j
public class SyncCursors {

    /** A lease outlives any single pull; a crashed instance's lease simply expires. */
    private static final Duration LEASE = Duration.ofMinutes(60);
    /** Failures back off exponentially up to this. */
    private static final Duration MAX_BACKOFF = Duration.ofHours(1);

    /** A due resource, claimed by this instance. */
    public record Cursor(UUID clinicId, String resource, Tier tier, boolean supportsChanges,
                         LocalDateTime watermark, Timestamp lastFullAt) {
        /** Open Dental can be asked for changes only, from a known point. */
        boolean pullChanges() {
            return supportsChanges && watermark != null && lastFullAt != null;
        }
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    /** Identifies this backend instance in locked_by. */
    private final String owner = ManagementFactory.getRuntimeMXBean().getName() + "/" + UUID.randomUUID().toString().substring(0, 8);

    public SyncCursors(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** Makes sure every listed resource of the clinic has a cursor, with its current tier. */
    public void ensure(Clinic clinic) {
        List<Object[]> rows = new ArrayList<>();
        for (Resource resource : OdResourceCatalog.LISTS) {
            rows.add(new Object[]{clinic.getId(), resource.resource(), OdResourceCatalog.tier(resource.resource()).dbName()});
        }
        tx.executeWithoutResult(status -> jdbc.batchUpdate("""
                INSERT INTO sync_cursors (clinic_id, resource, tier) VALUES (?, ?, ?)
                ON CONFLICT (clinic_id, resource) DO UPDATE SET tier = EXCLUDED.tier, updated_at = now()
                WHERE sync_cursors.tier <> EXCLUDED.tier
                """, rows));
    }

    /**
     * Claims up to {@code limit} due resources of the clinic for this instance. Rows another
     * instance holds (or is claiming right now) are skipped, so instances never overlap.
     */
    public List<Cursor> claimDue(Clinic clinic, int limit) {
        List<Cursor> claimed = tx.execute(status -> jdbc.query("""
                        UPDATE sync_cursors c
                        SET status = 'running', locked_by = ?, locked_until = now() + make_interval(secs => ?), updated_at = now()
                        WHERE (c.clinic_id, c.resource) IN (
                            SELECT clinic_id, resource FROM sync_cursors
                            WHERE clinic_id = ? AND next_due_at <= now() AND (locked_until IS NULL OR locked_until < now())
                              AND resource NOT LIKE 'core:%'
                            ORDER BY next_due_at
                            LIMIT ?
                            FOR UPDATE SKIP LOCKED)
                        RETURNING c.clinic_id, c.resource, c.tier, c.supports_tstamp, c.watermark, c.last_full_at
                        """,
                (rs, i) -> new Cursor(
                        rs.getObject("clinic_id", UUID.class),
                        rs.getString("resource"),
                        Tier.valueOf(rs.getString("tier").toUpperCase(java.util.Locale.ROOT)),
                        rs.getBoolean("supports_tstamp"),
                        rs.getTimestamp("watermark") == null ? null : rs.getTimestamp("watermark").toLocalDateTime(),
                        rs.getTimestamp("last_full_at")),
                owner, LEASE.toSeconds(), clinic.getId(), limit));
        return claimed == null ? List.of() : claimed;
    }

    /** A pull finished: release the lease, move the watermark forward and set the next due time. */
    public void succeeded(Cursor cursor, Timestamp latestChange, boolean changesOnly, Duration nextIn) {
        tx.executeWithoutResult(status -> jdbc.update("""
                        UPDATE sync_cursors
                        SET status = 'idle', locked_by = NULL, locked_until = NULL, consecutive_failures = 0, last_error = NULL,
                            watermark = GREATEST(watermark, ?::timestamp),
                            last_incremental_at = CASE WHEN ? THEN now() ELSE last_incremental_at END,
                            next_due_at = now() + make_interval(secs => ?), updated_at = now()
                        WHERE clinic_id = ? AND resource = ?
                        """,
                latestChange, changesOnly, nextIn.toSeconds(), cursor.clinicId(), cursor.resource()));
    }

    /**
     * A pull failed: release the lease and retry later, backing off with each failure in a
     * row. {@code filterRejected} turns the change pull off when Open Dental refused it.
     */
    public void failed(Cursor cursor, String error, Duration interval, boolean filterRejected) {
        tx.executeWithoutResult(status -> jdbc.update("""
                        UPDATE sync_cursors
                        SET status = 'failed', locked_by = NULL, locked_until = NULL,
                            consecutive_failures = consecutive_failures + 1, last_error = left(?, 1000),
                            supports_tstamp = supports_tstamp AND NOT ?,
                            next_due_at = now() + make_interval(secs => LEAST(? * power(2, consecutive_failures), ?)),
                            updated_at = now()
                        WHERE clinic_id = ? AND resource = ?
                        """,
                error == null ? "unknown error" : error, filterRejected,
                interval.toSeconds(), MAX_BACKOFF.toSeconds(), cursor.clinicId(), cursor.resource()));
    }

    /**
     * Where the change pull of a core table (patients, appointments, procedure logs) resumes:
     * the newest Open Dental DateTStamp merged so far. Kept as {@code core:<table>} rows,
     * which the listed-resource scheduler leaves alone.
     */
    public LocalDateTime coreWatermark(Clinic clinic, String table) {
        List<Timestamp> found = jdbc.queryForList(
                "SELECT watermark FROM sync_cursors WHERE clinic_id = ? AND resource = ? AND watermark IS NOT NULL",
                Timestamp.class, clinic.getId(), "core:" + table);
        return found.isEmpty() ? null : found.get(0).toLocalDateTime();
    }

    public void coreWatermark(Clinic clinic, String table, LocalDateTime latest) {
        if (latest == null) {
            return;
        }
        tx.executeWithoutResult(status -> jdbc.update("""
                        INSERT INTO sync_cursors (clinic_id, resource, tier, supports_tstamp, watermark, last_incremental_at, status)
                        VALUES (?, ?, 'hot', true, ?, now(), 'idle')
                        ON CONFLICT (clinic_id, resource) DO UPDATE
                        SET watermark = GREATEST(sync_cursors.watermark, EXCLUDED.watermark),
                            last_incremental_at = now(), updated_at = now()
                        """,
                clinic.getId(), "core:" + table, Timestamp.valueOf(latest)));
    }

    /**
     * After a complete, error-free full read of a listed resource (nightly, Force Sync, or a
     * refresh): record it, remember whether Open Dental filters it by DateTStamp, and start
     * the change pull from the newest change seen.
     */
    public void fullPassDone(Clinic clinic, Resource resource, ResourceMirrorService.Result result, boolean supportsChanges) {
        tx.executeWithoutResult(status -> jdbc.update("""
                        INSERT INTO sync_cursors (clinic_id, resource, tier, supports_tstamp, watermark, last_full_at)
                        VALUES (?, ?, ?, ?, ?::timestamp, now())
                        ON CONFLICT (clinic_id, resource) DO UPDATE
                        SET supports_tstamp = EXCLUDED.supports_tstamp,
                            watermark = GREATEST(sync_cursors.watermark, EXCLUDED.watermark),
                            last_full_at = now(), updated_at = now()
                        """,
                clinic.getId(), resource.resource(), OdResourceCatalog.tier(resource.resource()).dbName(),
                supportsChanges, result.latestChange()));
        log.debug("{} for clinic {}: full read done, change pull {}", resource.resource(), clinic.getClinicCode(),
                supportsChanges ? "on" : "off");
    }
}
