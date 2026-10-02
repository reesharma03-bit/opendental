package com.clinic.opendental.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.clinic.opendental.model.Patient;
import com.clinic.opendental.model.PatientId;

@Repository
public interface PatientRepository extends JpaRepository<Patient, PatientId>, JpaSpecificationExecutor<Patient> {

    @Query("SELECT p FROM Patient p WHERE LOWER(p.lName) LIKE LOWER(CONCAT('%', :lName, '%'))")
    List<Patient> findByLNameContainingIgnoreCase(@Param("lName") String lName);

    @Query("SELECT p FROM Patient p WHERE LOWER(p.fName) LIKE LOWER(CONCAT('%', :fName, '%'))")
    List<Patient> findByFNameContainingIgnoreCase(@Param("fName") String fName);

    @Query("SELECT p FROM Patient p WHERE LOWER(p.lName) LIKE LOWER(CONCAT('%', :lName, '%')) AND LOWER(p.fName) LIKE LOWER(CONCAT('%', :fName, '%'))")
    List<Patient> findByLNameContainingIgnoreCaseAndFNameContainingIgnoreCase(
            @Param("lName") String lName, @Param("fName") String fName);

    List<Patient> findByBirthdate(LocalDate birthdate);

    List<Patient> findByPatStatus(String patStatus);

    List<Patient> findByGuarantor(Long guarantor);

    List<Patient> findByClinicNum(Long clinicNum);

    List<Patient> findByIdClinicId(UUID clinicId);

    List<Patient> findByIdClinicIdAndIdPatNum(UUID clinicId, Long patNum);
}