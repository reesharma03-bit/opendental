package com.clinic.opendental.repository;

import com.clinic.opendental.model.ProcedureLog;
import com.clinic.opendental.model.ProcedureLogId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProcedureLogRepository extends JpaRepository<ProcedureLog, ProcedureLogId>, JpaSpecificationExecutor<ProcedureLog> {

    List<ProcedureLog> findByIdClinicIdAndIdProcNum(UUID clinicId, Long procNum);

    List<ProcedureLog> findByIdClinicId(UUID clinicId);

    List<ProcedureLog> findByAptNum(Long aptNum);

    List<ProcedureLog> findByProcStatus(String procStatus);

    List<ProcedureLog> findByClinicNum(Long clinicNum);

    List<ProcedureLog> findByProcCode(String procCode);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ProcedureLog p SET p.patNum = :realId WHERE p.id.clinicId = :clinicId AND p.patNum = :tempId")
    int movePatient(@Param("clinicId") UUID clinicId, @Param("tempId") Long tempId, @Param("realId") Long realId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ProcedureLog p SET p.aptNum = :realId WHERE p.id.clinicId = :clinicId AND p.aptNum = :tempId")
    int moveAppointment(@Param("clinicId") UUID clinicId, @Param("tempId") Long tempId, @Param("realId") Long realId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ProcedureLog p SET p.plannedAptNum = :realId WHERE p.id.clinicId = :clinicId AND p.plannedAptNum = :tempId")
    int movePlannedAppointment(@Param("clinicId") UUID clinicId, @Param("tempId") Long tempId, @Param("realId") Long realId);
}