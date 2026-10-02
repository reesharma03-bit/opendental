package com.clinic.opendental.model;

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
public class DocumentId implements Serializable {

    @Column(name = "clinic_id")
    private UUID clinicId;

    @Column(name = "doc_num")
    private Long docNum;
}