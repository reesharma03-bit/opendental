package com.clinic.opendental.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Narrow, allowlisted adapter for the documented Clinical Care API routes that do
 * not have a dedicated typed controller. It forwards Open Dental JSON field names
 * unchanged and deliberately exposes no arbitrary proxy operation.
 *
 * Resources covered here (see https://www.opendental.com/site/apispecification.html):
 * AutoNoteControls, AutoNotes, ChartModules, CodeGroups, PerioExams, PerioMeasures,
 * ProcedureCodes, ProcNotes, ProcTPs, ToothInitials, TreatPlanAttaches and TreatPlans.
 *
 * ProcedureLogs already have a dedicated typed controller at /api/procedurelogs,
 * so it is intentionally absent from this group.
 *
 * The literal paths below are more specific than the {@code /api/{resource}}
 * pattern used by {@link PatientFamilyResourcesController}, so Spring routes
 * each request to the correct controller without an ambiguous mapping.
 *
 * A single {@code dispatch} method handles every covered route for this group.
 */
@RestController
@RequestMapping({
        "/api/autonotecontrols", "/api/autonotecontrols/**",
        "/api/autonotes", "/api/autonotes/**",
        "/api/chartmodules", "/api/chartmodules/**",
        "/api/codegroups", "/api/codegroups/**",
        "/api/perioexams", "/api/perioexams/**",
        "/api/periomeasures", "/api/periomeasures/**",
        "/api/procedurecodes", "/api/procedurecodes/**",
        "/api/procnotes", "/api/procnotes/**",
        "/api/proctps", "/api/proctps/**",
        "/api/toothinitials", "/api/toothinitials/**",
        "/api/treatplanattaches", "/api/treatplanattaches/**",
        "/api/treatplans", "/api/treatplans/**"})
public class ClinicalCareResourcesController {

    private static final Set<String> RESOURCES = Set.of(
            "autonotecontrols", "autonotes", "chartmodules", "codegroups",
            "perioexams", "periomeasures", "procedurecodes", "procnotes",
            "proctps", "toothinitials", "treatplanattaches", "treatplans");

    private static final Set<String> CHART_MODULES =
            Set.of("ProgNotes", "PatientInfo", "PlannedAppts");

    /** Methods accepted on the bare collection route for each resource. */
    private static final Map<String, Set<String>> COLLECTION_METHODS = Map.ofEntries(
            Map.entry("autonotecontrols", Set.of("GET", "POST")),
            Map.entry("autonotes", Set.of("GET", "POST")),
            Map.entry("chartmodules", Set.of()),
            Map.entry("codegroups", Set.of("GET", "POST")),
            Map.entry("perioexams", Set.of("GET", "POST")),
            Map.entry("periomeasures", Set.of("GET", "POST")),
            Map.entry("procedurecodes", Set.of("GET", "POST")),
            Map.entry("procnotes", Set.of("GET", "POST")),
            Map.entry("proctps", Set.of("GET")),
            Map.entry("toothinitials", Set.of("GET", "POST")),
            Map.entry("treatplanattaches", Set.of("GET", "POST")),
            Map.entry("treatplans", Set.of("GET", "POST")));

    /** Methods accepted on the /{id} route for each resource. */
    private static final Map<String, Set<String>> ID_METHODS = Map.ofEntries(
            Map.entry("autonotecontrols", Set.of("PUT")),
            Map.entry("autonotes", Set.of("PUT")),
            Map.entry("chartmodules", Set.of()),
            Map.entry("codegroups", Set.of("GET", "PUT", "DELETE")),
            Map.entry("perioexams", Set.of("GET", "PUT", "DELETE")),
            Map.entry("periomeasures", Set.of("PUT", "DELETE")),
            Map.entry("procedurecodes", Set.of("GET", "PUT")),
            Map.entry("procnotes", Set.of()),
            Map.entry("proctps", Set.of("PUT", "DELETE")),
            Map.entry("toothinitials", Set.of("DELETE")),
            Map.entry("treatplanattaches", Set.of("PUT")),
            Map.entry("treatplans", Set.of("PUT", "DELETE")));

    // ANCHOR-TABLES
private static final Map<String, Set<String>> QUERY_FIELDS = Map.ofEntries(
            Map.entry("autonotecontrols", Set.of()),
            Map.entry("autonotes", Set.of("Category")),
            Map.entry("chartmodules", Set.of("Offset")),
            Map.entry("codegroups", Set.of("IsHidden", "ShowInAgeLimit", "ShowInFrequency",
                    "ShowInOther", "ShowInHistory")),
            Map.entry("perioexams", Set.of("PatNum", "ExamDate")),
            Map.entry("periomeasures", Set.of("PerioExamNum")),
            Map.entry("procedurecodes", Set.of("DateTStamp")),
            Map.entry("procnotes", Set.of("PatNum", "ProcNum")),
            Map.entry("proctps", Set.of("TreatPlanNum")),
            Map.entry("toothinitials", Set.of("PatNum")),
            Map.entry("treatplanattaches", Set.of("TreatPlanNum")),
            Map.entry("treatplans", Set.of("PatNum", "SecDateTEdit", "TPStatus")));

    /** Query parameters Open Dental documents as mandatory. */
    private static final Map<String, Set<String>> REQUIRED_QUERY = Map.of(
            "proctps", Set.of("TreatPlanNum"),
            "treatplanattaches", Set.of("TreatPlanNum"));

    private static final Set<String> CONTROL_TYPES = Set.of("Text", "OneResponse", "MultiResponse");
    private static final Set<String> SEQUENCE_TYPES = Set.of("Mobility", "Furcation",
            "GingMargin", "MGJ", "Probing", "SkipTooth", "BleedSupPlaqCalc");
    private static final Set<String> INITIAL_TYPES = Set.of("Missing", "Hidden", "Primary",
            "ShiftM", "ShiftO", "ShiftB", "Rotate", "TipM", "TipB");
    private static final Set<String> MOVEMENT_TYPES = Set.of("ShiftM", "ShiftO", "ShiftB",
            "Rotate", "TipM", "TipB");
    private static final Set<String> CODE_GROUP_FIXED = Set.of("None", "BW", "PanoFMX", "Exam",
            "Perio", "Prophy", "SRP", "FMDebride", "Fluoride", "Sealant");
    private static final Set<String> TREAT_AREAS = Set.of("None", "Surf", "Tooth", "Mouth",
            "Quad", "Sextant", "Arch", "ToothRange");
    private static final Set<String> SUBST_ONLY_IF = Set.of("Always", "Molar", "SecondMolar",
            "Never", "Posterior");
    private static final Set<String> TP_TYPES = Set.of("Insurance", "Discount");
    private static final Set<String> TP_STATUSES = Set.of("Saved", "Active", "Inactive");
    private static final Set<String> PROC_STATUSES = Set.of("TP", "C", "EC", "EO", "R", "D",
            "Cn", "TPi");

    private static final List<String> NUMERIC_BODY_FIELDS = List.of("Category", "PatNum",
            "CodeNum", "AptNum", "ProvNum", "ClinicNum", "Priority", "Dx", "ItemOrder",
            "PerioExamNum", "PerioMeasureNum", "TreatPlanNum", "ProcNum", "CodeGroupNum",
            "AutoNoteNum", "ToothInitialNum", "ProcTPNum", "TreatPlanAttachNum",
            "PlannedAptNum", "SiteNum", "Prognosis", "SecUserNumEntry", "UserNum",
            "BaseUnits", "CanadaTimeUnits", "IntTooth");

    private static final List<String> BOOLEAN_BODY_FIELDS = List.of("IsHidden",
            "ShowInAgeLimit", "ShowInFrequency", "ShowInOther", "ShowInHistory",
            "IsPerioFourQuads", "isSigned", "isSignedPractice", "doAppendNote",
            "clearAll", "NoBillIns", "IsProsth", "IsHygiene", "IsTaxed", "IsCanadianLab",
            "IsRadiology", "AreaAlsoToothRange");

    private static final List<String> DATE_BODY_FIELDS = List.of("ExamDate", "ProcDate",
            "DateTP", "DateEntryC", "SecDateEntry", "DateOriginalProsth");

    private static final List<String> DATE_TIME_BODY_FIELDS = List.of("DateTStamp",
            "SecDateTEdit", "EntryDateTime", "DateTSigned", "DateTPracticeSigned");

    @Value("${opendental.base-url}")
    private String baseUrl;
    private final RestTemplate restTemplate;

    public ClinicalCareResourcesController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @RequestMapping
    public ResponseEntity<?> dispatch(HttpServletRequest request,
                                      @RequestBody(required = false) Map<String, Object> body) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        if (!uri.startsWith("/api/")) return error(HttpStatus.NOT_FOUND, "Resource is not available.");

        String rest = uri.substring("/api/".length());
        int slash = rest.indexOf('/');
        String name = (slash < 0 ? rest : rest.substring(0, slash)).toLowerCase(Locale.ROOT);
        if (!RESOURCES.contains(name)) return error(HttpStatus.NOT_FOUND, "Resource is not available.");

        String tail = slash < 0 ? "" : rest.substring(slash + 1);
        while (tail.endsWith("/")) tail = tail.substring(0, tail.length() - 1);
        String route = tail.isEmpty() ? "collection" : tail;
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        Route resolved = resolve(name, route, method);
        if (resolved == null) {
            if (isKnownRoute(name, route)) {
                return error(HttpStatus.METHOD_NOT_ALLOWED, "Method is not supported for this route.");
            }
            return error(HttpStatus.NOT_FOUND, "Route is not documented for this resource.");
        }
        if (resolved.id != null && !positive(resolved.id)) {
            return error(HttpStatus.BAD_REQUEST, "Resource ID must be a positive integer.");
        }

        Map<String, String> params = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values.length > 0) params.put(key, values[0]);
        });
        String validation = validateQuery(name, resolved, params);
        if (validation != null) return error(HttpStatus.BAD_REQUEST, validation);
        validation = validateBody(name, resolved, body);
        if (validation != null) return error(HttpStatus.BAD_REQUEST, validation);

        UriComponentsBuilder target = UriComponentsBuilder.fromHttpUrl(
                baseUrl.replaceAll("/+$", "") + resolved.upstreamPath);
        params.forEach(target::queryParam);
        HttpHeaders headers = new HttpHeaders();
        if (body != null) headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            ResponseEntity<Object> upstream = restTemplate.exchange(
                    target.build().encode().toUri(),
                    method,
                    new HttpEntity<>(body, headers),
                    Object.class);
            ResponseEntity.BodyBuilder response = ResponseEntity.status(upstream.getStatusCode());
            return upstream.getBody() == null ? response.build() : response.body(upstream.getBody());
        } catch (HttpStatusCodeException ex) {
            ResponseEntity.BodyBuilder response = ResponseEntity.status(ex.getStatusCode());
            MediaType contentType = ex.getResponseHeaders() == null
                    ? null : ex.getResponseHeaders().getContentType();
            if (contentType != null) response.contentType(contentType);
            byte[] raw = ex.getResponseBodyAsByteArray();
            if (raw.length > 0) {
                return response.body(new String(raw, java.nio.charset.StandardCharsets.UTF_8));
            }
            return response.build();
        } catch (ResourceAccessException ex) {
            return error(HttpStatus.BAD_GATEWAY, "Open Dental connectivity failure.");
        }
    }

    private Route resolve(String resource, String route, HttpMethod method) {
        if (route.equals("collection")) {
            if (!COLLECTION_METHODS.get(resource).contains(method.name())) return null;
            return new Route("collection", "/" + resource, null, method.name());
        }
        if (route.matches("[0-9]+")) {
            if (!ID_METHODS.get(resource).contains(method.name())) return null;
            return new Route("id", "/" + resource + "/" + route, route, method.name());
        }
        if (resource.equals("toothinitials") && route.equalsIgnoreCase("ClearMovements")
                && method == HttpMethod.PUT) {
            return new Route("action", "/" + resource + "/ClearMovements", null, method.name());
        }
        int sep = route.indexOf('/');
        if (resource.equals("chartmodules") && sep > 0 && method == HttpMethod.GET) {
            String patNum = route.substring(0, sep);
            String chartModule = route.substring(sep + 1);
            if (patNum.matches("[0-9]+") && CHART_MODULES.contains(chartModule)) {
                return new Route("chartmodule", "/" + resource + "/" + patNum + "/" + chartModule,
                        patNum, method.name());
            }
        }
        return null;
    }

    private boolean isKnownRoute(String resource, String route) {
        if (route.equals("collection") || route.matches("[0-9]+")) return true;
        if (resource.equals("toothinitials") && route.equalsIgnoreCase("ClearMovements")) return true;
        int sep = route.indexOf('/');
        return resource.equals("chartmodules") && sep > 0
                && route.substring(0, sep).matches("[0-9]+")
                && CHART_MODULES.contains(route.substring(sep + 1));
    }

    private String validateQuery(String resource, Route route, Map<String, String> params) {
        Set<String> allowed = QUERY_FIELDS.get(resource);
        for (String key : params.keySet()) {
            if (!allowed.contains(key)) return "Unsupported query parameter: " + key;
            if (!route.kind.equals("collection") && !route.kind.equals("chartmodule")) {
                return "Query parameters are not supported for this route.";
            }
        }
        if (route.kind.equals("collection")) {
            for (String key : REQUIRED_QUERY.getOrDefault(resource, Set.of())) {
                if (!params.containsKey(key)) return key + " is required.";
            }
        }
        for (String key : List.of("PatNum", "ProcNum", "PerioExamNum", "TreatPlanNum",
                "Category", "Offset")) {
            if (params.containsKey(key) && !nonNegative(params.get(key))) {
                return key + " must be a non-negative integer.";
            }
        }
        if (params.containsKey("ExamDate") && !validDate(params.get("ExamDate"))) {
            return "ExamDate must use yyyy-MM-dd format.";
        }
        if (params.containsKey("DateTStamp") && !validDateTime(params.get("DateTStamp"))) {
            return "DateTStamp must use yyyy-MM-dd HH:mm:ss format.";
        }
        if (params.containsKey("SecDateTEdit") && !validDateTime(params.get("SecDateTEdit"))) {
            return "SecDateTEdit must use yyyy-MM-dd HH:mm:ss format.";
        }
        if (params.containsKey("TPStatus") && !TP_STATUSES.contains(params.get("TPStatus"))) {
            return "TPStatus must be Saved, Active, or Inactive.";
        }
        for (String key : List.of("IsHidden", "ShowInAgeLimit", "ShowInFrequency",
                "ShowInOther", "ShowInHistory")) {
            if (params.containsKey(key) && !bool(params.get(key))) {
                return key + " must be true or false.";
            }
        }
        return null;
    }

    private String validateBody(String resource, Route route, Map<String, Object> body) {
        boolean write = (route.kind.equals("id") && route.method.equals("PUT"))
                || (route.kind.equals("collection") && route.method.equals("POST"))
                || (route.kind.equals("action") && route.method.equals("PUT"));
        if (write && (body == null || body.isEmpty())) {
            return "A JSON request body is required.";
        }
        if (body == null) return null;

        if (route.kind.equals("collection") && route.method.equals("POST")) {
            String required = requiredOnCreate(resource, body);
            if (required != null) return required;
        }
        if (body.containsKey("ControlType") && !CONTROL_TYPES.contains(body.get("ControlType"))) {
            return "ControlType must be Text, OneResponse, or MultiResponse.";
        }
        if (body.containsKey("SequenceType") && !SEQUENCE_TYPES.contains(body.get("SequenceType"))) {
            return "SequenceType must be Mobility, Furcation, GingMargin, MGJ, Probing, "
                    + "SkipTooth, or BleedSupPlaqCalc.";
        }
        if (body.containsKey("InitialType") && !INITIAL_TYPES.contains(body.get("InitialType"))) {
            return "InitialType must be Missing, Hidden, Primary, ShiftM, ShiftO, ShiftB, "
                    + "Rotate, TipM, or TipB.";
        }
        if (resource.equals("toothinitials") && body.containsKey("InitialType")
                && MOVEMENT_TYPES.contains(body.get("InitialType"))
                && (body.get("Movement") == null
                || blank(String.valueOf(body.get("Movement"))))) {
            return "Movement is required when InitialType is " + body.get("InitialType") + ".";
        }
        if (body.containsKey("CodeGroupFixed")
                && !CODE_GROUP_FIXED.contains(body.get("CodeGroupFixed"))) {
            return "CodeGroupFixed must be one of " + String.join(", ", CODE_GROUP_FIXED) + ".";
        }
        if (body.containsKey("TreatArea") && !TREAT_AREAS.contains(body.get("TreatArea"))) {
            return "TreatArea must be one of " + String.join(", ", TREAT_AREAS) + ".";
        }
        if (body.containsKey("SubstOnlyIf") && !SUBST_ONLY_IF.contains(body.get("SubstOnlyIf"))) {
            return "SubstOnlyIf must be Always, Molar, SecondMolar, Never, or Posterior.";
        }
        if (body.containsKey("TPType") && !TP_TYPES.contains(body.get("TPType"))) {
            return "TPType must be Insurance or Discount.";
        }
        if (body.containsKey("TPStatus") && !TP_STATUSES.contains(body.get("TPStatus"))) {
            return "TPStatus must be Saved, Active, or Inactive.";
        }
        if (body.containsKey("ProcStatus") && !PROC_STATUSES.contains(body.get("ProcStatus"))) {
            return "ProcStatus must be one of " + String.join(", ", PROC_STATUSES) + ".";
        }
        if ("true".equalsIgnoreCase(String.valueOf(body.get("ShowInHistory")))
                && (body.get("HistProcCode") == null
                || blank(String.valueOf(body.get("HistProcCode"))))) {
            return "HistProcCode is required when ShowInHistory is true.";
        }
        if (body.containsKey("AreaAlsoToothRange") && body.containsKey("TreatArea")
                && !Set.of("Quad", "Arch").contains(body.get("TreatArea"))) {
            return "AreaAlsoToothRange requires TreatArea to be Quad or Arch.";
        }
        if (body.containsKey("IntTooth") && !toothNumber(body.get("IntTooth"))) {
            return "IntTooth must be an integer between 1 and 32.";
        }
        if (body.containsKey("ToothNum") && !toothNumber(body.get("ToothNum"))) {
            return "ToothNum must be 1-32 or A-T.";
        }
        for (String key : NUMERIC_BODY_FIELDS) {
            if (body.containsKey(key) && !nonNegative(String.valueOf(body.get(key)))) {
                return key + " must be a non-negative integer.";
            }
        }
        for (String key : BOOLEAN_BODY_FIELDS) {
            if (body.containsKey(key) && !bool(String.valueOf(body.get(key)))) {
                return key + " must be true or false.";
            }
        }
        for (String key : DATE_BODY_FIELDS) {
            if (body.containsKey(key) && !validDate(String.valueOf(body.get(key)))) {
                return key + " must use yyyy-MM-dd format.";
            }
        }
        for (String key : DATE_TIME_BODY_FIELDS) {
            if (body.containsKey(key) && !validDateTime(String.valueOf(body.get(key)))) {
                return key + " must use yyyy-MM-dd HH:mm:ss format.";
            }
        }
        return null;
    }

    private String requiredOnCreate(String resource, Map<String, Object> body) {
        switch (resource) {
            case "autonotecontrols" -> {
                String error = requireText(body, "Descript", "ControlLabel");
                if (error != null) return error;
                if (!body.containsKey("ControlType")) return "ControlType is required.";
                if (!"Text".equals(body.get("ControlType"))
                        && (body.get("ControlOptions") == null
                        || blank(String.valueOf(body.get("ControlOptions"))))) {
                    return "ControlOptions is required unless ControlType is Text.";
                }
            }
            case "autonotes" -> {
                String error = requireText(body, "AutoNoteName", "MainText");
                if (error != null) return error;
            }
            case "codegroups" -> {
                String error = requireText(body, "GroupName");
                if (error != null) return error;
            }
            case "perioexams", "toothinitials", "treatplans" -> {
                if (!body.containsKey("PatNum")) return "PatNum is required.";
            }
            case "periomeasures" -> {
                if (!body.containsKey("PerioExamNum")) return "PerioExamNum is required.";
                if (!body.containsKey("SequenceType")) return "SequenceType is required.";
                if (!body.containsKey("IntTooth")) return "IntTooth is required.";
            }
            case "procedurecodes" -> {
                String error = requireText(body, "ProcCode", "Descript", "AbbrDesc");
                if (error != null) return error;
                if (!body.containsKey("ProcCat") && !body.containsKey("procCat")) {
                    return "ProcCat or procCat is required.";
                }
            }
            case "procnotes" -> {
                if (!body.containsKey("PatNum")) return "PatNum is required.";
                if (!body.containsKey("ProcNum")) return "ProcNum is required.";
                String error = requireText(body, "Note");
                if (error != null) return error;
            }
            case "treatplanattaches" -> {
                if (!body.containsKey("TreatPlanNum")) return "TreatPlanNum is required.";
                if (!body.containsKey("ProcNum")) return "ProcNum is required.";
            }
            default -> {
                return null;
            }
        }
        return null;
    }

    private String requireText(Map<String, Object> body, String... fields) {
        for (String field : fields) {
            Object value = body.get(field);
            if (value == null || blank(String.valueOf(value))) return field + " is required.";
        }
        return null;
    }

    private boolean toothNumber(Object value) {
        String text = String.valueOf(value);
        if (text.matches("[1-9]|[12][0-9]|3[0-2]")) return true;
        return text.matches("(?i)^[A-T]$");
    }

    private boolean positive(Object value) {
        if (value == null) return false;
        try { return Long.parseLong(value.toString()) > 0; }
        catch (NumberFormatException ignored) { return false; }
    }

    private boolean nonNegative(String value) {
        try { return Long.parseLong(value) >= 0; }
        catch (Exception ignored) { return false; }
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
    private boolean bool(String value) { return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value); }

    private boolean validDate(String value) {
        try { LocalDate.parse(value); return value.matches("\\d{4}-\\d{2}-\\d{2}"); }
        catch (DateTimeParseException ignored) { return false; }
    }

    private boolean validDateTime(String value) {
        try {
            LocalDateTime.parse(value.replace(' ', 'T'));
            return value.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
        } catch (DateTimeParseException ignored) { return false; }
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }

    private record Route(String kind, String upstreamPath, String id, String method) {}
}