package com.clinic.opendental.service;

import com.clinic.opendental.dto.procedurelog.*;

import java.util.List;
import java.util.Map;

public interface ProcedureLogService {

    // GET single
    ProcedureLogResponse getProcedureLog(Long procNum);

    // GET multiple
    List<ProcedureLogResponse> getProcedureLogs(Map<String, String> params);

    // GET InsuranceHistory
    List<InsuranceHistoryResponse> getInsuranceHistory(Long patNum, Long insSubNum);

    // GET GroupNotes
    List<GroupNoteResponse> getGroupNotes(Long patNum);

    // POST create
    ProcedureLogResponse createProcedureLog(CreateProcedureLogRequest request);

    // POST GroupNote
    GroupNoteResponse createGroupNote(GroupNoteRequest request);

    // POST InsuranceHistory
    ProcedureLogResponse createInsuranceHistory(InsuranceHistoryRequest request);

    // PUT update
    ProcedureLogResponse updateProcedureLog(Long procNum, UpdateProcedureLogRequest request);

    // PUT GroupNote
    GroupNoteResponse updateGroupNote(Long procNum, UpdateGroupNoteRequest request);

    // DELETE
    void deleteProcedureLog(Long procNum);

    // DELETE GroupNote
    void deleteGroupNote(Long procNum);
}