package com.clinic.opendental.service.Impl;

import com.clinic.opendental.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What Open Dental's API accepts when a resource is created or updated, taken from its
 * documentation (https://www.opendental.com/site/api&lt;resource&gt;.html). One spec per resource
 * drives three things: the dashboard's Add/Edit forms, the checks made before a change is
 * saved, and whether Delete is offered.
 *
 * <p>Specs are written as one line, e.g.
 * {@code "C: PatNum*, Relationship*(Mother|Father), IsGuardian | U: Relationship(Mother|Father), IsGuardian | D"}:
 * {@code C:} create fields, {@code U:} update fields, {@code D} delete. {@code *} marks a
 * required field, {@code (a|b)} its allowed values, {@code :kind} overrides the field type
 * guessed from its name.</p>
 */
public record WriteSpec(List<Field> create, List<Field> update, boolean delete) {

    /** A form field. {@code kind}: text, textarea, number, date, datetime, bool, select, patient. */
    public record Field(String name, boolean required, String kind, List<String> options) {
        Map<String, Object> describe() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", name);
            map.put("required", required);
            map.put("kind", kind);
            if (!options.isEmpty()) map.put("options", options);
            return map;
        }
    }

    public boolean canCreate() {
        return !create.isEmpty();
    }

    public boolean canUpdate() {
        return !update.isEmpty();
    }

    public static WriteSpec parse(String line) {
        List<Field> create = List.of();
        List<Field> update = List.of();
        boolean delete = false;
        for (String part : line.split("\\|(?![^(]*\\))")) {
            String p = part.trim();
            if (p.startsWith("C:")) create = fields(p.substring(2));
            else if (p.startsWith("U:")) update = fields(p.substring(2));
            else if (p.equals("D")) delete = true;
            else if (!p.isEmpty()) throw new IllegalArgumentException("Bad write spec part: " + p);
        }
        return new WriteSpec(create, update, delete);
    }

    private static List<Field> fields(String text) {
        List<Field> out = new ArrayList<>();
        for (String raw : text.split(",(?![^(]*\\))")) {
            String f = raw.trim();
            if (f.isEmpty()) continue;
            String kind = null;
            int colon = f.lastIndexOf(':');
            if (colon > 0 && f.indexOf('(') < 0 || colon > f.lastIndexOf(')') && colon > 0) {
                kind = f.substring(colon + 1).trim();
                f = f.substring(0, colon).trim();
            }
            List<String> options = List.of();
            int paren = f.indexOf('(');
            if (paren > 0) {
                options = List.of(f.substring(paren + 1, f.lastIndexOf(')')).split("\\|"));
                f = f.substring(0, paren).trim();
            }
            boolean required = f.endsWith("*");
            String name = required ? f.substring(0, f.length() - 1).trim() : f;
            out.add(new Field(name, required, kind != null ? kind : options.isEmpty() ? guessKind(name) : "select", options));
        }
        return List.copyOf(out);
    }

    /** The form control a field gets when the spec doesn't say: from Open Dental's naming habits. */
    static String guessKind(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.equals("patnum") || n.equals("patnumchild") || n.equals("patnumguardian") || n.equals("subscriber")) return "patient";
        if (n.startsWith("is") && Character.isUpperCase(name.charAt(2 < name.length() ? 2 : 0)) || n.startsWith("has") || n.startsWith("show")) return "bool";
        if (n.contains("datetime")) return "datetime";
        if (n.startsWith("date") || n.endsWith("date")) return "date";
        if (n.endsWith("num") || n.endsWith("nums") || n.endsWith("amt") || n.endsWith("amount") || n.endsWith("fee") || n.contains("limit")
                || n.equals("ordinal") || n.equals("itemorder") || n.startsWith("days") || n.endsWith("max") || n.endsWith("percent")
                || n.endsWith("seconds") || n.equals("pulse") || n.equals("height") || n.equals("weight")) return "number";
        if (n.contains("note") || n.equals("memo") || n.equals("instructions") || n.equals("documentation") || n.equals("description") && false) return "textarea";
        return "text";
    }

    public Map<String, Object> describe() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("createFields", create.stream().map(Field::describe).toList());
        map.put("updateFields", update.stream().map(Field::describe).toList());
        return map;
    }

    /** Required fields present, nothing Open Dental won't accept, allowed values respected. */
    public void checkCreate(String resource, Map<String, Object> body) {
        check(resource, body, create, true);
    }

    public void checkUpdate(String resource, Map<String, Object> changes) {
        check(resource, changes, update, false);
    }

    private static void check(String resource, Map<String, Object> body, List<Field> allowed, boolean create) {
        List<String> problems = new ArrayList<>();
        for (String key : body.keySet()) {
            if (allowed.stream().noneMatch(f -> f.name().equalsIgnoreCase(key)) && !ignorable(key)) {
                problems.add(key + (create ? " can't be set when adding " : " can't be changed on ") + resource
                        + ". Open Dental accepts: " + String.join(", ", allowed.stream().map(Field::name).toList()) + ".");
            }
        }
        for (Field f : allowed) {
            Object value = value(body, f.name());
            String text = value == null ? "" : String.valueOf(value).trim();
            if (create && f.required() && text.isEmpty()) {
                problems.add(f.name() + " is required.");
                continue;
            }
            if (text.isEmpty()) continue;
            switch (f.kind()) {
                case "number" -> { if (!(value instanceof List<?>) && !text.matches("-?\\d+(\\.\\d+)?")) problems.add(f.name() + " must be a number."); }
                case "patient" -> { if (!text.matches("-?\\d+")) problems.add(f.name() + " must be a patient number."); }
                case "bool" -> { if (!text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false")) problems.add(f.name() + " must be true or false."); }
                case "date" -> { if (!text.matches("\\d{4}-\\d{2}-\\d{2}")) problems.add(f.name() + " must be a date like 2026-10-08."); }
                case "datetime" -> { if (!text.matches("\\d{4}-\\d{2}-\\d{2}( \\d{2}:\\d{2}(:\\d{2})?)?")) problems.add(f.name() + " must look like 2026-10-08 14:30:00."); }
                case "select" -> { if (f.options().stream().noneMatch(o -> o.equalsIgnoreCase(text))) problems.add(f.name() + " must be one of: " + String.join(", ", f.options()) + "."); }
                default -> { }
            }
        }
        if (!problems.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, String.join(" ", problems));
        }
    }

    /** Keys the dashboard may send that are ours, not Open Dental fields (e.g. display names for allergies). */
    private static boolean ignorable(String key) {
        return key.equals("defDescription");
    }

    private static Object value(Map<String, Object> body, String field) {
        for (Map.Entry<String, Object> e : body.entrySet()) {
            if (e.getKey().equalsIgnoreCase(field)) return e.getValue();
        }
        return null;
    }
}
