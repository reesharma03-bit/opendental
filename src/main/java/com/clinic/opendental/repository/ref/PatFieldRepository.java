package com.clinic.opendental.repository.ref;

import com.clinic.opendental.model.ref.PatField;
import com.clinic.opendental.model.ref.PatFieldId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PatFieldRepository
        extends JpaRepository<PatField, PatFieldId> {

    List<PatField> findByIdClinicId(UUID clinicId);
}