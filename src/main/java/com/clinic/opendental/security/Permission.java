package com.clinic.opendental.security;

/** What a signed-in user may do. Roles are fixed bundles of these (see {@link Role}). */
public enum Permission {
    PATIENTS_READ,
    PATIENTS_WRITE,
    APPOINTMENTS_READ,
    APPOINTMENTS_WRITE,
    /** Allergies, problems, medications, procedures, treatment plans, perio, documents. */
    CLINICAL_READ,
    CLINICAL_WRITE,
    /** Insurance, claims, payments, fees, statements. */
    BILLING_READ,
    BILLING_WRITE,
    ASSISTANT_USE,
    /** Force Sync, the push queue, Open Dental subscriptions. */
    SYNC_MANAGE,
    /** Users, roles and the audit log. */
    USERS_MANAGE,
    /** Raw Open Dental queries, API docs and anything not explicitly mapped. */
    SYSTEM_ADMIN;

    /** Spring Security authority name. */
    public String authority() {
        return "PERM_" + name();
    }
}
