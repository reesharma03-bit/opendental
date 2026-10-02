package com.clinic.opendental.repository.ref;

import com.clinic.opendental.model.ref.Operatory;
import com.clinic.opendental.model.ref.OperatoryId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OperatoryRepository
        extends JpaRepository<Operatory, OperatoryId> {

    List<Operatory> findByIdClinicId(UUID clinicId);
}