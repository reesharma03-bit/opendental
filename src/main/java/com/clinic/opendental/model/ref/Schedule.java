package com.clinic.opendental.model.ref;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "schedules")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Schedule {

    @EmbeddedId
    private ScheduleId id;

    @Column(name = "sched_date")
    private LocalDate schedDate;

    @Column(name = "sched_type_num")
    private Long schedTypeNum;

    @Column(name = "prov_num")
    private Long provNum;

    @Column(name = "clinic_num")
    private Long clinicNum;

    @Column(name = "start_time")
    private String startTime;

    @Column(name = "stop_time")
    private String stopTime;

    @Column(name = "blockout")
    private String blockout;

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