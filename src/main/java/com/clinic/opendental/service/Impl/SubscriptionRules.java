package com.clinic.opendental.service.Impl;

import com.clinic.opendental.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Open Dental's rules for webhook subscriptions (https://www.opendental.com/site/apisubscriptions.html
 * and apievents.html), checked before a change is saved, so a subscription Open Dental would
 * reject never sits in the outbox failing.
 *
 * <ul>
 *   <li>A <b>database event</b> needs WatchTable (one of {@link #WATCH_TABLES}) and PollingSeconds.</li>
 *   <li>A <b>UI event</b> needs UiEventType ({@link #UI_EVENT_TYPES}) and no WatchTable/PollingSeconds.</li>
 *   <li>An update may only change {@link #UPDATABLE}; WatchTable and UiEventType are fixed once
 *       created, and PollingSeconds only applies to database events.</li>
 * </ul>
 */
public final class SubscriptionRules {

    public static final List<String> WATCH_TABLES = List.of(
            "Appointment", "AppointmentDeleted", "LabCase", "LabCaseDeleted", "MedicationPat", "MedicationPatDeleted",
            "Operatory", "PatField", "PatFieldDeleted", "Patient", "Provider", "Schedule", "ScheduleDeleted",
            "ToothInitial", "ToothInitialDeleted");
    public static final List<String> UI_EVENT_TYPES = List.of("PatientSelected");
    static final Set<String> UPDATABLE = Set.of("EndPointUrl", "Workstation", "PollingSeconds", "DateTimeStart", "DateTimeStop", "Note");

    private static final Pattern URL = Pattern.compile("^https?://\\S+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}( \\d{2}:\\d{2}(:\\d{2})?)?$");

    private SubscriptionRules() {
    }

    /** A new subscription. */
    public static void checkCreate(Map<String, Object> body) {
        List<String> problems = new ArrayList<>();
        String url = text(body, "EndPointUrl");
        if (url.isEmpty()) problems.add("EndPointUrl is required.");
        else if (!URL.matcher(url).matches()) problems.add("EndPointUrl must be an http(s) address.");

        String table = text(body, "WatchTable");
        String uiEvent = text(body, "UiEventType");
        boolean hasPolling = !text(body, "PollingSeconds").isEmpty();
        if (table.isEmpty() && uiEvent.isEmpty()) {
            problems.add("Give either WatchTable and PollingSeconds (a database event) or UiEventType (a UI event).");
        } else if (!table.isEmpty() && !uiEvent.isEmpty()) {
            problems.add("A subscription is either a database event (WatchTable) or a UI event (UiEventType), not both.");
        } else if (!table.isEmpty()) {
            if (!WATCH_TABLES.contains(table)) problems.add("WatchTable must be one of: " + String.join(", ", WATCH_TABLES) + ".");
            if (!hasPolling) problems.add("PollingSeconds is required for a database event.");
            else checkPolling(body, problems);
        } else {
            if (!UI_EVENT_TYPES.contains(uiEvent)) problems.add("UiEventType must be one of: " + String.join(", ", UI_EVENT_TYPES) + ".");
            if (hasPolling) problems.add("PollingSeconds only applies to database events.");
        }
        checkDates(body, problems);
        fail(problems);
    }

    /**
     * Changes to an existing subscription. {@code stored} is the subscription as we hold it
     * (null when unknown), to tell whether it is a database event.
     */
    public static void checkUpdate(Map<String, Object> changes, Map<String, Object> stored) {
        List<String> problems = new ArrayList<>();
        for (String field : changes.keySet()) {
            String canonical = UPDATABLE.stream().filter(f -> f.equalsIgnoreCase(field)).findFirst().orElse(null);
            if (canonical == null) {
                problems.add(field.equalsIgnoreCase("WatchTable") || field.equalsIgnoreCase("UiEventType")
                        ? field + " can't be changed; remove the subscription and add a new one."
                        : field + " can't be changed. Open Dental allows: " + String.join(", ", UPDATABLE.stream().sorted().toList()) + ".");
            }
        }
        if (changes.containsKey("EndPointUrl") && !URL.matcher(text(changes, "EndPointUrl")).matches()) {
            problems.add("EndPointUrl must be an http(s) address.");
        }
        if (!text(changes, "PollingSeconds").isEmpty()) {
            if (stored != null && text(stored, "WatchTable").isEmpty()) problems.add("PollingSeconds only applies to database events.");
            else checkPolling(changes, problems);
        }
        checkDates(changes, problems);
        fail(problems);
    }

    private static void checkPolling(Map<String, Object> body, List<String> problems) {
        try {
            if (Integer.parseInt(text(body, "PollingSeconds")) < 1) problems.add("PollingSeconds must be at least 1.");
        } catch (NumberFormatException e) {
            problems.add("PollingSeconds must be a whole number of seconds.");
        }
    }

    private static void checkDates(Map<String, Object> body, List<String> problems) {
        for (String field : List.of("DateTimeStart", "DateTimeStop")) {
            String value = text(body, field);
            if (!value.isEmpty() && !DATE.matcher(value).matches()) {
                problems.add(field + " must look like 2026-10-04 or 2026-10-04 08:00:00.");
            }
        }
    }

    /** Field value as text, whatever its casing in the request. */
    private static String text(Map<String, Object> body, String field) {
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(field)) {
                return entry.getValue() == null ? "" : String.valueOf(entry.getValue()).trim();
            }
        }
        return "";
    }

    private static void fail(List<String> problems) {
        if (!problems.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, String.join(" ", problems));
        }
    }
}
