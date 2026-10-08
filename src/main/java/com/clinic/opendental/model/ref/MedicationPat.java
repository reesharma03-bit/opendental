package com.clinic.opendental.model.ref;

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

import java.time.LocalDate;
import java.time.LocalDateTime;

/** A medication on a patient's record in Open Dental (table medication_pats). */
@Entity
@Table(name = "medication_pats")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicationPat {

    @EmbeddedId
    private MedicationPatId id;

    @Column(name = "pat_num")
    private Long patNum;

    @Column(name = "medication_num")
    private Long medicationNum;

    @Column(name = "med_name")
    private String medName;

    @Column(name = "pat_note")
    private String patNote;

    @Column(name = "date_start")
    private LocalDate dateStart;

    @Column(name = "date_stop")
    private LocalDate dateStop;

    @Column(name = "prov_num")
    private Long provNum;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "is_deleted")
    private Boolean isDeleted;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isDeleted == null) {
            isDeleted = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
