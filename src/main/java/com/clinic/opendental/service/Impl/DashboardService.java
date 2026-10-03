package com.clinic.opendental.service.Impl;

import com.clinic.opendental.exception.ApiException;
import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.repository.ClinicRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The home dashboard, computed from our database (never Open Dental). Every figure is
 * computed on its own: one that cannot be (e.g. payments not synced yet) comes back as
 * null and is listed in {@code unavailable}, instead of failing the whole page.
 */
@Service
@Slf4j
public class DashboardService {

    /** Statuses that are on the schedule (not broken, planned or on the unscheduled list). */
    private static final String BOOKED = "('Scheduled','Complete','ASAP')";
    /** Open Dental stores appointment length in 5-minute steps, one pattern character each. */
    private static final int MINUTES_PER_PATTERN_STEP = 5;

    private final JdbcTemplate jdbc;
    private final ClinicRepository clinicRepository;
    private final FullSyncService fullSyncService;

    public DashboardService(JdbcTemplate jdbc, ClinicRepository clinicRepository, FullSyncService fullSyncService) {
        this.jdbc = jdbc;
        this.clinicRepository = clinicRepository;
        this.fullSyncService = fullSyncService;
    }

    public Map<String, Object> summary(LocalDate day) {
        UUID clinicId = clinicRepository.findByIsActiveTrue().stream().findFirst().map(Clinic::getId)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "No active clinic configured. Please register a clinic in the clinics table."));
        LocalDate weekStart = day.with(DayOfWeek.MONDAY);
        LocalDate monthStart = day.withDayOfMonth(1);
        List<String> unavailable = new ArrayList<>();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("date", day.toString());
        out.put("appointments", figure("appointments", unavailable, () -> appointments(clinicId, day)));
        out.put("providers", figure("providers", unavailable, () -> providers(clinicId, day)));
        out.put("patients", figure("patients", unavailable, () -> patients(clinicId, day)));
        out.put("production", figure("production", unavailable, () -> Map.of(
                "monthToDate", sum("SELECT COALESCE(SUM(proc_fee), 0) FROM procedure_logs WHERE clinic_id = ? "
                        + "AND proc_status = 'C' AND proc_date BETWEEN ? AND ?", clinicId, Date.valueOf(monthStart), Date.valueOf(day)),
                "lastMonthToDate", sum("SELECT COALESCE(SUM(proc_fee), 0) FROM procedure_logs WHERE clinic_id = ? "
                        + "AND proc_status = 'C' AND proc_date BETWEEN ? AND ?", clinicId,
                        Date.valueOf(monthStart.minusMonths(1)), Date.valueOf(day.minusMonths(1))))));
        out.put("collections", figure("collections", unavailable, () -> collections(clinicId, weekStart)));
        out.put("attention", attention(clinicId, day, weekStart, unavailable));
        out.put("recentPatients", figure("recentPatients", unavailable, () -> recentPatients(clinicId, day)));
        out.put("sync", sync(clinicId, unavailable));
        out.put("unavailable", unavailable);
        return out;
    }

    private Map<String, Object> appointments(UUID clinicId, LocalDate day) {
        List<Map<String, Object>> list = jdbc.query("""
                        SELECT a.apt_num, a.pat_num, a.apt_date_time, a.apt_status, a.pattern, a.note, a.proc_descript,
                               a.prov_num, a.prov_abbr, a.date_time_arrived, a.date_time_dismissed,
                               p.f_name, p.l_name, p.preferred, pr.f_name AS prov_f_name, pr.l_name AS prov_l_name, pr.abbrev
                        FROM appointments a
                        LEFT JOIN patients p ON p.clinic_id = a.clinic_id AND p.pat_num = a.pat_num
                        LEFT JOIN providers pr ON pr.clinic_id = a.clinic_id AND pr.prov_num = a.prov_num
                        WHERE a.clinic_id = ? AND a.apt_date_time >= ? AND a.apt_date_time < ?
                          AND a.apt_status NOT IN ('Planned', 'UnschedList', 'PtNote', 'PtNoteCompleted')
                        ORDER BY a.apt_date_time
                        """,
                (rs, i) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    Timestamp at = rs.getTimestamp("apt_date_time");
                    Timestamp arrived = rs.getTimestamp("date_time_arrived");
                    Timestamp dismissed = rs.getTimestamp("date_time_dismissed");
                    String pattern = rs.getString("pattern");
                    row.put("aptNum", rs.getLong("apt_num"));
                    row.put("patNum", rs.getLong("pat_num"));
                    row.put("patientName", name(rs.getString("preferred"), rs.getString("f_name"), rs.getString("l_name"),
                            "Patient #" + rs.getLong("pat_num")));
                    row.put("time", at == null ? null : at.toLocalDateTime().toString());
                    row.put("minutes", pattern == null || pattern.isEmpty() ? null : pattern.length() * MINUTES_PER_PATTERN_STEP);
                    row.put("treatment", firstNonBlank(rs.getString("proc_descript"), rs.getString("note"), "Appointment"));
                    row.put("provider", providerName(rs.getString("prov_f_name"), rs.getString("prov_l_name"),
                            firstNonBlank(rs.getString("abbrev"), rs.getString("prov_abbr"), null)));
                    row.put("status", rs.getString("apt_status"));
                    row.put("arrived", isSet(arrived));
                    row.put("dismissed", isSet(dismissed));
                    return row;
                },
                clinicId, Timestamp.valueOf(day.atStartOfDay()), Timestamp.valueOf(day.plusDays(1).atStartOfDay()));

        Map<String, Integer> byStatus = new LinkedHashMap<>();
        list.forEach(row -> byStatus.merge(displayStatus(row), 1, Integer::sum));
        long booked = list.stream().filter(row -> !"Broken".equals(row.get("status"))).count();
        LocalDateTime now = LocalDateTime.now();
        String next = list.stream()
                .filter(row -> row.get("time") != null && "Scheduled".equals(row.get("status")) && !(Boolean) row.get("arrived"))
                .map(row -> (String) row.get("time"))
                .filter(time -> !LocalDateTime.parse(time).isBefore(now))
                .findFirst().orElse(null);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("today", booked);
        out.put("sameDayLastWeek", count("SELECT COUNT(*) FROM appointments WHERE clinic_id = ? AND apt_date_time >= ? "
                        + "AND apt_date_time < ? AND apt_status IN " + BOOKED, clinicId,
                Timestamp.valueOf(day.minusWeeks(1).atStartOfDay()), Timestamp.valueOf(day.minusWeeks(1).plusDays(1).atStartOfDay())));
        out.put("byStatus", byStatus);
        out.put("nextAt", next);
        out.put("list", list);
        return out;
    }

    /** Check-in state matters more on the day than Open Dental's status. */
    private static String displayStatus(Map<String, Object> row) {
        String status = (String) row.get("status");
        if ("Complete".equals(status) || Boolean.TRUE.equals(row.get("dismissed"))) return "Completed";
        if ("Broken".equals(status)) return "Broken";
        if (Boolean.TRUE.equals(row.get("arrived"))) return "Checked in";
        return "Scheduled";
    }

    private Map<String, Object> providers(UUID clinicId, LocalDate day) {
        return Map.of(
                "onDuty", count("SELECT COUNT(DISTINCT prov_num) FROM appointments WHERE clinic_id = ? AND prov_num > 0 "
                                + "AND apt_date_time >= ? AND apt_date_time < ? AND apt_status IN " + BOOKED,
                        clinicId, Timestamp.valueOf(day.atStartOfDay()), Timestamp.valueOf(day.plusDays(1).atStartOfDay())),
                "total", count("SELECT COUNT(*) FROM providers WHERE clinic_id = ? AND COALESCE(prov_status, '') <> 'Deleted'",
                        clinicId));
    }

    private Map<String, Object> patients(UUID clinicId, LocalDate day) {
        return Map.of(
                "active", count("SELECT COUNT(*) FROM patients WHERE clinic_id = ? AND pat_status = 'Patient'", clinicId),
                "total", count("SELECT COUNT(*) FROM patients WHERE clinic_id = ?", clinicId),
                "newLast30Days", count("SELECT COUNT(*) FROM patients WHERE clinic_id = ? AND date_first_visit > ? "
                        + "AND date_first_visit <= ?", clinicId, Date.valueOf(day.minusDays(30)), Date.valueOf(day)),
                "newPrevious30Days", count("SELECT COUNT(*) FROM patients WHERE clinic_id = ? AND date_first_visit > ? "
                        + "AND date_first_visit <= ?", clinicId, Date.valueOf(day.minusDays(60)), Date.valueOf(day.minusDays(30))));
    }

    /** Payments by day, for this week and last week (Monday first). */
    private Map<String, Object> collections(UUID clinicId, LocalDate weekStart) {
        Map<LocalDate, BigDecimal> byDay = new LinkedHashMap<>();
        jdbc.query("""
                        SELECT (data ->> 'PayDate')::date AS day, SUM((data ->> 'PayAmt')::numeric) AS amount
                        FROM od_resource_records
                        WHERE clinic_id = ? AND resource = 'payments'
                          AND (data ->> 'PayDate') ~ '^\\d{4}-\\d{2}-\\d{2}'
                          AND (data ->> 'PayAmt') ~ '^-?[0-9]+(\\.[0-9]+)?$'
                          AND (data ->> 'PayDate')::date BETWEEN ? AND ?
                        GROUP BY 1
                        """,
                rs -> {
                    byDay.put(rs.getDate("day").toLocalDate(), rs.getBigDecimal("amount"));
                },
                clinicId, Date.valueOf(weekStart.minusWeeks(1)), Date.valueOf(weekStart.plusDays(6)));
        List<Map<String, Object>> thisWeek = week(weekStart, byDay);
        List<Map<String, Object>> lastWeek = week(weekStart.minusWeeks(1), byDay);
        return Map.of(
                "thisWeek", total(thisWeek),
                "lastWeek", total(lastWeek),
                "dailyThisWeek", thisWeek,
                "dailyLastWeek", lastWeek);
    }

    private static List<Map<String, Object>> week(LocalDate start, Map<LocalDate, BigDecimal> byDay) {
        List<Map<String, Object>> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = start.plusDays(i);
            days.add(Map.of(
                    "day", date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    "date", date.toString(),
                    "value", byDay.getOrDefault(date, BigDecimal.ZERO)));
        }
        return days;
    }

    private static BigDecimal total(List<Map<String, Object>> days) {
        return days.stream().map(d -> (BigDecimal) d.get("value")).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<String, Object> attention(UUID clinicId, LocalDate day, LocalDate weekStart, List<String> unavailable) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("plannedAppointments", figure("plannedAppointments", unavailable, () -> count(
                "SELECT COUNT(*) FROM appointments WHERE clinic_id = ? AND apt_status = 'Planned'", clinicId)));
        out.put("brokenThisWeek", figure("brokenThisWeek", unavailable, () -> count(
                "SELECT COUNT(*) FROM appointments WHERE clinic_id = ? AND apt_status = 'Broken' "
                        + "AND apt_date_time >= ? AND apt_date_time < ?", clinicId,
                Timestamp.valueOf(weekStart.atStartOfDay()), Timestamp.valueOf(weekStart.plusDays(7).atStartOfDay()))));
        out.put("recallsDueNext7Days", figure("recalls", unavailable, () -> count("""
                SELECT COUNT(*) FROM od_resource_records
                WHERE clinic_id = ? AND resource = 'recalls'
                  AND (data ->> 'DateDue') ~ '^\\d{4}-\\d{2}-\\d{2}'
                  AND (data ->> 'DateDue')::date BETWEEN ? AND ?
                  AND COALESCE(data ->> 'DateScheduled', '0001-01-01') LIKE '0001-01-01%'
                  AND COALESCE(data ->> 'IsDisabled', 'false') <> 'true'
                """, clinicId, Date.valueOf(day), Date.valueOf(day.plusDays(7)))));
        out.put("openClaims", figure("claims", unavailable, () -> jdbc.queryForMap("""
                SELECT COUNT(*) AS count,
                       COALESCE(SUM(CASE WHEN (data ->> 'ClaimFee') ~ '^-?[0-9]+(\\.[0-9]+)?$'
                                         THEN (data ->> 'ClaimFee')::numeric END), 0) AS amount
                FROM od_resource_records
                WHERE clinic_id = ? AND resource = 'claims' AND data ->> 'ClaimStatus' IN ('U', 'H', 'W', 'P', 'S')
                """, clinicId)));
        return out;
    }

    private List<Map<String, Object>> recentPatients(UUID clinicId, LocalDate day) {
        return jdbc.query("""
                        SELECT p.pat_num, p.f_name, p.l_name, p.preferred, p.date_first_visit,
                               (SELECT MIN(a.apt_date_time) FROM appointments a
                                 WHERE a.clinic_id = p.clinic_id AND a.pat_num = p.pat_num AND a.apt_date_time >= ?
                                   AND a.apt_status IN ('Scheduled', 'ASAP')) AS next_apt
                        FROM patients p
                        WHERE p.clinic_id = ?
                        ORDER BY p.date_first_visit DESC NULLS LAST, p.pat_num DESC
                        LIMIT 5
                        """,
                (rs, i) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    Timestamp next = rs.getTimestamp("next_apt");
                    Date firstVisit = rs.getDate("date_first_visit");
                    row.put("patNum", rs.getLong("pat_num"));
                    row.put("name", name(rs.getString("preferred"), rs.getString("f_name"), rs.getString("l_name"),
                            "Patient #" + rs.getLong("pat_num")));
                    row.put("nextAppointment", next == null ? null : next.toLocalDateTime().toString());
                    row.put("firstVisit", firstVisit == null ? null : firstVisit.toLocalDate().toString());
                    return row;
                },
                Timestamp.valueOf(day.atStartOfDay()), clinicId);
    }

    private Map<String, Object> sync(UUID clinicId, List<String> unavailable) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pendingChanges", figure("syncQueue", unavailable, () -> count(
                "SELECT COUNT(*) FROM od_sync_queue WHERE clinic_id = ? AND status IN ('PENDING', 'IN_PROGRESS')", clinicId)));
        out.put("failedChanges", figure("syncQueue", unavailable, () -> count(
                "SELECT COUNT(*) FROM od_sync_queue WHERE clinic_id = ? AND status = 'FAILED'", clinicId)));
        out.put("lastFullSync", figure("syncRuns", unavailable, () -> jdbc.query(
                "SELECT completed_at, status FROM sync_runs WHERE clinic_id = ? AND sync_type LIKE 'full:%' "
                        + "AND completed_at IS NOT NULL ORDER BY completed_at DESC LIMIT 1",
                (rs, i) -> Map.of("at", rs.getTimestamp("completed_at").toInstant().toString(), "status", rs.getString("status")),
                clinicId).stream().findFirst().orElse(null)));
        out.put("running", fullSyncService.isRunning());
        return out;
    }

    // ---------------------------------------------------------------- helpers

    private <T> T figure(String name, List<String> unavailable, Supplier<T> compute) {
        try {
            return compute.get();
        } catch (Exception e) {
            log.warn("Dashboard figure '{}' unavailable: {}", name, e.getMessage());
            if (!unavailable.contains(name)) {
                unavailable.add(name);
            }
            return null;
        }
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private BigDecimal sum(String sql, Object... args) {
        BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class, args);
        return value == null ? BigDecimal.ZERO : value;
    }

    private static boolean isSet(Timestamp timestamp) {
        return timestamp != null && timestamp.toLocalDateTime().getYear() > 1;
    }

    private static String name(String preferred, String first, String last, String fallback) {
        String given = firstNonBlank(preferred, first, "");
        String full = (given + " " + (last == null ? "" : last)).trim();
        return full.isEmpty() ? fallback : full;
    }

    private static String providerName(String first, String last, String abbreviation) {
        String full = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
        if (!full.isEmpty()) return "Dr. " + full;
        return abbreviation;
    }

    private static String firstNonBlank(String a, String b, String fallback) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return fallback;
    }
}
