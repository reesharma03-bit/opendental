package com.clinic.opendental.service.Impl;

import java.util.Map;
import java.util.Set;

/**
 * How Open Dental can be asked for only what changed, as documented on each API's page of
 * https://www.opendental.com/site/apispecification.html (checked 2026-10-30) for the APIs
 * listed here. A timestamp a response carries is not, by itself, something Open Dental can
 * filter by: an undocumented parameter (any other listed resource, and the {@link #CANDIDATES})
 * is only used once it is verified against the clinic's own Open Dental.
 *
 * <p>Every filter takes Open Dental's local time as "yyyy-MM-dd HH:mm:ss". The checkpoint
 * (sync_cursors.watermark) is the newest stamp merged, so it is on Open Dental's clock too.</p>
 */
final class OdChangeFilters {

    /** Open Dental's timestamp format, for both the filter value and the response fields. */
    static final String FORMAT = "yyyy-MM-dd HH:mm:ss";

    /**
     * @param param      the documented query parameter
     * @param stampField the response field the checkpoint is taken from
     * @param since      Open Dental's version that added the parameter; an older one ignores
     *                   or refuses it, so it is checked once per clinic before it is relied on
     */
    record Filter(String param, String stampField, String since) {
    }

    /**
     * The in-scope APIs whose list endpoint documents a change filter. Keyed by our resource
     * name (od_resource_records.resource, or the core table name).
     */
    static final Map<String, Filter> FILTERS = Map.of(
            // "Only include appointments with a DateTStamp altered after the specified date and time."
            "appointments", new Filter("DateTStamp", "DateTStamp", "21.1"),
            // "Gets commlogs modified after a specified date."
            "commlogs", new Filter("DateTStamp", "DateTStamp", "25.1.13"),
            // Documented on GET /patients/Simple only (not GET /patients).
            "patients", new Filter("DateTStamp", "DateTStamp", "21.1"),
            // "Get ProcedureCodes altered on and after the specified date and time."
            "procedurecodes", new Filter("DateTStamp", "DateTStamp", "22.4.28"),
            // "Gets procedurelogs created on or after the specified date and time": edits to older
            // procedures are not returned, so the nightly full read stays on for procedure logs.
            "procedurelogs", new Filter("DateTStamp", "DateTStamp", "21.1"),
            // "Get providers altered after the specified date and time."
            "providers", new Filter("DateTStamp", "DateTStamp", "21.3"),
            // "Only include refattaches with a DateTStamp altered after the specified date and time."
            "refattaches", new Filter("DateTStamp", "DateTStamp", "25.2.10"),
            // Treatment plans carry no DateTStamp; their documented filter is SecDateTEdit:
            // "Only include treatplans with a SecDateTEdit altered after the specified date and time."
            "treatplans", new Filter("SecDateTEdit", "SecDateTEdit", "22.4.16"));

    /**
     * The in-scope APIs whose list endpoint documents no change filter, and why. Their records
     * still carry a last-change stamp, so the same-named parameter is tried
     * ({@link #CANDIDATES}), but only once it is verified against the clinic's own Open Dental
     * (see ResourceMirrorService#acceptsChangedSince). Until then, and wherever it is not
     * honoured, they are read in full and the merge only writes new or changed records.
     */
    static final Map<String, String> UNFILTERED = Map.ofEntries(
            Map.entry("allergies", "GET /allergies documents only PatNum"),
            Map.entry("allergydefs", "GET /allergydefs documents only Offset"),
            Map.entry("diseasedefs", "GET /diseasedefs documents only Offset"),
            Map.entry("documents", "GET /documents requires PatNum and documents no other filter"),
            Map.entry("histappointments", "GET /histappointments documents HistApptAction, AptNum, PatNum, AptStatus, "
                    + "ClinicNum and date/dateStart/dateEnd (days); HistDateTStamp is a response field only"),
            Map.entry("labcases", "GET /labcases documents PatNum, LaboratoryNum, AptNum, PlannedAptNum, ProvNum"),
            Map.entry("medications", "GET /medications documents no parameters"),
            Map.entry("recalls", "GET /recalls documents only PatNum"),
            Map.entry("referrals", "GET /referrals documents IsHidden, NotPerson, IsDoctor, IsPreferred, isPatient, BusinessName"),
            Map.entry("rxpats", "GET /rxpats documents only PatNum"),
            Map.entry("statements", "GET /statements documents only PatNum"));

    /**
     * Undocumented change parameters tried for {@link #UNFILTERED} APIs: the stamp field their
     * records carry (HistDateTStamp for history rows). Documents have none to try: the endpoint
     * requires PatNum, so it is read patient by patient.
     */
    static final Map<String, String> CANDIDATES = Map.ofEntries(
            Map.entry("allergies", "DateTStamp"),
            Map.entry("allergydefs", "DateTStamp"),
            Map.entry("diseasedefs", "DateTStamp"),
            Map.entry("histappointments", "HistDateTStamp"),
            Map.entry("labcases", "DateTStamp"),
            Map.entry("medications", "DateTStamp"),
            Map.entry("recalls", "DateTStamp"),
            Map.entry("referrals", "DateTStamp"),
            Map.entry("rxpats", "DateTStamp"),
            Map.entry("statements", "DateTStamp"));

    /** The APIs synced incrementally. */
    static final Set<String> IN_SCOPE;

    static {
        java.util.Set<String> all = new java.util.HashSet<>(FILTERS.keySet());
        all.addAll(UNFILTERED.keySet());
        IN_SCOPE = Set.copyOf(all);
    }

    /** Last-change field of a record: HistDateTStamp for history rows, SecDateTEdit for treatment plans. */
    private static final Map<String, String> STAMP_FIELDS = Map.of(
            "histappointments", "HistDateTStamp",
            "treatplans", "SecDateTEdit");

    static boolean inScope(String resource) {
        return IN_SCOPE.contains(resource);
    }

    /** The documented change filter, or null when the API has none (or is not in scope). */
    static Filter filter(String resource) {
        return FILTERS.get(resource);
    }

    /**
     * Whether the change parameter is undocumented, so must be verified first: the
     * {@link #CANDIDATES}, and every resource not listed here (tried with DateTStamp).
     */
    static boolean needsVerifying(String resource) {
        return CANDIDATES.containsKey(resource) || !inScope(resource);
    }

    /**
     * Query parameter a change pull of {@code resource} uses: the documented one, else the
     * candidate to verify; null when there is none (documents). Out of scope: DateTStamp, as before.
     */
    static String param(String resource) {
        Filter filter = FILTERS.get(resource);
        if (filter != null) {
            return filter.param();
        }
        if (inScope(resource)) {
            return CANDIDATES.get(resource);
        }
        return "DateTStamp";
    }

    /** The response field holding a record's last change. */
    static String stampField(String resource) {
        return STAMP_FIELDS.getOrDefault(resource, "DateTStamp");
    }

    private OdChangeFilters() {
    }
}
