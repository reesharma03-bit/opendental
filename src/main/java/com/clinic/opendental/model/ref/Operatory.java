package com.clinic.opendental.model.ref;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "operatories")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Operatory {

    @EmbeddedId
    private OperatoryId id;

    @Column(name = "abbrev")
    private String abbrev;

    @Column(name = "description")
    private String description;

    @Column(name = "clinic_num")
    private Long clinicNum;

    @Column(name = "is_hygiene")
    private String isHygiene;

    @Column(name = "is_disabled")
    private String isDisabled;

    @Column(name = "is_web_sched")
    private String isWebSched;

    @Column(name = "order_value")
    private Long orderValue;

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