package com.clinic.opendental.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "appointments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Appointment {

    @EmbeddedId
    private AppointmentId id;

    @Column(name = "pat_num")
    private Long patNum;

    @Column(name = "apt_status")
    private String aptStatus;

    @Column(name = "pattern")
    private String pattern;

    @Column(name = "confirmed")
    private Long confirmed;

    @Column(name = "time_locked")
    private String timeLocked;

    @Column(name = "op")
    private Long op;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "prov_num")
    private Long provNum;

    @Column(name = "prov_abbr")
    private String provAbbr;

    @Column(name = "prov_hyg")
    private Long provHyg;

    @Column(name = "apt_date_time")
    private LocalDateTime aptDateTime;

    @Column(name = "next_apt_num")
    private Long nextAptNum;

    @Column(name = "unsched_status")
    private Long unschedStatus;

    @Column(name = "is_new_patient")
    private String isNewPatient;

    @Column(name = "proc_descript")
    private String procDescript;

    @Column(name = "assistant")
    private Long assistant;

    @Column(name = "clinic_num")
    private Long clinicNum;

    @Column(name = "is_hygiene")
    private String isHygiene;

    @Column(name = "date_t_stamp")
    private LocalDateTime dateTStamp;

    @Column(name = "date_time_arrived")
    private LocalDateTime dateTimeArrived;

    @Column(name = "date_time_seated")
    private LocalDateTime dateTimeSeated;

    @Column(name = "date_time_dismissed")
    private LocalDateTime dateTimeDismissed;

    @Column(name = "ins_plan1")
    private Long insPlan1;

    @Column(name = "ins_plan2")
    private Long insPlan2;

    @Column(name = "date_time_asked_to_arrive")
    private LocalDateTime dateTimeAskedToArrive;

    @Column(name = "color_override")
    private String colorOverride;

    @Column(name = "appointment_type_num")
    private Long appointmentTypeNum;

    @Column(name = "sec_user_num_entry")
    private Long secUserNumEntry;

    @Column(name = "sec_date_t_entry")
    private LocalDateTime secDateTEntry;

    @Column(name = "priority")
    private String priority;

    @Column(name = "pattern_secondary")
    private String patternSecondary;

    @Column(name = "item_order_planned")
    private Long itemOrderPlanned;

    @Column(name = "is_mirrored")
    private String isMirrored;

    @Column(name = "e_service_log_type")
    private String eServiceLogType;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "datetime_deleted")
    private LocalDateTime datetimeDeleted;

    @Column(name = "appointment_num")
    private Long appointmentNum;

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