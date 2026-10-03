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
 * Narrow, allowlisted adapter for the documented Scheduling API routes that do
 * not have a dedicated typed controller. It forwards Open Dental JSON field
 * names unchanged and deliberately exposes no arbitrary proxy operation.
 *
 * Resources covered here (see https://www.opendental.com/site/apispecification.html):
 * AppointmentTypes, ApptFields, ApptFieldDefs, AsapComms, ClockEvents,
 * HistAppointments, Operatories, ScheduleOps and Schedules.
 *
 * Appointments already have a dedicated typed controller at /api/appointments,
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
        "/api/appointmenttypes", "/api/appointmenttypes/**",
        "/api/apptfields", "/api/apptfields/**",
        "/api/apptfielddefs", "/api/apptfielddefs/**",
        "/api/asapcomms", "/api/asapcomms/**",
        "/api/clockevents", "/api/clockevents/**",
        "/api/histappointments", "/api/histappointments/**",
        "/api/operatories", "/api/operatories/**",
        "/api/scheduleops", "/api/scheduleops/**",
        "/api/schedules", "/api/schedules/**"})
public class SchedulingResourcesController {

    private static final Set<String> RESOURCES = Set.of(
            "appointmenttypes", "apptfields", "apptfielddefs", "asapcomms",
            "clockevents", "histappointments", "operatories", "scheduleops", "schedules");

    private static final Map<String, Set<String>> QUERY_FIELDS = Map.ofEntries(
            Map.entry("appointmenttypes", Set.of()),
            Map.entry("apptfields", Set.of("AptNum", "FieldName")),
            Map.entry("apptfielddefs", Set.of()),
            Map.entry("asapcomms", Set.of("ClinicNum", "date", "dateStart", "dateEnd")),
            Map.entry("clockevents", Set.of("EmployeeNum", "ClockStatus", "ClinicNum",
                    "date", "dateStart", "dateEnd")),
            Map.entry("histappointments", Set.of("HistApptAction", "AptNum", "PatNum", "AptStatus",
                    "ClinicNum", "date", "dateStart", "dateEnd")),
            Map.entry("operatories", Set.of("ClinicNum")),
            Map.entry("scheduleops", Set.of("ScheduleNum", "OperatoryNum")),
            Map.entry("schedules", Set.of("date", "dateStart", "dateEnd", "SchedType",
                    "BlockoutDefNum", "ProvNum", "EmployeeNum")));

    @Value("${opendental.base-url}")
    private String baseUrl;
    private final RestTemplate restTemplate;

    public SchedulingResourcesController(RestTemplate restTemplate) {
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
        if (route.equals("collection")) {
            kind = "collection";
            upstreamPath = "/" + resource;
        } else if (route.matches("[0-9]+")) {
            id = route;
            kind = "id";
            upstreamPath = "/" + resource + "/" + id;
        } else return null;

        boolean supported = switch (resource) {
            case "appointmenttypes" -> supports(kind, method,
                    "collection", "GET", "POST", "id", "GET", "PUT", "DELETE");
            case "apptfields" -> supports(kind, method,
                    "collection", "GET", "POST", "PUT", "id", "GET", "PUT", "DELETE");
            case "apptfielddefs" -> supports(kind, method,
                    "collection", "GET", "POST", "id", "GET", "PUT");
            case "asapcomms" -> supports(kind, method,
                    "collection", "GET", "POST", "id", "GET");
            case "clockevents" -> supports(kind, method,
                    "collection", "GET", "id", "GET");
            case "histappointments" -> supports(kind, method,
                    "collection", "GET");
            case "operatories" -> supports(kind, method,
                    "collection", "GET", "id", "GET");
            case "scheduleops" -> supports(kind, method,
                    "collection", "GET");
            case "schedules" -> supports(kind, method,
                    "collection", "GET", "id", "GET");
            default -> false;
        };
        return supported ? new Route(kind, upstreamPath, id, method.name()) : null;
    }

    private boolean isKnownRoute(String resource, String route) {
        return route.equals("collection") || route.matches("[0-9]+");
    }

    private boolean supports(String kind, HttpMethod method, Object... args) {
        Set<String> routeKinds = Set.of("collection", "id");
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
            if (!allowed.contains(key)) return "Unsupported query parameter: " + key;
            if (!route.kind.equals("collection")) {
                return "Query parameters are not supported for this route.";
            }
        }
        for (String key : List.of("AptNum", "PatNum", "ProvNum", "ScheduleNum",
                "OperatoryNum", "EmployeeNum", "BlockoutDefNum")) {
            if (params.containsKey(key) && !nonNegative(params.get(key))) {
                return key + " must be a non-negative integer.";
            }
        }
        if (params.containsKey("ClinicNum") && !nonNegative(params.get("ClinicNum"))) {
            return "ClinicNum must be a non-negative integer.";
        }
        for (String key : List.of("date", "dateStart", "dateEnd")) {
            if (params.containsKey(key) && !validDate(params.get(key))) {
                return key + " must use yyyy-MM-dd format.";
            }
        }
        if (params.containsKey("HistApptAction")
                && !Set.of("Created", "Changed", "Missed", "Cancelled", "Deleted").contains(params.get("HistApptAction"))) {
            return "HistApptAction must be Created, Changed, Missed, Cancelled, or Deleted.";
        }
        if (params.containsKey("AptStatus")
                && !Set.of("Scheduled", "Complete", "UnschedList", "Broken", "Planned").contains(params.get("AptStatus"))) {
            return "AptStatus must be Scheduled, Complete, UnschedList, Broken, or Planned.";
        }
        if (params.containsKey("ClockStatus")
                && !Set.of("Home", "Lunch", "Break").contains(params.get("ClockStatus"))) {
            return "ClockStatus must be Home, Lunch, or Break.";
        }
        if (params.containsKey("SchedType")
                && !Set.of("Practice", "Provider", "Blockout", "Employee", "WebSchedASAP").contains(params.get("SchedType"))) {
            return "SchedType must be Practice, Provider, Blockout, Employee, or WebSchedASAP.";
        }
        if (params.containsKey("FieldName") && blank(params.get("FieldName"))) {
            return "FieldName must not be blank.";
        }
        return null;
    }

    private String validateBody(String resource, Route route, Map<String, Object> body) {
        boolean write = (route.kind.equals("id") && route.method.equals("PUT"))
                || (route.kind.equals("collection") && (route.method.equals("POST")
                || (route.method.equals("PUT") && resource.equals("apptfields"))));
        if (write && body == null) return "A JSON request body is required.";
        if (body == null) return null;

        if (route.kind.equals("collection") && route.method.equals("POST")) {
            Set<String> required = switch (resource) {
                case "appointmenttypes" -> Set.of("AppointmentTypeName");
                case "apptfields" -> Set.of("AptNum", "FieldValue");
                case "apptfielddefs" -> Set.of("FieldName");
                case "asapcomms" -> Set.of("op", "dateTimeStart");
                default -> Set.of();
            };
            for (String key : required) {
                if (!body.containsKey(key) || body.get(key) == null
                        || (!key.equals("FieldValue") && blank(String.valueOf(body.get(key))))) {
                    return key + " is required.";
                }
            }
            if (resource.equals("apptfields") && !body.containsKey("FieldName")
                    && !body.containsKey("apptFieldDefNum") && !body.containsKey("ApptFieldDefNum")) {
                return "Provide FieldName or apptFieldDefNum.";
            }
            if (resource.equals("asapcomms") && !body.containsKey("aptNum") && !body.containsKey("recallNum")) {
                return "Provide aptNum or recallNum.";
            }
        }
        return validateBodyValues(resource, body);
    }

    private String validateBodyValues(String resource, Map<String, Object> body) {
        for (String key : List.of("AptNum", "PatNum", "ProvNum", "ScheduleNum", "OperatoryNum",
                "EmployeeNum", "AppointmentTypeNum", "ApptFieldNum", "ApptFieldDefNum",
                "apptFieldDefNum", "FKey", "recallNum", "ClinicNum")) {
            if (body.containsKey(key) && !nonNegative(String.valueOf(body.get(key)))) {
                return key + " must be a non-negative integer.";
            }
        }
        if (body.containsKey("op") && !positive(String.valueOf(body.get("op")))) {
            return "op must be a positive integer.";
        }
        if (body.containsKey("IsHidden") && !bool(String.valueOf(body.get("IsHidden")))) {
            return "IsHidden must be true or false.";
        }
        if (body.containsKey("RequiredProcCodesNeeded")
                && !Set.of("None", "AtLeastOne", "All").contains(body.get("RequiredProcCodesNeeded"))) {
            return "RequiredProcCodesNeeded must be None, AtLeastOne, or All.";
        }
        if (resource.equals("apptfielddefs") && body.containsKey("FieldType")
                && !Set.of("Text", "PickList").contains(body.get("FieldType"))) {
            return "FieldType must be Text or PickList.";
        }
        if (resource.equals("apptfielddefs") && "PickList".equals(body.get("FieldType"))
                && (body.get("PickList") == null || blank(String.valueOf(body.get("PickList"))))) {
            return "PickList is required when FieldType is PickList.";
        }
        if (body.containsKey("dateTimeStart") && !validDateTime(String.valueOf(body.get("dateTimeStart")))) {
            return "dateTimeStart must use yyyy-MM-dd HH:mm:ss format.";
        }
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