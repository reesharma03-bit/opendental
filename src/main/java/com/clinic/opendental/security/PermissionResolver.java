package com.clinic.opendental.security;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Which permission each API route needs. One table for the whole backend: reads (GET)
 * need the area's READ permission, anything else its WRITE permission. Routes that are
 * not listed need {@link Permission#SYSTEM_ADMIN}, so a new endpoint is admin-only until
 * it is added here.
 */
@Component
public class PermissionResolver {

    /** What a request needs: nothing, any signed-in user, or a permission. */
    public record Requirement(Kind kind, Permission permission) {
        public enum Kind { PUBLIC, AUTHENTICATED, PERMISSION }

        static final Requirement PUBLIC = new Requirement(Kind.PUBLIC, null);
        static final Requirement AUTHENTICATED = new Requirement(Kind.AUTHENTICATED, null);

        static Requirement of(Permission permission) {
            return new Requirement(Kind.PERMISSION, permission);
        }
    }

    /** Areas of practice data; each has a READ and a WRITE permission. */
    public enum Area {
        PATIENTS(Permission.PATIENTS_READ, Permission.PATIENTS_WRITE),
        APPOINTMENTS(Permission.APPOINTMENTS_READ, Permission.APPOINTMENTS_WRITE),
        CLINICAL(Permission.CLINICAL_READ, Permission.CLINICAL_WRITE),
        BILLING(Permission.BILLING_READ, Permission.BILLING_WRITE),
        SYNC(Permission.SYNC_MANAGE, Permission.SYNC_MANAGE);

        public final Permission read;
        public final Permission write;

        Area(Permission read, Permission write) {
            this.read = read;
            this.write = write;
        }
    }

    private static final Map<String, Area> AREAS = new HashMap<>();

    static {
        put(Area.PATIENTS, "patients", "patientnotes", "patientraces", "ehrpatients", "familymodules", "guardians",
                "patfields", "patfielddefs", "popups", "patrestrictions", "commlogs", "referrals", "refattaches",
                "definitions", "employers", "sheets", "sheetfields", "sheetdefs", "sheetfielddefs",
                "tasks", "tasklists", "tasknotes", "quickpastecats", "quickpastenotes");
        put(Area.APPOINTMENTS, "appointments", "appointmenttypes", "apptfields", "apptfielddefs", "asapcomms",
                "histappointments", "schedules", "scheduleops", "operatories", "providers", "clockevents",
                "recalls", "recalltypes");
        put(Area.CLINICAL, "allergies", "allergydefs", "diseases", "diseasedefs", "medications", "medicationpats",
                "rxpats", "vitalsigns", "pharmacies", "procedurelogs", "procedurecodes", "procnotes", "proctps",
                "treatplans", "treatplanattaches", "perioexams", "periomeasures", "toothinitials", "autonotes",
                "autonotecontrols", "codegroups", "chartmodules", "documents", "labcases", "laboratories",
                "labturnarounds");
        put(Area.BILLING, "benefits", "carriers", "claimforms", "claimpayments", "claimprocs", "claims",
                "claimtrackings", "covcats", "covspans", "deposits", "discountplans", "discountplansubs", "eobattaches",
                "fees", "feescheds", "insplans", "inssubs", "insverifies", "payments", "payplancharges", "payplanlinks",
                "payplans", "paysplits", "statements", "substitutionlinks", "patplans", "etranss", "adjustments", "accountmodules");
        // Staff records and Open Dental's own users and security groups: admins only
        put(Area.SYNC, "subscriptions", "employees", "userods", "usergroups", "usergroupattaches");
    }

    private static void put(Area area, String... resources) {
        for (String resource : resources) AREAS.put(resource, area);
    }

    /** The area a resource name belongs to, or null when it isn't practice data we know. */
    public Area area(String resource) {
        return resource == null ? null : AREAS.get(resource.toLowerCase());
    }

    public Requirement required(String method, String path) {
        String m = method == null ? "GET" : method.toUpperCase();
        String p = path == null ? "/" : path.replaceAll("/+$", "");
        if (p.isEmpty()) p = "/";
        boolean read = m.equals("GET") || m.equals("HEAD");

        if (m.equals("OPTIONS")) return Requirement.PUBLIC;
        if (p.equals("/error") || p.equals("/actuator/health")) return Requirement.PUBLIC;
        if (!p.startsWith("/api/")) return Requirement.of(Permission.SYSTEM_ADMIN);

        String[] parts = p.substring("/api/".length()).split("/");
        String first = parts[0].toLowerCase();
        String second = parts.length > 1 ? parts[1] : null;

        switch (first) {
            case "auth":
                return Set.of("login", "logout").contains(second) ? Requirement.PUBLIC : Requirement.AUTHENTICATED;
            case "webhooks":
                // Called by Open Dental's servers, which cannot sign in.
                return Requirement.PUBLIC;
            case "users":
            case "audit":
                return Requirement.of(Permission.USERS_MANAGE);
            case "dashboard":
                return Requirement.AUTHENTICATED;
            case "assistant":
                return Requirement.of(Permission.ASSISTANT_USE);
            case "sync":
                return read && "force".equals(second) && parts.length > 2 && "status".equals(parts[2])
                        ? Requirement.AUTHENTICATED : Requirement.of(Permission.SYNC_MANAGE);
            case "queries":
                return Requirement.of(Permission.SYSTEM_ADMIN);
            case "database":
                if (second == null) return read ? Requirement.AUTHENTICATED : Requirement.of(Permission.SYSTEM_ADMIN);
                return forArea(area(second), read);
            default:
                return forArea(area(first), read);
        }
    }

    private static Requirement forArea(Area area, boolean read) {
        if (area == null) return Requirement.of(Permission.SYSTEM_ADMIN);
        return Requirement.of(read ? area.read : area.write);
    }

    /** Whether someone with these permissions may read / change a resource. */
    public boolean allowed(Set<Permission> permissions, String resource, boolean write) {
        Area area = area(resource);
        if (area == null) return permissions.contains(Permission.SYSTEM_ADMIN);
        return permissions.contains(write ? area.write : area.read);
    }
}
