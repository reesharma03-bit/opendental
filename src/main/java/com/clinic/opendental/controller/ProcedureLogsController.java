package com.clinic.opendental.controller;

import com.clinic.opendental.dto.procedurelog.*;
import com.clinic.opendental.service.ProcedureLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/procedurelogs")
@RequiredArgsConstructor
public class ProcedureLogsController {

    private final ProcedureLogService procedureLogService;

    // ========================================================================
    // GET Endpoints
    // ========================================================================

    /**
     * GET /procedurelogs/{procNum} - Get a single procedure log
     */
    @GetMapping("/{procNum}")
    public ResponseEntity<ProcedureLogResponse> getProcedureLog(
            @PathVariable Long procNum) {

        return ResponseEntity.ok(
                procedureLogService.getProcedureLog(procNum));
    }

    /**
     * GET /procedurelogs - Get multiple procedure logs with optional filters
     */
    @GetMapping
    public ResponseEntity<List<ProcedureLogResponse>> getProcedureLogs(
            @RequestParam Map<String, String> params) {

        return ResponseEntity.ok(
                procedureLogService.getProcedureLogs(params));
    }

    /**
     * GET /procedurelogs/InsuranceHistory - Get insurance history
     */
    @GetMapping("/InsuranceHistory")
    public ResponseEntity<List<InsuranceHistoryResponse>> getInsuranceHistory(
            @RequestParam Long PatNum,
            @RequestParam Long InsSubNum) {

        return ResponseEntity.ok(
                procedureLogService.getInsuranceHistory(PatNum, InsSubNum));
    }

    /**
     * GET /procedurelogs/GroupNotes - Get group notes for a patient
     */
    @GetMapping("/GroupNotes")
    public ResponseEntity<List<GroupNoteResponse>> getGroupNotes(
            @RequestParam Long PatNum) {

        return ResponseEntity.ok(
                procedureLogService.getGroupNotes(PatNum));
    }

    // ========================================================================
    // POST Endpoints
    // ========================================================================

    /**
     * POST /procedurelogs - Create a new procedure log
     */
    @PostMapping
    public ResponseEntity<ProcedureLogResponse> createProcedureLog(
            @Valid @RequestBody CreateProcedureLogRequest request) {

        ProcedureLogResponse response = procedureLogService.createProcedureLog(request);

        return ResponseEntity
                .created(Objects.requireNonNull(URI.create("/api/procedurelogs/" + response.getProcNum())))
                .body(response);
    }

    /**
     * POST /procedurelogs/GroupNote - Create a group note
     */
    @PostMapping("/GroupNote")
    public ResponseEntity<GroupNoteResponse> createGroupNote(
            @Valid @RequestBody GroupNoteRequest request) {

        GroupNoteResponse response = procedureLogService.createGroupNote(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    /**
     * POST /procedurelogs/InsuranceHistory - Create insurance history entry
     */
    @PostMapping("/InsuranceHistory")
    public ResponseEntity<ProcedureLogResponse> createInsuranceHistory(
            @Valid @RequestBody InsuranceHistoryRequest request) {

        ProcedureLogResponse response = procedureLogService.createInsuranceHistory(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // ========================================================================
    // PUT Endpoints
    // ========================================================================

    /**
     * PUT /procedurelogs/{procNum} - Update a procedure log
     */
    @PutMapping("/{procNum}")
    public ResponseEntity<ProcedureLogResponse> updateProcedureLog(
            @PathVariable Long procNum,
            @Valid @RequestBody UpdateProcedureLogRequest request) {

        return ResponseEntity.ok(
                procedureLogService.updateProcedureLog(procNum, request));
    }

    /**
     * PUT /procedurelogs/{procNum}/GroupNote - Update a group note
     */
    @PutMapping("/{procNum}/GroupNote")
    public ResponseEntity<GroupNoteResponse> updateGroupNote(
            @PathVariable Long procNum,
            @Valid @RequestBody UpdateGroupNoteRequest request) {

        return ResponseEntity.ok(
                procedureLogService.updateGroupNote(procNum, request));
    }

    // ========================================================================
    // DELETE Endpoints
    // ========================================================================

    /**
     * DELETE /procedurelogs/{procNum} - Delete a procedure log
     */
    @DeleteMapping("/{procNum}")
    public ResponseEntity<Void> deleteProcedureLog(
            @PathVariable Long procNum) {

        procedureLogService.deleteProcedureLog(procNum);
        return ResponseEntity.ok().build();
    }

    /**
     * DELETE /procedurelogs/{procNum}/GroupNote - Delete a group note
     */
    @DeleteMapping("/{procNum}/GroupNote")
    public ResponseEntity<Void> deleteGroupNote(
            @PathVariable Long procNum) {

        procedureLogService.deleteGroupNote(procNum);
        return ResponseEntity.ok().build();
    }
}