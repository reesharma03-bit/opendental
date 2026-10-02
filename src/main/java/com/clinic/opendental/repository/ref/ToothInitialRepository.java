package com.clinic.opendental.repository.ref;

import com.clinic.opendental.model.ref.ToothInitial;
import com.clinic.opendental.model.ref.ToothInitialId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ToothInitialRepository
        extends JpaRepository<ToothInitial, ToothInitialId> {

    List<ToothInitial> findByIdClinicId(UUID clinicId);
}