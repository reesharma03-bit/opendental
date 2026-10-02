package com.clinic.opendental.model.ref;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "providers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Provider {

    @EmbeddedId
    private ProviderId id;

    @Column(name = "abbrev")
    private String abbrev;

    @Column(name = "f_name")
    private String fName;

    @Column(name = "l_name")
    private String lName;

    @Column(name = "suffix")
    private String suffix;

    @Column(name = "specialty")
    private String specialty;

    @Column(name = "prov_status")
    private String provStatus;

    @Column(name = "prov_type")
    private String provType;

    @Column(name = "clinic_num")
    private Long clinicNum;

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