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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** An Open Dental lab case (table lab_cases, see supabase-lab-cases-medication-pats.sql). */
@Entity
@Table(name = "lab_cases")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabCase {

    @EmbeddedId
    private LabCaseId id;

    @Column(name = "pat_num")
    private Long patNum;

    @Column(name = "laboratory_num")
    private Long laboratoryNum;

    @Column(name = "apt_num")
    private Long aptNum;

    @Column(name = "planned_apt_num")
    private Long plannedAptNum;

    @Column(name = "date_time_due")
    private LocalDateTime dateTimeDue;

    @Column(name = "date_time_created")
    private LocalDateTime dateTimeCreated;

    @Column(name = "date_time_sent")
    private LocalDateTime dateTimeSent;

    @Column(name = "date_time_recd")
    private LocalDateTime dateTimeRecd;

    @Column(name = "date_time_checked")
    private LocalDateTime dateTimeChecked;

    @Column(name = "prov_num")
    private Long provNum;

    @Column(name = "instructions")
    private String instructions;

    @Column(name = "lab_fee")
    private BigDecimal labFee;

    @Column(name = "invoice_num")
    private String invoiceNum;

    @Column(name = "date_t_stamp")
    private LocalDateTime dateTStamp;

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
