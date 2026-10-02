package com.clinic.opendental.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "patients")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Patient {

    @EmbeddedId
    private PatientId id;

    @Column(name = "l_name", nullable = false)
    private String lName;

    @Column(name = "f_name", nullable = false)
    private String fName;

    @Column(name = "middle_i")
    private String middleI;

    @Column(name = "preferred")
    private String preferred;

    @Column(name = "pat_status")
    private String patStatus;

    @Column(name = "gender")
    private String gender;

    @Column(name = "position")
    private String position;

    @Column(name = "birthdate")
    private LocalDate birthdate;

    @Column(name = "ssn")
    private String ssn;

    @Column(name = "address")
    private String address;

    @Column(name = "address2")
    private String address2;

    @Column(name = "city")
    private String city;

    @Column(name = "state")
    private String state;

    @Column(name = "zip")
    private String zip;

    @Column(name = "hm_phone")
    private String hmPhone;

    @Column(name = "wk_phone")
    private String wkPhone;

    @Column(name = "wireless_phone")
    private String wirelessPhone;

    @Column(name = "guarantor")
    private Long guarantor;

    @Column(name = "email")
    private String email;

    @Column(name = "pri_prov")
    private Long priProv;

    @Column(name = "sec_prov")
    private Long secProv;

    @Column(name = "fee_sched")
    private Long feeSched;

    @Column(name = "billing_type")
    private String billingType;

    @Column(name = "chart_number")
    private String chartNumber;

    @Column(name = "medicaid_id")
    private String medicaidId;

    @Column(name = "employer_num")
    private Long employerNum;

    @Column(name = "date_first_visit")
    private LocalDate dateFirstVisit;

    @Column(name = "clinic_num")
    private Long clinicNum;

    @Column(name = "clinic_abbr")
    private String clinicAbbr;

    @Column(name = "has_ins")
    private String hasIns;

    @Column(name = "premed")
    private Boolean premed;

    @Column(name = "ward")
    private String ward;

    @Column(name = "prefer_confirm_method")
    private String preferConfirmMethod;

    @Column(name = "prefer_contact_method")
    private String preferContactMethod;

    @Column(name = "prefer_recall_method")
    private String preferRecallMethod;

    @Column(name = "language")
    private String language;

    @Column(name = "admit_date")
    private LocalDate admitDate;

    @Column(name = "site_num")
    private Long siteNum;

    @Column(name = "site_desc")
    private String siteDesc;

    @Column(name = "super_family")
    private Long superFamily;

    @Column(name = "txt_msg_ok")
    private String txtMsgOk;

    @Column(name = "sec_user_num_entry")
    private Long secUserNumEntry;

    @Column(name = "sec_date_entry")
    private LocalDate secDateEntry;

    @Column(name = "est_balance", precision = 12, scale = 2)
    private BigDecimal estBalance;

    @Column(name = "bal_0_30", precision = 12, scale = 2)
    private BigDecimal bal030;

    @Column(name = "bal_31_60", precision = 12, scale = 2)
    private BigDecimal bal3160;

    @Column(name = "bal_61_90", precision = 12, scale = 2)
    private BigDecimal bal6190;

    @Column(name = "bal_over_90", precision = 12, scale = 2)
    private BigDecimal balOver90;

    @Column(name = "ins_est", precision = 12, scale = 2)
    private BigDecimal insEst;

    @Column(name = "bal_total", precision = 12, scale = 2)
    private BigDecimal balTotal;

    @Column(name = "date_time_last_aging")
    private LocalDateTime dateTimeLastAging;

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