package com.clinic.opendental.model.ref;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tooth_initials")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ToothInitial {

    @EmbeddedId
    private ToothInitialId id;

    @Column(name = "pat_num")
    private Long patNum;

    @Column(name = "tooth_num")
    private String toothNum;

    @Column(name = "tooth_type")
    private String toothType;

    @Column(name = "tooth_group")
    private String toothGroup;

    @Column(name = "mobility")
    private String mobility;

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