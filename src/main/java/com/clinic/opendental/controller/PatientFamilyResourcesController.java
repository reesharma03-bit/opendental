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
import java.util.*;

/**
 * Narrow, allowlisted adapter for the documented Patients & Families API routes
 * that do not have dedicated typed controllers. It forwards Open Dental JSON
 * field names unchanged and deliberately exposes no arbitrary proxy operation.
 */
@RestController
@RequestMapping({"/api/{resource}", "/api/{resource}/**"})
public class PatientFamilyResourcesController {
    private static final Set<String> RESOURCES = Set.of(
            "allergydefs", "diseasedefs", "diseases", "ehrpatients", "familymodules",
            "guardians", "medicationpats", "medications", "patientnotes", "patientraces",
            "patfielddefs", "patfields", "patplans", "patrestrictions", "pharmacies",
            "popups", "recalls", "recalltypes", "rxpats", "vitalsigns");

    private static final Map<String, Set<String>> QUERY_FIELDS = Map.ofEntries(
            Map.entry("allergydefs", Set.of("Offset")),
            Map.entry("diseasedefs", Set.of("Offset")),
            Map.entry("diseases", Set.of("PatNum", "Offset")),
            Map.entry("ehrpatients", Set.of()),
            Map.entry("familymodules", Set.of()),
            Map.entry("guardians", Set.of("PatNumChild", "PatNumGuardian", "Offset")),
            Map.entry("medicationpats", Set.of("PatNum", "includeDiscontinued", "Offset")),
            Map.entry("medications", Set.of("Offset")),
            Map.entry("patientnotes", Set.of()),
            Map.entry("patientraces", Set.of("PatNum")),
            Map.entry("patfielddefs", Set.of("Offset")),
            Map.entry("patfields", Set.of("PatNum", "FieldName", "SecDateTEdit", "Offset")),
            Map.entry("patplans", Set.of("PatNum", "InsSubNum", "Offset")),
            Map.entry("patrestrictions", Set.of("PatNum", "Offset")),
            Map.entry("pharmacies", Set.of("Offset")),
            Map.entry("popups", Set.of("PatNum")),
            Map.entry("recalls", Set.of("PatNum", "DateStart", "DateEnd", "ProvNum",
                    "ClinicNum", "RecallType", "IncludeReminded", "Offset")),
            Map.entry("recalltypes", Set.of("Offset")),
            Map.entry("rxpats", Set.of("PatNum", "Offset")),
            Map.entry("vitalsigns", Set.of("PatNum", "Offset")));

    @Value("${opendental.base-url}")
    private String baseUrl;
    private final RestTemplate restTemplate;

    public PatientFamilyResourcesController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @RequestMapping
    public ResponseEntity<?> dispatch(HttpServletRequest request,
                                      @org.springframework.web.bind.annotation.PathVariable String resource,
                                      @RequestBody(required = false) Map<String, Object> body) {
        String name = resource.toLowerCase(Locale.ROOT);
        if (!RESOURCES.contains(name)) return error(HttpStatus.NOT_FOUND, "Resource is not available.");

        String basePath = "/api/" + resource;
        String uri = request.getRequestURI();
        String tail = uri.length() > basePath.length() ? uri.substring(basePath.length()) : "";
        if (tail.startsWith("/")) tail = tail.substring(1);
        String route = tail.isEmpty() ? "collection" : tail;
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        Route resolved = resolve(name, route, method);
        if (resolved == null) {
            if (isKnownRoute(name, route)) return error(HttpStatus.METHOD_NOT_ALLOWED, "Method is not supported for this route.");
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
            String responseBody = ex.getResponseBodyAsString();
            return responseBody.isEmpty() ? response.build() : response.body(responseBody);
        } catch (ResourceAccessException ex) {
            return error(HttpStatus.BAD_GATEWAY, "Open Dental connectivity failure.");
        }
    }

    private Route resolve(String resource, String route, HttpMethod method) {
        String kind;
        String upstreamPath;
        String id = null;
        if (resource.equals("familymodules") && route.matches("[0-9]+/Insurance")) {
            id = route.substring(0, route.indexOf('/'));
            kind = "insurance";
            upstreamPath = "/" + resource + "/" + id + "/Insurance";
        } else if (resource.equals("recalls") && Set.of("List", "Status", "SwitchType").contains(route)) {
            kind = route.toLowerCase(Locale.ROOT);
            upstreamPath = "/" + resource + "/" + route;
        } else if (route.equals("collection")) {
            kind = "collection";
            upstreamPath = "/" + resource;
        } else if (route.matches("[0-9]+")) {
            id = route;
            kind = "id";
            upstreamPath = "/" + resource + "/" + id;
        } else return null;

        boolean supported = switch (resource) {
            case "allergydefs" -> supports(kind, method, "collection", "GET", "POST", "id", "GET", "PUT");
            case "diseasedefs" -> supports(kind, method, "collection", "GET", "POST", "id", "GET");
            case "diseases" -> supports(kind, method, "collection", "GET", "POST", "id", "GET", "PUT", "DELETE");
            case "ehrpatients" -> supports(kind, method, "id", "GET", "PUT");
            case "familymodules" -> supports(kind, method, "insurance", "GET");
            case "guardians" -> supports(kind, method, "collection", "GET", "POST", "id", "GET", "PUT", "DELETE");
            case "medicationpats" -> supports(kind, method, "collection", "GET", "POST", "id", "GET", "PUT", "DELETE");
            case "medications" -> supports(kind, method, "collection", "GET", "POST", "id", "GET", "PUT", "DELETE");
            case "patientnotes" -> supports(kind, method, "collection", "GET", "id", "GET", "PUT");
            case "patientraces" -> supports(kind, method, "collection", "GET");
            case "patfielddefs" -> supports(kind, method, "collection", "GET", "POST", "id", "PUT", "DELETE");
            case "patfields" -> supports(kind, method, "collection", "GET", "POST", "PUT", "id", "GET", "DELETE");
            case "patplans" -> supports(kind, method, "collection", "GET", "POST", "id", "PUT", "DELETE");
            case "patrestrictions" -> supports(kind, method, "collection", "GET", "POST", "id", "GET", "DELETE");
            case "pharmacies" -> supports(kind, method, "collection", "GET", "id", "GET");
            case "popups" -> supports(kind, method, "collection", "GET", "POST", "id", "PUT");
            case "recalls" -> supports(kind, method, "collection", "GET", "POST", "id", "PUT",
                    "list", "GET", "status", "PUT", "switchtype", "PUT");
            case "recalltypes" -> supports(kind, method, "collection", "GET", "id", "GET");
            case "rxpats" -> supports(kind, method, "collection", "GET", "id", "GET");
            case "vitalsigns" -> supports(kind, method, "collection", "GET", "POST", "id", "GET", "PUT", "DELETE");
            default -> false;
        };
        return supported ? new Route(kind, upstreamPath, id, method.name()) : null;
    }

    private boolean isKnownRoute(String resource, String route) {
        if (route.equals("collection") || route.matches("[0-9]+")) return true;
        if (resource.equals("familymodules") && route.matches("[0-9]+/Insurance")) return true;
        return resource.equals("recalls") && Set.of("List", "Status", "SwitchType").contains(route);
    }

    private boolean supports(String kind, HttpMethod method, Object... args) {
        Set<String> routeKinds = Set.of("collection", "id", "insurance", "list", "status", "switchtype");
        for (int i = 0; i < args.length;) {
            String candidate = (String) args[i++];
            while (i < args.length && !routeKinds.contains(args[i])) {
                if (candidate.equals(kind) && method.name().equals(args[i])) return true;
                i++;
            }
        }
        return false;
    }

    private String validateQuery(String resource, Route route, Map<String, String> params) {
        Set<String> allowed = QUERY_FIELDS.get(resource);
        for (String key : params.keySet()) {
            if (!allowed.contains(key) || (route.kind.equals("insurance") && !key.equals("Offset"))) {
                return "Unsupported query parameter: " + key;
            }
            if (route.kind.equals("status") || route.kind.equals("switchtype") || route.kind.equals("id")
                    || route.kind.equals("insurance")) {
                return "Query parameters are not supported for this route.";
            }
            if (route.kind.equals("list") && !resource.equals("recalls")) {
                return "Query parameter is not supported for this route.";
            }
            if (resource.equals("recalls") && route.kind.equals("collection")
                    && !Set.of("PatNum", "Offset").contains(key)) {
                return key + " is only supported on /recalls/List.";
            }
            if (resource.equals("recalls") && route.kind.equals("list") && key.equals("PatNum")) {
                return "PatNum is only supported on /recalls.";
            }
        }
        boolean requiredPatient = resource.equals("patientraces");
        if (requiredPatient && !positive(params.get("PatNum"))) return "PatNum query parameter must be a positive integer.";
        if (resource.equals("popups") && route.kind.equals("collection") && route.method.equals("GET")
                && !positive(params.get("PatNum"))) {
            return "PatNum query parameter is required and must be a positive integer.";
        }
        for (String key : List.of("PatNum", "PatNumChild", "PatNumGuardian", "InsSubNum", "ProvNum",
                "RecallTypeNum")) {
            if (params.containsKey(key) && !positive(params.get(key))) return key + " must be a positive integer.";
        }
        if (params.containsKey("ClinicNum") && !nonNegative(params.get("ClinicNum"))) return "ClinicNum must be a non-negative integer.";
        if (params.containsKey("Offset") && !nonNegative(params.get("Offset"))) return "Offset must be a non-negative integer.";
        for (String key : List.of("IncludeReminded", "includeDiscontinued")) {
            if (params.containsKey(key) && !bool(params.get(key))) return key + " must be true or false.";
        }
        for (String key : List.of("DateStart", "DateEnd")) {
            if (params.containsKey(key) && !validDate(params.get(key))) return key + " must use yyyy-MM-dd format.";
        }
        if (params.containsKey("SecDateTEdit") && !validDateTime(params.get("SecDateTEdit"))) {
            return "SecDateTEdit must use yyyy-MM-dd HH:mm:ss format.";
        }
        return null;
    }

    private String validateBody(String resource, Route route, Map<String, Object> body) {
        if (route.kind.equals("collection") && route.method.equals("POST")) {
            if (body == null) return "A JSON request body is required.";
        }
        if ((route.kind.equals("id") && route.method.equals("PUT"))
                || route.kind.equals("status") || route.kind.equals("switchtype")
                || resource.equals("patfields") && route.kind.equals("collection") && route.method.equals("PUT")) {
            if (body == null) return "A JSON request body is required.";
        }
        if (body == null) return null;
        Set<String> required = Set.of();
        if (route.kind.equals("collection") && route.method.equals("POST")) {
            required = switch (resource) {
                case "allergydefs" -> Set.of("Description");
                case "diseasedefs" -> Set.of("DiseaseName");
                case "diseases" -> Set.of("PatNum");
                case "guardians" -> Set.of("PatNumChild", "PatNumGuardian", "Relationship");
                case "medicationpats" -> Set.of("PatNum");
                case "medications" -> Set.of("MedName");
                case "patfielddefs" -> Set.of("FieldName", "FieldType");
                case "patfields" -> Set.of("PatNum", "FieldName", "FieldValue");
                case "patplans" -> Set.of("PatNum", "InsSubNum");
                case "patrestrictions" -> Set.of("PatNum", "PatRestrictType");
                case "popups" -> Set.of("PatNum", "Description");
                case "recalls" -> Set.of("PatNum", "RecallTypeNum");
                case "vitalsigns" -> Set.of("PatNum");
                default -> Set.of();
            };
        } else if (resource.equals("patfields") && route.kind.equals("collection")
                && route.method.equals("PUT")) {
            required = Set.of("PatNum", "FieldName", "FieldValue");
        } else if (route.kind.equals("status")) {
            required = Set.of("PatNum", "recallType");
        } else if (route.kind.equals("switchtype")) {
            required = Set.of("PatNum");
        }
        for (String key : required) {
            if (!body.containsKey(key) || body.get(key) == null
                    || (!key.equals("FieldValue") && body.get(key).toString().isBlank())) {
                return key + " is required.";
            }
        }
        if (resource.equals("diseases") && route.kind.equals("collection") && route.method.equals("POST")
                && !body.containsKey("diseaseDefName") && !body.containsKey("DiseaseDefNum")) {
            return "Provide diseaseDefName or DiseaseDefNum.";
        }
        if (resource.equals("patfielddefs") && "PickList".equals(body.get("FieldType"))
                && (body.get("PickList") == null || body.get("PickList").toString().isBlank())) {
            return "PickList is required when FieldType is PickList.";
        }
        if (resource.equals("patfielddefs") && body.containsKey("PickList")
                && !"PickList".equals(body.get("FieldType"))) {
            return "PickList is only supported when changing FieldType to PickList.";
        }
        String bad = validateBodyValues(resource, body);
        if (bad != null) return bad;
        return null;
    }

    private String validateBodyValues(String resource, Map<String, Object> body) {
        for (String key : List.of("PatNum", "PatNumChild", "PatNumGuardian", "DiseaseDefNum",
                "MedicationNum", "InsSubNum", "RecallTypeNum")) {
            if (body.containsKey(key) && !positive(body.get(key))) return key + " must be a positive integer.";
        }
        if (body.containsKey("ProvNum") && !nonNegative(String.valueOf(body.get("ProvNum")))) {
            return "ProvNum must be a non-negative integer.";
        }
        for (String key : List.of("IsHidden", "IsGuardian", "IsDisabled", "StatusIsActive")) {
            if (body.containsKey(key) && !bool(String.valueOf(body.get(key)))) return key + " must be true or false.";
        }
        for (String key : List.of("DateStart", "DateStop", "DateDue", "DisableUntilDate", "DateTaken", "DischargeDate")) {
            if (body.containsKey(key) && !validDate(String.valueOf(body.get(key)))) return key + " must use yyyy-MM-dd format.";
        }
        if (body.containsKey("DateTimeDisabled") && !validDateTime(String.valueOf(body.get("DateTimeDisabled")))) {
            return "DateTimeDisabled must use yyyy-MM-dd HH:mm:ss format.";
        }
        if (body.containsKey("ProbStatus") && !Set.of("Active", "Resolved", "Inactive").contains(body.get("ProbStatus"))) {
            return "ProbStatus must be Active, Resolved, or Inactive.";
        }
        if (resource.equals("patrestrictions") && body.containsKey("PatRestrictType")
                && !"ApptSchedule".equals(body.get("PatRestrictType"))) return "PatRestrictType must be ApptSchedule.";
        if (resource.equals("patfielddefs") && body.containsKey("FieldType")
                && !Set.of("Text", "PickList", "Date", "Checkbox", "Currency", "CareCreditStatus",
                "CareCreditPreApprovalAmt", "CareCreditAvailableCredit").contains(body.get("FieldType"))) {
            return "FieldType is not supported.";
        }
        if (resource.equals("guardians") && body.containsKey("Relationship")
                && !Set.of("Mother", "Stepfather", "Stepmother", "Grandfather", "Grandmother", "Father",
                "Brother", "CareGiver", "FosterChild", "Guardian", "Grandparent", "Other", "Parent",
                "Stepchild", "Self", "Sibling", "Sister", "Spouse", "Child", "LifePartner", "Friend",
                "Grandchild", "Sitter").contains(body.get("Relationship"))) return "Relationship is not supported.";
        if (resource.equals("recalls") && body.containsKey("Priority")
                && !Set.of("Normal", "ASAP").contains(body.get("Priority"))) return "Priority must be Normal or ASAP.";
        if (resource.equals("recalls") && body.containsKey("commlogMode")
                && !Set.of("None", "Email", "Mail", "Phone", "InPerson", "Text", "EmailAndText",
                "PhoneAndText").contains(body.get("commlogMode"))) return "commlogMode is not supported.";
        return null;
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
        try { LocalDateTime.parse(value.replace(' ', 'T')); return value.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"); }
        catch (DateTimeParseException ignored) { return false; }
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }

    private record Route(String kind, String upstreamPath, String id, String method) {}
}