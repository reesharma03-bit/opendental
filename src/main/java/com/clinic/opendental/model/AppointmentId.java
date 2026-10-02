package com.clinic.opendental.model;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentId implements Serializable {

    @Column(name = "clinic_id")
    private UUID clinicId;

    @Column(name = "apt_num")
    private Long aptNum;
}