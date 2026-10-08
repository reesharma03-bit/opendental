package com.clinic.opendental.service.Impl;

import java.util.List;
import java.util.Map;

/**
 * Open Dental API resources copied into {@code od_resource_records}.
 *
 * <p>Patients, appointments and procedure logs have their own Supabase tables, synced by
 * {@link ReconciliationSyncService}; the dashboard reads and changes them through those
 * tables ({@link #TYPED}). Pat fields, operatories, schedules and tooth initials also have
 * typed tables, but are copied here too so every catalog screen reads the same way.
 * Chart modules are views computed per patient, not records, so they are not copied.</p>
 */
final class OdResourceCatalog {

    /** Parent sources that are not themselves mirrored resources. */
    static final String PATIENTS = "patients";
    static final String APPOINTMENTS = "appointments";

    /**
     * @param resource    name stored in {@code od_resource_records.resource}
     * @param path        Open Dental path; {@code {id}} is replaced by the parent id
     * @param keyField    field that identifies a row; a hash of the row is used when absent
     * @param parent      null for a plain list, otherwise where the ids to query by come from:
     *                    {@link #PATIENTS}, {@link #APPOINTMENTS} or a mirrored resource
     * @param parentField query parameter (and field of the parent rows) carrying the parent id
     * @param passes      fixed query parameters of a full read; one paged read per entry. Some
     *                    endpoints leave records out by default (hidden definitions, archived
     *                    task lists, tasks older than 14 days), so they are asked explicitly.
     */
    record Resource(String resource, String path, String keyField, String parent, String parentField,
                    List<Map<String, String>> passes) {

        Resource(String resource, String path, String keyField, String parent, String parentField) {
            this(resource, path, keyField, parent, parentField, List.of(Map.of()));
        }

        boolean isList() {
            return parent == null;
        }

        /** The parent id is part of the URL, so rows are only unique per parent. */
        boolean parentInPath() {
            return path.contains("{id}");
        }
    }

    private static Resource list(String resource, String keyField) {
        return new Resource(resource, "/" + resource, keyField, null, null);
    }

    @SafeVarargs
    private static Resource list(String resource, String keyField, Map<String, String>... passes) {
        return new Resource(resource, "/" + resource, keyField, null, null, List.of(passes));
    }

    private static Resource perParent(String resource, String path, String keyField, String parent, String parentField) {
        return new Resource(resource, path, keyField, parent, parentField);
    }

    /** One paged list call each. Synced on every resource sync. */
    static final List<Resource> LISTS = List.of(
            // Patients & families
            list("allergydefs", "AllergyDefNum"),
            list("diseasedefs", "DiseaseDefNum"),
            list("diseases", "DiseaseNum"),
            list("guardians", "GuardianNum"),
            list("medicationpats", "MedicationPatNum"),
            list("medications", "MedicationNum"),
            list("patientnotes", "PatNum"),
            list("patfielddefs", "PatFieldDefNum"),
            list("patfields", "PatFieldNum"),
            list("patplans", "PatPlanNum"),
            list("patrestrictions", "PatRestrictionNum"),
            list("pharmacies", "PharmacyNum"),
            list("recalls", "RecallNum"),
            list("recalltypes", "RecallTypeNum"),
            list("rxpats", "RxNum"),
            list("vitalsigns", "VitalsignNum"),
            // Scheduling
            list("appointmenttypes", "AppointmentTypeNum"),
            list("apptfielddefs", "ApptFieldDefNum"),
            list("asapcomms", "AsapCommNum"),
            list("clockevents", "ClockEventNum"),
            list("histappointments", "HistApptNum"),
            list("operatories", "OperatoryNum"),
            list("scheduleops", "ScheduleOpNum"),
            list("schedules", "ScheduleNum"),
            // Clinical care
            list("autonotecontrols", "AutoNoteControlNum"),
            list("autonotes", "AutoNoteNum"),
            list("codegroups", "CodeGroupNum"),
            list("perioexams", "PerioExamNum"),
            list("procedurecodes", "CodeNum"),
            list("procnotes", "ProcNoteNum"),
            list("treatplans", "TreatPlanNum"),
            // Insurance & billing
            list("benefits", "BenefitNum"),
            list("carriers", "CarrierNum"),
            list("claimforms", "ClaimFormNum"),
            list("claimpayments", "ClaimPaymentNum"),
            list("claimprocs", "ClaimProcNum"),
            list("claims", "ClaimNum"),
            list("claimtrackings", "ClaimTrackingNum"),
            list("covcats", "CovCatNum"),
            list("covspans", "CovSpanNum"),
            list("deposits", "DepositNum"),
            list("discountplans", "DiscountPlanNum"),
            list("fees", "FeeNum"),
            list("feescheds", "FeeSchedNum"),
            list("insplans", "PlanNum"),
            list("inssubs", "InsSubNum"),
            list("insverifies", "InsVerifyNum"),
            list("payments", "PayNum"),
            list("payplanlinks", "PayPlanLinkNum"),
            list("paysplits", "SplitNum"),
            list("statements", "StatementNum"),
            // Practice setup used by the dashboard screens
            list("providers", "ProvNum"),
            // Open Dental webhook subscriptions (where Open Dental sends change events)
            list("subscriptions", "SubscriptionNum"),
            // Communication and referrals. Commlogs lists without PatNum from Open Dental 25.1.13.
            list("commlogs", "CommlogNum"),
            list("referrals", "ReferralNum"),
            list("refattaches", "RefAttachNum"),
            // Lookups: hidden definitions are left out unless asked for
            list("definitions", "DefNum", Map.of("includeHidden", "true")),
            // Labs
            list("laboratories", "LaboratoryNum"),
            list("labturnarounds", "LabTurnaroundNum"),
            list("labcases", "LabCaseNum"),
            // Forms. Sheet fields list without SheetNum from Open Dental 25.2.3.
            list("sheetdefs", "SheetDefNum"),
            list("sheetfielddefs", "SheetFieldDefNum"),
            list("sheetfields", "SheetFieldNum"),
            // Tasks: task lists default to Active only; tasks to the last 14 days unless a start is given
            list("tasklists", "TaskListNum", Map.of("TaskListStatus", "Active"), Map.of("TaskListStatus", "Archived")),
            list("tasks", "TaskNum", Map.of("DateTimeOriginal", "1900-01-01 00:00:00")),
            list("tasknotes", "TaskNoteNum"),
            // Staff, employers and note templates
            list("employees", "EmployeeNum"),
            list("employers", "EmployerNum"),
            list("quickpastecats", "QuickPasteCatNum"),
            list("quickpastenotes", "QuickPasteNoteNum"),
            // Open Dental's own users and their security groups (hidden users included)
            list("usergroups", "UserGroupNum"),
            list("usergroupattaches", "UserGroupAttachNum"),
            list("userods", "UserNum", Map.of("includeHidden", "true")));

    /**
     * Resources Open Dental only returns per patient or per parent record: one call per
     * parent, so they run in the full (nightly / Force Sync) pass. Order matters: a
     * resource's parent is synced before it.
     */
    static final List<Resource> PER_PARENT = List.of(
            perParent("allergies", "/allergies", "AllergyNum", PATIENTS, "PatNum"),
            perParent("patientraces", "/patientraces", "PatientRaceNum", PATIENTS, "PatNum"),
            perParent("popups", "/popups", "PopupNum", PATIENTS, "PatNum"),
            perParent("discountplansubs", "/discountplansubs", "DiscountSubNum", PATIENTS, "PatNum"),
            perParent("payplans", "/payplans", "PayPlanNum", PATIENTS, "PatNum"),
            perParent("familymodules", "/familymodules/{id}/Insurance", "InsSubNum", PATIENTS, "PatNum"),
            perParent("ehrpatients", "/ehrpatients/{id}", "PatNum", PATIENTS, "PatNum"),
            perParent("toothinitials", "/toothinitials", "ToothInitialNum", PATIENTS, "PatNum"),
            perParent("apptfields", "/apptfields", "ApptFieldNum", APPOINTMENTS, "AptNum"),
            perParent("periomeasures", "/periomeasures", "PerioMeasureNum", "perioexams", "PerioExamNum"),
            perParent("proctps", "/proctps", "ProcTPNum", "treatplans", "TreatPlanNum"),
            perParent("treatplanattaches", "/treatplanattaches", "TreatPlanAttachNum", "treatplans", "TreatPlanNum"),
            perParent("eobattaches", "/eobattaches", "EobAttachNum", "claimpayments", "ClaimPaymentNum"),
            perParent("payplancharges", "/payplancharges", "PayPlanChargeNum", "payplans", "PayPlanNum"),
            perParent("substitutionlinks", "/substitutionlinks", "SubstitutionLinkNum", "insplans", "PlanNum"),
            // Patient forms and electronic claim transmissions: Open Dental lists these per patient only
            perParent("sheets", "/sheets", "SheetNum", PATIENTS, "PatNum"),
            perParent("etranss", "/etranss", "EtransNum", PATIENTS, "PatNum"),
            perParent("adjustments", "/adjustments", "AdjNum", PATIENTS, "PatNum"));

    /** Every mirrored resource, by name. */
    static Resource find(String resource) {
        return java.util.stream.Stream.concat(LISTS.stream(), PER_PARENT.stream())
                .filter(r -> r.resource().equals(resource))
                .findFirst()
                .orElse(null);
    }

    /**
     * How a mirrored resource is written: saved in od_resource_records first, then sent
     * to Open Dental through od_sync_queue.
     *
     * @param defaults           fields Open Dental fills in on create, so our copy shows them right away
     * @param matchOnCreate      for endpoints that answer a create without a body: the field used to
     *                           find the new record afterwards (newest record with the same value)
     * @param updateOnCollection Open Dental updates these with PUT /{resource} (identity in the body)
     *                           instead of PUT /{resource}/{key}
     */
    record Writable(boolean create, boolean update, boolean delete,
                    Map<String, String> defaults, String matchOnCreate, boolean updateOnCollection) {
    }

    private static Writable ops(String ops) {
        return new Writable(ops.contains("C"), ops.contains("U"), ops.contains("D"), Map.of(), null, false);
    }

    /**
     * What Open Dental's API lets us change, per resource (C = create, U = update,
     * D = delete). Resources not listed are read-only in Open Dental's API, so the
     * dashboard shows them without Add / Edit / Delete.
     */
    static final Map<String, Writable> WRITABLE = Map.ofEntries(
            // Patients & families
            Map.entry("allergies", new Writable(true, true, true, Map.of("StatusIsActive", "true"), null, false)),
            Map.entry("allergydefs", new Writable(true, true, false, Map.of("IsHidden", "false"), null, false)),
            Map.entry("diseasedefs", new Writable(true, false, false, Map.of("IsHidden", "false"), "DiseaseName", false)),
            Map.entry("diseases", ops("CUD")),
            Map.entry("ehrpatients", ops("U")),
            Map.entry("guardians", ops("CUD")),
            Map.entry("medicationpats", ops("CUD")),
            Map.entry("medications", ops("CUD")),
            Map.entry("patientnotes", ops("U")),
            Map.entry("patfielddefs", ops("CUD")),
            Map.entry("patfields", new Writable(true, true, true, Map.of(), null, true)),
            Map.entry("patplans", ops("CUD")),
            Map.entry("patrestrictions", ops("CD")),
            Map.entry("popups", ops("CU")),
            Map.entry("recalls", ops("CU")),
            Map.entry("vitalsigns", ops("CUD")),
            // Scheduling
            Map.entry("appointmenttypes", ops("CUD")),
            Map.entry("apptfields", ops("CUD")),
            Map.entry("apptfielddefs", ops("CU")),
            Map.entry("asapcomms", ops("C")),
            // Clinical care
            Map.entry("autonotecontrols", ops("CU")),
            Map.entry("autonotes", ops("CU")),
            Map.entry("codegroups", ops("CUD")),
            Map.entry("perioexams", ops("CUD")),
            Map.entry("periomeasures", ops("CUD")),
            Map.entry("procedurecodes", ops("CU")),
            Map.entry("procnotes", ops("C")),
            Map.entry("proctps", ops("UD")),
            Map.entry("toothinitials", ops("CD")),
            Map.entry("treatplanattaches", ops("CU")),
            Map.entry("treatplans", ops("CUD")),
            // Insurance & billing
            Map.entry("benefits", ops("CUD")),
            Map.entry("carriers", ops("CU")),
            Map.entry("claimpayments", ops("CUD")),
            Map.entry("claimprocs", ops("UD")),
            Map.entry("claims", ops("CUD")),
            Map.entry("claimtrackings", ops("CU")),
            Map.entry("covcats", ops("CU")),
            Map.entry("covspans", ops("CUD")),
            Map.entry("deposits", ops("CUD")),
            Map.entry("discountplans", ops("CU")),
            Map.entry("discountplansubs", ops("CUD")),
            Map.entry("eobattaches", ops("D")),
            Map.entry("fees", ops("CUD")),
            Map.entry("feescheds", ops("CU")),
            Map.entry("insplans", ops("CU")),
            Map.entry("inssubs", ops("CUD")),
            Map.entry("insverifies", new Writable(false, true, false, Map.of(), null, true)),
            Map.entry("payments", ops("CU")),
            Map.entry("payplancharges", ops("CUD")),
            Map.entry("payplanlinks", ops("CUD")),
            Map.entry("paysplits", ops("U")),
            // Open Dental has no DELETE for adjustments
            Map.entry("adjustments", ops("CU")),
            // Communication, referrals, labs, forms, tasks and practice setup (see OdWriteSpecs)
            Map.entry("commlogs", ops("CU")),
            Map.entry("employers", ops("CUD")),
            Map.entry("referrals", ops("CU")),
            Map.entry("refattaches", ops("CUD")),
            Map.entry("labcases", ops("CUD")),
            Map.entry("laboratories", ops("CU")),
            Map.entry("labturnarounds", ops("CU")),
            Map.entry("sheets", ops("C")),
            Map.entry("sheetfields", ops("U")),
            Map.entry("tasks", ops("CU")),
            Map.entry("tasknotes", ops("CU")),
            Map.entry("definitions", ops("CU")),
            Map.entry("employees", ops("CU")),
            Map.entry("providers", ops("CU")),
            // Open Dental users: no DELETE (hide instead); created straight in Open Dental, see DatabaseResourceService
            Map.entry("userods", ops("CU")),
            Map.entry("statements", ops("CD")),
            Map.entry("substitutionlinks", ops("CUD")),
            // Webhook subscriptions
            Map.entry("subscriptions", ops("CUD")));

    /**
     * Resources with their own typed table and local-first service. The dashboard reads
     * and changes them through those (see DatabaseResourceService), not od_resource_records.
     */
    static final Map<String, Writable> TYPED = Map.of(
            "patients", ops("CU"),
            "appointments", ops("CU"),
            "procedurelogs", ops("CUD"));

    /** Key field of the typed resources. */
    static final Map<String, String> TYPED_KEYS = Map.of(
            "patients", "PatNum",
            "appointments", "AptNum",
            "procedurelogs", "ProcNum");

    /**
     * How fresh a listed resource must be. A resource Open Dental can filter by DateTStamp
     * is asked for its changes every {@code changesEvery}; one it cannot is re-read in
     * full every {@code refreshEvery}. All of them are also re-read in full nightly.
     */
    enum Tier {
        HOT(java.time.Duration.ofMinutes(2), java.time.Duration.ofMinutes(15)),
        WARM(java.time.Duration.ofMinutes(15), java.time.Duration.ofHours(1)),
        COLD(java.time.Duration.ofHours(24), java.time.Duration.ofHours(24));

        final java.time.Duration changesEvery;
        final java.time.Duration refreshEvery;

        Tier(java.time.Duration changesEvery, java.time.Duration refreshEvery) {
            this.changesEvery = changesEvery;
            this.refreshEvery = refreshEvery;
        }

        String dbName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Schedule and front desk data. */
    private static final java.util.Set<String> HOT = java.util.Set.of(
            "operatories", "schedules", "scheduleops", "providers", "asapcomms", "patfields");

    /** Setup lists that rarely change. */
    private static final java.util.Set<String> COLD = java.util.Set.of(
            "allergydefs", "diseasedefs", "medications", "patfielddefs", "pharmacies", "recalltypes",
            "appointmenttypes", "apptfielddefs", "autonotecontrols", "autonotes", "codegroups", "procedurecodes",
            "carriers", "claimforms", "covcats", "covspans", "discountplans", "fees", "feescheds", "subscriptions",
            "definitions", "referrals", "laboratories", "labturnarounds", "sheetdefs", "sheetfielddefs",
            "employees", "employers", "quickpastecats", "quickpastenotes", "usergroups", "usergroupattaches", "userods");

    /** Everything else (clinical records, insurance, billing) is WARM. */
    static Tier tier(String resource) {
        return HOT.contains(resource) ? Tier.HOT : COLD.contains(resource) ? Tier.COLD : Tier.WARM;
    }

    /** Queue entity type for a mirrored resource, e.g. {@code resource:allergies}. */
    static String entityType(String resource) {
        return "resource:" + resource;
    }

    private OdResourceCatalog() {
    }
}
