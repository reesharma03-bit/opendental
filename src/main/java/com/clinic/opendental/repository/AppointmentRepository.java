package com.clinic.opendental.repository;

import com.clinic.opendental.model.Appointment;
import com.clinic.opendental.model.AppointmentId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
}