package com.clinic.opendental.repository;

import com.clinic.opendental.model.Appointment;
import com.clinic.opendental.model.AppointmentId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, AppointmentId>, JpaSpecificationExecutor<Appointment> {

    List<Appointment> findByIdClinicIdAndIdAptNum(UUID clinicId, Long aptNum);

    List<Appointment> findByIdClinicId(UUID clinicId);

    List<Appointment> findByAptStatus(String aptStatus);

    List<Appointment> findByClinicNum(Long clinicNum);

    List<Appointment> findByAptDateTimeBetween(LocalDateTime start, LocalDateTime end);

    List<Appointment> findByDateTStampAfter(LocalDateTime dateTStamp);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Appointment a SET a.patNum = :realId WHERE a.id.clinicId = :clinicId AND a.patNum = :tempId")
    int movePatient(@Param("clinicId") UUID clinicId, @Param("tempId") Long tempId, @Param("realId") Long realId);
}