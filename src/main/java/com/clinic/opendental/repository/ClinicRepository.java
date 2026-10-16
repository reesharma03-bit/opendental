package com.clinic.opendental.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.clinic.opendental.model.Clinic;

@Repository
public interface ClinicRepository extends JpaRepository<Clinic, UUID> {

    Optional<Clinic> findByClinicCode(String clinicCode);

    List<Clinic> findByIsActiveTrue();

    /** A dentist's clinics, oldest first. */
    List<Clinic> findByOwnerUserIdOrderByCreatedAtAsc(UUID ownerUserId);

    /** Clinic names are unique, whatever the case. */
    @org.springframework.data.jpa.repository.Query("SELECT c FROM Clinic c WHERE LOWER(TRIM(c.clinicName)) = LOWER(TRIM(:name))")
    List<Clinic> findByNameIgnoringCase(@org.springframework.data.repository.query.Param("name") String name);
}