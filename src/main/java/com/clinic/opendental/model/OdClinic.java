package com.clinic.opendental.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Open Dental clinic synced from {@code GET /api/v1/clinics} within a single
 * Open Dental installation.
 *
 * <p>The tenant/installation (which server + credentials to use) lives in the
 * {@code clinics} registry; this entity holds the clinics that Open Dental
 * itself reports inside one installation, keyed by
 * {@code (clinic_id = tenant registry id, clinic_num = Open Dental clinic id)}.
 */
@Entity
@Table(name = "od_clinics")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OdClinic {

    @EmbeddedId
    private OdClinicId id;

    @Column(name = "abbr")
    private String abbr;

    @Column(name = "description")
    private String description;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

