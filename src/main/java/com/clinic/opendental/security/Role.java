package com.clinic.opendental.security;

import java.util.EnumSet;
import java.util.Set;

import static com.clinic.opendental.security.Permission.*;

/** Dashboard roles and what each may do. */
public enum Role {
    ADMIN("Admin", EnumSet.allOf(Permission.class)),
    DENTIST("Dentist", EnumSet.of(PATIENTS_READ, PATIENTS_WRITE, APPOINTMENTS_READ, APPOINTMENTS_WRITE,
            CLINICAL_READ, CLINICAL_WRITE, BILLING_READ, ASSISTANT_USE)),
    FRONT_DESK("Front desk", EnumSet.of(PATIENTS_READ, PATIENTS_WRITE, APPOINTMENTS_READ, APPOINTMENTS_WRITE,
            CLINICAL_READ, ASSISTANT_USE)),
    BILLING("Billing", EnumSet.of(PATIENTS_READ, APPOINTMENTS_READ, BILLING_READ, BILLING_WRITE, ASSISTANT_USE)),
    READ_ONLY("Read-only", EnumSet.of(PATIENTS_READ, APPOINTMENTS_READ, CLINICAL_READ, BILLING_READ, ASSISTANT_USE));

    private final String label;
    private final Set<Permission> permissions;

    Role(String label, Set<Permission> permissions) {
        this.label = label;
        this.permissions = permissions;
    }

    public String label() {
        return label;
    }

    public Set<Permission> permissions() {
        return permissions;
    }

    public static Role parse(String value) {
        return value == null ? null : Role.valueOf(value.trim().toUpperCase());
    }
}
