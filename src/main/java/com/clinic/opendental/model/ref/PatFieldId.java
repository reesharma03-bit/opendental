package com.clinic.opendental.model.ref;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatFieldId implements Serializable {

    @Column(name = "clinic_id")
    private UUID clinicId;

    @Column(name = "pat_field_num")
    private Long patFieldNum;
}