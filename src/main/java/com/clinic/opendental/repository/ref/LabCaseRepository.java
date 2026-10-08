package com.clinic.opendental.repository.ref;

import com.clinic.opendental.model.ref.LabCase;
import com.clinic.opendental.model.ref.LabCaseId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LabCaseRepository extends JpaRepository<LabCase, LabCaseId> {
    List<LabCase> findByIdClinicId(UUID clinicId);
}
