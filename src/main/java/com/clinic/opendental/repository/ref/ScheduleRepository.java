package com.clinic.opendental.repository.ref;

import com.clinic.opendental.model.ref.Schedule;
import com.clinic.opendental.model.ref.ScheduleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ScheduleRepository
        extends JpaRepository<Schedule, ScheduleId> {

    List<Schedule> findByIdClinicId(UUID clinicId);
}