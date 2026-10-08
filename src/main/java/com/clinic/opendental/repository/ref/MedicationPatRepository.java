package com.clinic.opendental.repository.ref;

import com.clinic.opendental.model.ref.MedicationPat;
import com.clinic.opendental.model.ref.MedicationPatId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MedicationPatRepository extends JpaRepository<MedicationPat, MedicationPatId> {
    List<MedicationPat> findByIdClinicId(UUID clinicId);
}
