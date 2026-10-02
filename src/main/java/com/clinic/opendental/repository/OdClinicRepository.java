package com.clinic.opendental.repository;

import com.clinic.opendental.model.OdClinic;
import com.clinic.opendental.model.OdClinicId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OdClinicRepository extends JpaRepository<OdClinic, OdClinicId> {

    List<OdClinic> findByIdClinicId(UUID clinicId);
}
