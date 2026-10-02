package com.clinic.opendental.repository;

import com.clinic.opendental.model.ProcedureLog;
import com.clinic.opendental.model.ProcedureLogId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
}