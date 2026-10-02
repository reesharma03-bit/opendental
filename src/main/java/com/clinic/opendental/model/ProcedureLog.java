package com.clinic.opendental.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "procedure_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcedureLog {

    @EmbeddedId
    private ProcedureLogId id;

    @Column(name = "pat_num")
    private Long patNum;

    @Column(name = "apt_num")
    private Long aptNum;

    @Column(name = "proc_date")
    private LocalDate procDate;

    @Column(name = "proc_fee", precision = 12, scale = 2)
    private BigDecimal procFee;

    @Column(name = "surf")
    private String surf;

    @Column(name = "tooth_num")
    private String toothNum;

    @Column(name = "tooth_range")
    private String toothRange;

    @Column(name = "priority")
    private Long priority;

    @Column(name = "proc_status")
    private String procStatus;

    @Column(name = "prov_num")
    private Long provNum;

    @Column(name = "prov_abbr")
    private String provAbbr;

    @Column(name = "dx")
    private Long dx;

    @Column(name = "dx_name")
    private String dxName;

    @Column(name = "planned_apt_num")
    private Long plannedAptNum;

    @Column(name = "place_service")
    private String placeService;

    @Column(name = "prosthesis")
    private String prosthesis;

    @Column(name = "date_original_prosth")
    private LocalDate dateOriginalProsth;

    @Column(name = "claim_note", columnDefinition = "TEXT")
    private String claimNote;

    @Column(name = "date_entry_c")
    private LocalDate dateEntryC;

    @Column(name = "clinic_num")
    private Long clinicNum;

    @Column(name = "diagnostic_code")
    private String diagnosticCode;

    @Column(name = "is_princ_diag")
    private String isPrincDiag;

    @Column(name = "code_num")
    private Long codeNum;

    @Column(name = "proc_code")
    private String procCode;

    @Column(name = "descript")
    private String descript;

    @Column(name = "unit_qty")
    private Integer unitQty;

    @Column(name = "base_units")
    private Integer baseUnits;

    @Column(name = "date_tp")
    private LocalDate dateTP;

    @Column(name = "site_num")
    private Long siteNum;

    @Column(name = "hide_graphics")
    private String hideGraphics;

    @Column(name = "canadian_type_codes")
    private String canadianTypeCodes;

    @Column(name = "proc_time")
    private String procTime;

    @Column(name = "proc_time_end")
    private String procTimeEnd;

    @Column(name = "date_t_stamp")
    private LocalDateTime dateTStamp;

    @Column(name = "prognosis")
    private Long prognosis;

    @Column(name = "is_locked")
    private String isLocked;

    @Column(name = "billing_note", columnDefinition = "TEXT")
    private String billingNote;

    @Column(name = "snomed_body_site")
    private String snomedBodySite;

    @Column(name = "diagnostic_code2")
    private String diagnosticCode2;

    @Column(name = "diagnostic_code3")
    private String diagnosticCode3;

    @Column(name = "diagnostic_code4")
    private String diagnosticCode4;

    @Column(name = "discount", precision = 12, scale = 2)
    private BigDecimal discount;

    @Column(name = "is_date_prosth_est")
    private String isDateProsthEst;

    @Column(name = "icd_version")
    private Integer icdVersion;

    @Column(name = "sec_date_entry")
    private LocalDateTime secDateEntry;

    @Column(name = "discount_plan_amt", precision = 12, scale = 2)
    private BigDecimal discountPlanAmt;

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