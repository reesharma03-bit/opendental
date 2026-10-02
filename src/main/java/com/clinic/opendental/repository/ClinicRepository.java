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
}