package com.clinic.opendental.service.Impl;

import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.repository.ClinicRepository;
import com.clinic.opendental.security.Permission;
import com.clinic.opendental.security.PermissionResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Read-only tools the AI Assistant may call. Every tool is a fixed, clinic-scoped query
 * over our database: the model never writes SQL, never changes data, and never sees
 * social security numbers. Results are capped so a single call stays small.
 */
@Component
public class AssistantTools {

    static final int MAX_ROWS = 50;
    private static final int MAX_RESULT_CHARS = 20_000;
    private static final Pattern SENSITIVE_FIELD = Pattern.compile("(?i).*(ssn|password|secret|apikey|customerkey).*");
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * A tool in the chat-completions "function" format (name, description, JSON Schema
     * parameters), the permission it needs (null: any signed-in user) and the code that
     * answers it. The handler gets the caller's permissions to leave out what they may not see.
     */
    record Definition(Map<String, Object> tool, Permission required, Handler handler) {
    }

    @FunctionalInterface
    interface Handler {
        Object apply(UUID clinicId, JsonNode input, Set<Permission> permissions);
    }

    private final JdbcTemplate jdbc;
    private final ClinicRepository clinicRepository;
    private final DashboardService dashboardService;
    private final ResourceRecordStore records;
    private final PermissionResolver permissionResolver;
    private final Map<String, Definition> definitions = new LinkedHashMap<>();

    public AssistantTools(JdbcTemplate jdbc, ClinicRepository clinicRepository,
                          DashboardService dashboardService, ResourceRecordStore records,
                          PermissionResolver permissionResolver) {
        this.jdbc = jdbc;
        this.clinicRepository = clinicRepository;
        this.dashboardService = dashboardService;
        this.records = records;
        this.permissionResolver = permissionResolver;
        register();
    }

    /** The tools someone with these permissions may use, as {"type": "function", "function": {...}} entries. */
    List<Map<String, Object>> tools(Set<Permission> permissions) {
        return definitions.values().stream()
                .filter(d -> d.required() == null || permissions.contains(d.required()))
                .map(Definition::tool).toList();
    }

    /** Runs a tool and returns its result as JSON text (capped). Unknown or not-permitted tools are an error. */
    String run(String name, JsonNode input, Set<Permission> permissions) {
        Definition definition = definitions.get(name);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown tool: " + name);
        }
        if (definition.required() != null && !permissions.contains(definition.required())) {
            throw new IllegalArgumentException("Your role doesn't allow " + name);
        }
        Object result = definition.handler().apply(clinicId(), input == null ? JSON.createObjectNode() : input, permissions);
        JsonNode tree = scrub(JSON.valueToTree(result));
        String text = tree.toString();
        return text.length() > MAX_RESULT_CHARS
                ? text.substring(0, MAX_RESULT_CHARS) + "... [truncated: ask a narrower question]"
                : text;
    }

    // ------------------------------------------------------------------ tool definitions

    private void register() {
        add("get_practice_overview",
                "Today's key figures for the practice: appointment counts and status breakdown, providers on duty, "
                        + "patient counts, production this month, collections this week, open claims, recalls due, "
                        + "planned appointments and sync health. Use for 'how is today looking' or a morning briefing.",
                Map.of("date", prop("string", "Day to summarise, yyyy-MM-dd. Defaults to today.")),
                List.of(), null,
                (clinicId, in, perms) -> dashboardService.summary(date(in, "date", LocalDate.now()),
                        perms.contains(Permission.BILLING_READ)));

        add("get_schedule",
                "Appointments in a date range (inclusive), with patient name, time, length, treatment, provider and "
                        + "status. Optionally only one patient. Use for schedule questions.",
                Map.of("date_from", prop("string", "First day, yyyy-MM-dd."),
                        "date_to", prop("string", "Last day, yyyy-MM-dd. Defaults to date_from."),
                        "pat_num", prop("integer", "Only this patient's appointments.")),
                List.of("date_from"), Permission.APPOINTMENTS_READ,
                (clinicId, in, perms) -> schedule(clinicId, in));

        add("find_patients",
                "Find patients by name, phone, email or patient number (PatNum). Returns up to 20 matches with "
                        + "basic details. Use before other patient tools when you only have a name.",
                Map.of("query", prop("string", "Name, phone, email or PatNum to search for.")),
                List.of("query"), Permission.PATIENTS_READ,
                (clinicId, in, perms) -> findPatients(clinicId, in));

        add("get_patient_summary",
                "Everything relevant about one patient for a pre-visit brief: demographics and contact details, "
                        + "allergies, problems, medications, insurance plans, balance, last and next appointments.",
                Map.of("pat_num", prop("integer", "The patient's PatNum.")),
                List.of("pat_num"), Permission.PATIENTS_READ,
                this::patientSummary);

        add("find_patients_with_allergy",
                "Patients who have an allergy whose name contains the given text (e.g. 'penicillin', 'latex'). "
                        + "Optionally only patients with appointments on a given day.",
                Map.of("allergy", prop("string", "Part of the allergy name."),
                        "appointment_date", prop("string", "Only patients booked on this day, yyyy-MM-dd.")),
                List.of("allergy"), Permission.CLINICAL_READ,
                (clinicId, in, perms) -> patientsWithAllergy(clinicId, in));

        add("get_open_claims",
                "Insurance claims that are still open (unsent, on hold, waiting to send, probably sent, or sent), "
                        + "oldest first, with patient, amount and dates. Optionally only claims at or above an amount.",
                Map.of("min_amount", prop("number", "Only claims with a fee at or above this amount.")),
                List.of(), Permission.BILLING_READ,
                (clinicId, in, perms) -> openClaims(clinicId, in));

        add("get_collections",
                "Payments received and production (completed procedure fees) for a date range, per day and in total.",
                Map.of("date_from", prop("string", "First day, yyyy-MM-dd."),
                        "date_to", prop("string", "Last day, yyyy-MM-dd.")),
                List.of("date_from", "date_to"), Permission.BILLING_READ,
                (clinicId, in, perms) -> collections(clinicId, in));

        add("get_sync_status",
                "Health of the link with Open Dental: changes waiting to be sent, changes that failed (with the "
                        + "error), and the last full sync. Use when someone asks why data is missing or not in Open Dental.",
                Map.of(),
                List.of(), Permission.SYNC_MANAGE,
                (clinicId, in, perms) -> syncStatus(clinicId));

        add("search_records",
                "Read synced Open Dental records of any type, optionally for one patient. Types include: recalls, "
                        + "diseases, medicationpats, patplans, inssubs, insplans, carriers, benefits, claims, claimprocs, "
                        + "payments, paysplits, treatplans, perioexams, procedurecodes, fees, appointmenttypes, operatories, "
                        + "providers, popups, guardians, vitalsigns, rxpats, statements. Use when no specific tool fits.",
                Map.of("resource", prop("string", "Record type, lower case, e.g. 'recalls'."),
                        "pat_num", prop("integer", "Only this patient's records."),
                        "limit", prop("integer", "Maximum rows (1-50). Defaults to 25.")),
                List.of("resource"), null,
                this::searchRecords);
    }

    private void add(String name, String description, Map<String, Map<String, Object>> properties,
                     List<String> required, Permission permission, Handler handler) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", required);
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", name);
        function.put("description", description);
        function.put("parameters", parameters);
        definitions.put(name, new Definition(Map.of("type", "function", "function", function), permission, handler));
    }

    private static Map<String, Object> prop(String type, String description) {
        return Map.of("type", type, "description", description);
    }

    // ------------------------------------------------------------------ tool implementations

    private Object schedule(UUID clinicId, JsonNode in) {
        LocalDate from = date(in, "date_from", LocalDate.now());
        LocalDate to = date(in, "date_to", from);
        Long patNum = longValue(in, "pat_num");
        String sql = """
                SELECT a.apt_num, a.pat_num, a.apt_date_time, a.apt_status, a.pattern, a.note, a.proc_descript,
                       a.op, a.date_time_arrived, p.f_name, p.l_name, p.preferred,
                       pr.f_name AS prov_f_name, pr.l_name AS prov_l_name, a.prov_abbr
                FROM appointments a
                LEFT JOIN patients p ON p.clinic_id = a.clinic_id AND p.pat_num = a.pat_num
                LEFT JOIN providers pr ON pr.clinic_id = a.clinic_id AND pr.prov_num = a.prov_num
                WHERE a.clinic_id = ? AND a.apt_date_time >= ? AND a.apt_date_time < ?
                """ + (patNum != null ? " AND a.pat_num = ?" : "") + " ORDER BY a.apt_date_time LIMIT " + MAX_ROWS;
        List<Object> args = new ArrayList<>(List.of(clinicId, Timestamp.valueOf(from.atStartOfDay()),
                Timestamp.valueOf(to.plusDays(1).atStartOfDay())));
        if (patNum != null) args.add(patNum);
        return jdbc.query(sql, (rs, i) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            String pattern = rs.getString("pattern");
            Timestamp arrived = rs.getTimestamp("date_time_arrived");
            row.put("apt_num", rs.getLong("apt_num"));
            row.put("pat_num", rs.getLong("pat_num"));
            row.put("patient", name(rs.getString("preferred"), rs.getString("f_name"), rs.getString("l_name")));
            row.put("time", String.valueOf(rs.getTimestamp("apt_date_time")));
            row.put("minutes", pattern == null ? null : pattern.length() * 5);
            row.put("status", rs.getString("apt_status"));
            row.put("treatment", firstNonBlank(rs.getString("proc_descript"), rs.getString("note")));
            row.put("provider", firstNonBlank(name(null, rs.getString("prov_f_name"), rs.getString("prov_l_name")),
                    rs.getString("prov_abbr")));
            row.put("operatory", rs.getLong("op"));
            row.put("checked_in", arrived != null && arrived.toLocalDateTime().getYear() > 1);
            return row;
        }, args.toArray());
    }

    private Object findPatients(UUID clinicId, JsonNode in) {
        String query = text(in, "query").trim();
        if (query.isEmpty()) return List.of();
        String like = "%" + query.toLowerCase() + "%";
        Long patNum = query.matches("\\d+") ? Long.valueOf(query) : null;
        return jdbc.query("""
                        SELECT pat_num, f_name, l_name, preferred, birthdate, pat_status, wireless_phone, hm_phone, email
                        FROM patients
                        WHERE clinic_id = ? AND (pat_num = ? OR LOWER(f_name || ' ' || l_name) LIKE ? OR LOWER(l_name || ' ' || f_name) LIKE ?
                              OR LOWER(COALESCE(preferred, '')) LIKE ? OR LOWER(COALESCE(email, '')) LIKE ?
                              OR REGEXP_REPLACE(COALESCE(wireless_phone, '') || ' ' || COALESCE(hm_phone, ''), '[^0-9]', '', 'g') LIKE ?)
                        ORDER BY l_name, f_name LIMIT 20
                        """,
                (rs, i) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("pat_num", rs.getLong("pat_num"));
                    row.put("name", name(rs.getString("preferred"), rs.getString("f_name"), rs.getString("l_name")));
                    row.put("birthdate", String.valueOf(rs.getDate("birthdate")));
                    row.put("status", rs.getString("pat_status"));
                    row.put("phone", firstNonBlank(rs.getString("wireless_phone"), rs.getString("hm_phone")));
                    row.put("email", rs.getString("email"));
                    return row;
                },
                clinicId, patNum == null ? -1L : patNum, like, like, like, like, phoneLike(query));
    }

    /** Phone search only kicks in for 4+ digits; otherwise it matches nothing. */
    private static String phoneLike(String query) {
        String digits = query.replaceAll("[^0-9]", "");
        return digits.length() >= 4 ? "%" + digits + "%" : "\u0000no-phone\u0000";
    }

    private Object patientSummary(UUID clinicId, JsonNode in, Set<Permission> perms) {
        Long patNum = longValue(in, "pat_num");
        if (patNum == null) throw new IllegalArgumentException("pat_num is required");
        List<Map<String, Object>> patient = jdbc.query("""
                        SELECT pat_num, f_name, l_name, preferred, birthdate, gender, pat_status, wireless_phone, hm_phone,
                               email, address, city, state, zip, date_first_visit, est_balance, bal_total, ins_est, premed,
                               language, guarantor
                        FROM patients WHERE clinic_id = ? AND pat_num = ?
                        """,
                (rs, i) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("pat_num", rs.getLong("pat_num"));
                    row.put("name", name(rs.getString("preferred"), rs.getString("f_name"), rs.getString("l_name")));
                    row.put("birthdate", String.valueOf(rs.getDate("birthdate")));
                    row.put("gender", rs.getString("gender"));
                    row.put("status", rs.getString("pat_status"));
                    row.put("phone", firstNonBlank(rs.getString("wireless_phone"), rs.getString("hm_phone")));
                    row.put("email", rs.getString("email"));
                    row.put("address", String.join(", ", nonBlank(rs.getString("address"), rs.getString("city"),
                            rs.getString("state"), rs.getString("zip"))));
                    row.put("first_visit", String.valueOf(rs.getDate("date_first_visit")));
                    row.put("balance_total", rs.getBigDecimal("bal_total"));
                    row.put("insurance_estimate", rs.getBigDecimal("ins_est"));
                    row.put("premedicate", rs.getBoolean("premed"));
                    row.put("language", rs.getString("language"));
                    row.put("guarantor", rs.getLong("guarantor"));
                    return row;
                }, clinicId, patNum);
        if (patient.isEmpty()) {
            return Map.of("error", "No patient " + patNum + " in our database.");
        }
        Map<String, Object> out = new LinkedHashMap<>(patient.get(0));
        // Only the parts of the record this role may see.
        if (!perms.contains(Permission.BILLING_READ)) {
            out.remove("balance_total");
            out.remove("insurance_estimate");
        }
        if (perms.contains(Permission.CLINICAL_READ)) {
            out.put("allergies", records.list(clinicId, "allergies", patNum, MAX_ROWS, 0));
            out.put("problems", records.list(clinicId, "diseases", patNum, MAX_ROWS, 0));
            out.put("medications", records.list(clinicId, "medicationpats", patNum, MAX_ROWS, 0));
        }
        if (perms.contains(Permission.BILLING_READ)) {
            out.put("insurance_plans", records.list(clinicId, "patplans", patNum, MAX_ROWS, 0));
        }
        if (perms.contains(Permission.APPOINTMENTS_READ)) {
            out.put("recalls", records.list(clinicId, "recalls", patNum, MAX_ROWS, 0));
            out.put("last_appointment", appointmentNear(clinicId, patNum, false));
            out.put("next_appointment", appointmentNear(clinicId, patNum, true));
        }
        return out;
    }

    private Object appointmentNear(UUID clinicId, long patNum, boolean upcoming) {
        return jdbc.query("SELECT apt_num, apt_date_time, apt_status, proc_descript, note FROM appointments "
                        + "WHERE clinic_id = ? AND pat_num = ? AND apt_status NOT IN ('Planned', 'UnschedList') AND apt_date_time "
                        + (upcoming ? ">= now() ORDER BY apt_date_time ASC" : "< now() ORDER BY apt_date_time DESC") + " LIMIT 1",
                (rs, i) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("apt_num", rs.getLong("apt_num"));
                    row.put("time", String.valueOf(rs.getTimestamp("apt_date_time")));
                    row.put("status", rs.getString("apt_status"));
                    row.put("treatment", firstNonBlank(rs.getString("proc_descript"), rs.getString("note")));
                    return row;
                },
                clinicId, patNum).stream().findFirst().orElse(null);
    }

    private Object patientsWithAllergy(UUID clinicId, JsonNode in) {
        String allergy = "%" + text(in, "allergy").trim().toLowerCase() + "%";
        LocalDate day = date(in, "appointment_date", null);
        String sql = """
                SELECT r.pat_num, p.f_name, p.l_name, p.preferred,
                       r.data ->> 'defDescription' AS allergy, r.data ->> 'Reaction' AS reaction
                FROM od_resource_records r
                LEFT JOIN patients p ON p.clinic_id = r.clinic_id AND p.pat_num = r.pat_num
                WHERE r.clinic_id = ? AND r.resource = 'allergies'
                  AND LOWER(COALESCE(r.data ->> 'defDescription', '')) LIKE ?
                  AND LOWER(COALESCE(r.data ->> 'StatusIsActive', r.data ->> 'statusIsActive', 'true')) <> 'false'
                """ + (day != null
                ? " AND EXISTS (SELECT 1 FROM appointments a WHERE a.clinic_id = r.clinic_id AND a.pat_num = r.pat_num "
                + "AND a.apt_date_time >= ? AND a.apt_date_time < ?)" : "")
                + " ORDER BY p.l_name, p.f_name LIMIT " + MAX_ROWS;
        List<Object> args = new ArrayList<>(List.of(clinicId, allergy));
        if (day != null) {
            args.add(Timestamp.valueOf(day.atStartOfDay()));
            args.add(Timestamp.valueOf(day.plusDays(1).atStartOfDay()));
        }
        return jdbc.query(sql, (rs, i) -> Map.of(
                "pat_num", rs.getLong("pat_num"),
                "patient", name(rs.getString("preferred"), rs.getString("f_name"), rs.getString("l_name")),
                "allergy", String.valueOf(rs.getString("allergy")),
                "reaction", String.valueOf(rs.getString("reaction"))), args.toArray());
    }

    private Object openClaims(UUID clinicId, JsonNode in) {
        double min = in.path("min_amount").asDouble(0);
        return jdbc.query("""
                        SELECT r.record_key, r.pat_num, p.f_name, p.l_name, p.preferred,
                               r.data ->> 'ClaimStatus' AS status, r.data ->> 'ClaimFee' AS fee,
                               r.data ->> 'InsPayEst' AS insurance_estimate, r.data ->> 'DateService' AS date_service,
                               r.data ->> 'DateSent' AS date_sent, r.data ->> 'ClaimType' AS claim_type
                        FROM od_resource_records r
                        LEFT JOIN patients p ON p.clinic_id = r.clinic_id AND p.pat_num = r.pat_num
                        WHERE r.clinic_id = ? AND r.resource = 'claims' AND r.data ->> 'ClaimStatus' IN ('U', 'H', 'W', 'P', 'S')
                          AND COALESCE(NULLIF(regexp_replace(r.data ->> 'ClaimFee', '[^0-9.\\-]', '', 'g'), ''), '0')::numeric >= ?
                        ORDER BY r.data ->> 'DateService' NULLS LAST
                        LIMIT 50
                        """,
                (rs, i) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("claim_num", rs.getString("record_key"));
                    row.put("pat_num", rs.getLong("pat_num"));
                    row.put("patient", name(rs.getString("preferred"), rs.getString("f_name"), rs.getString("l_name")));
                    row.put("status", claimStatus(rs.getString("status")));
                    row.put("fee", rs.getString("fee"));
                    row.put("insurance_estimate", rs.getString("insurance_estimate"));
                    row.put("date_service", rs.getString("date_service"));
                    row.put("date_sent", rs.getString("date_sent"));
                    row.put("type", rs.getString("claim_type"));
                    return row;
                }, clinicId, java.math.BigDecimal.valueOf(min));
    }

    private Object collections(UUID clinicId, JsonNode in) {
        LocalDate from = date(in, "date_from", LocalDate.now().minusDays(6));
        LocalDate to = date(in, "date_to", LocalDate.now());
        List<Map<String, Object>> payments = jdbc.query("""
                        SELECT (data ->> 'PayDate')::date AS day, SUM((data ->> 'PayAmt')::numeric) AS amount, COUNT(*) AS count
                        FROM od_resource_records
                        WHERE clinic_id = ? AND resource = 'payments'
                          AND (data ->> 'PayDate') ~ '^\\d{4}-\\d{2}-\\d{2}' AND (data ->> 'PayAmt') ~ '^-?[0-9]+(\\.[0-9]+)?$'
                          AND (data ->> 'PayDate')::date BETWEEN ? AND ?
                        GROUP BY 1 ORDER BY 1
                        """,
                (rs, i) -> Map.of("day", rs.getDate("day").toString(), "amount", rs.getBigDecimal("amount"),
                        "payments", rs.getLong("count")),
                clinicId, Date.valueOf(from), Date.valueOf(to));
        Object production = jdbc.queryForObject("SELECT COALESCE(SUM(proc_fee), 0) FROM procedure_logs "
                        + "WHERE clinic_id = ? AND proc_status = 'C' AND proc_date BETWEEN ? AND ?",
                java.math.BigDecimal.class, clinicId, Date.valueOf(from), Date.valueOf(to));
        java.math.BigDecimal total = payments.stream().map(p -> (java.math.BigDecimal) p.get("amount"))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        return Map.of("date_from", from.toString(), "date_to", to.toString(), "collections_total", total,
                "collections_by_day", payments, "production_total", production);
    }

    private Object syncStatus(UUID clinicId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("waiting", jdbc.queryForObject("SELECT COUNT(*) FROM od_sync_queue WHERE clinic_id = ? "
                + "AND status IN ('PENDING', 'IN_PROGRESS')", Long.class, clinicId));
        out.put("failed", jdbc.queryForObject("SELECT COUNT(*) FROM od_sync_queue WHERE clinic_id = ? "
                + "AND status = 'FAILED'", Long.class, clinicId));
        out.put("recent_problems", jdbc.query("""
                        SELECT entity_type, operation, local_id, status, attempts, last_error, updated_at
                        FROM od_sync_queue
                        WHERE clinic_id = ? AND status IN ('PENDING', 'IN_PROGRESS', 'FAILED') AND last_error IS NOT NULL
                        ORDER BY updated_at DESC LIMIT 10
                        """,
                (rs, i) -> Map.of("record", rs.getString("entity_type") + " " + rs.getLong("local_id"),
                        "change", rs.getString("operation"), "status", rs.getString("status"),
                        "attempts", rs.getInt("attempts"),
                        "error", truncate(String.valueOf(rs.getString("last_error")), 300)),
                clinicId));
        Clinic clinic = clinicRepository.findById(clinicId).orElse(null);
        out.put("open_dental_address", clinic == null ? null : clinic.getBaseUrl());
        out.put("last_full_sync", jdbc.query("SELECT completed_at, status FROM sync_runs WHERE clinic_id = ? "
                        + "AND sync_type LIKE 'full:%' AND completed_at IS NOT NULL ORDER BY completed_at DESC LIMIT 1",
                (rs, i) -> Map.of("at", String.valueOf(rs.getTimestamp("completed_at")), "status", rs.getString("status")),
                clinicId).stream().findFirst().orElse(null));
        return out;
    }

    private Object searchRecords(UUID clinicId, JsonNode in, Set<Permission> perms) {
        String resource = text(in, "resource").trim().toLowerCase();
        if (OdResourceCatalog.find(resource) == null) {
            return Map.of("error", "Unknown record type '" + resource + "'.");
        }
        if (!permissionResolver.allowed(perms, resource, false)) {
            return Map.of("error", "Your role doesn't allow reading " + resource + ".");
        }
        int limit = Math.max(1, Math.min(in.path("limit").asInt(25), MAX_ROWS));
        return records.list(clinicId, resource, longValue(in, "pat_num"), limit, 0);
    }

    // ------------------------------------------------------------------ helpers

    private UUID clinicId() {
        return clinicRepository.findByIsActiveTrue().stream().findFirst().map(Clinic::getId)
                .orElseThrow(() -> new IllegalStateException("No active clinic configured"));
    }

    /** Removes fields that must never leave our systems (SSNs, keys), at any depth. */
    static JsonNode scrub(JsonNode node) {
        if (node instanceof ObjectNode object) {
            List<String> drop = new ArrayList<>();
            for (Iterator<String> it = object.fieldNames(); it.hasNext(); ) {
                String field = it.next();
                if (SENSITIVE_FIELD.matcher(field).matches()) drop.add(field);
                else scrub(object.get(field));
            }
            object.remove(drop);
        } else if (node instanceof ArrayNode array) {
            array.forEach(AssistantTools::scrub);
        }
        return node;
    }

    private static String claimStatus(String code) {
        return switch (code == null ? "" : code) {
            case "U" -> "Unsent";
            case "H" -> "On hold";
            case "W" -> "Waiting to send";
            case "P" -> "Probably sent";
            case "S" -> "Sent";
            default -> code;
        };
    }

    private static LocalDate date(JsonNode in, String field, LocalDate fallback) {
        String value = in.path(field).asText("");
        if (value.isBlank()) return fallback;
        try {
            return LocalDate.parse(value.trim().substring(0, Math.min(10, value.trim().length())));
        } catch (Exception e) {
            throw new IllegalArgumentException(field + " must be a date like 2026-10-03");
        }
    }

    private static Long longValue(JsonNode in, String field) {
        JsonNode node = in.path(field);
        if (node.isMissingNode() || node.isNull() || node.asText().isBlank()) return null;
        if (node.canConvertToLong()) return node.asLong();
        String text = node.asText().trim();
        return text.matches("-?\\d+") ? Long.valueOf(text) : null;
    }

    private static String text(JsonNode in, String field) {
        return in.path(field).asText("");
    }

    private static String name(String preferred, String first, String last) {
        String given = firstNonBlank(preferred, first);
        return ((given == null ? "" : given) + " " + (last == null ? "" : last)).trim();
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }

    private static List<String> nonBlank(String... values) {
        List<String> out = new ArrayList<>();
        for (String value : values) if (value != null && !value.isBlank()) out.add(value);
        return out;
    }

    private static String truncate(String text, int max) {
        return text.length() > max ? text.substring(0, max) + "…" : text;
    }
}
