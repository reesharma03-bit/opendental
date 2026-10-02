package com.clinic.opendental.controller;

import com.clinic.opendental.dto.patient.CreatePatientRequest;
import com.clinic.opendental.dto.patient.PatientResponse;
import com.clinic.opendental.dto.patient.PatientDirectoryResponse;
import com.clinic.opendental.dto.patient.PatientSimpleResponse;
import com.clinic.opendental.dto.patient.UpdatePatientRequest;
import com.clinic.opendental.service.PatientService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/patients")
public class ApiPatientsController {

    private final PatientService patientService;

    public ApiPatientsController(PatientService patientService) {
        this.patientService = patientService;
    }

    /**
     * Get Patients (multiple) - Uses Patient Select-style search logic
     */
    @GetMapping
    public ResponseEntity<List<PatientResponse>> getPatients(
            @RequestParam Map<String, String> params) {

        return ResponseEntity.ok(patientService.getPatients(params));
    }

    /**
     * Read-only, minimal patient directory projection directly from the configured database.
     */
    @GetMapping("/database")
    public ResponseEntity<List<PatientDirectoryResponse>> getPatientDirectoryFromDatabase(
            @RequestParam Map<String, String> params) {

        return ResponseEntity.ok(patientService.getPatientDirectoryFromDatabase(params));
    }

    /**
     * Get Simple Patients - Faster alternative
     */
    @GetMapping("/Simple")
    public ResponseEntity<List<PatientSimpleResponse>> getSimplePatients(
            @RequestParam Map<String, String> params) {

        return ResponseEntity.ok(patientService.getSimplePatients(params));
    }

    /**
     * Get Patient by PatNum
     */
    @GetMapping("/{patNum}")
    public ResponseEntity<PatientResponse> getPatient(
            @PathVariable Long patNum) {

        return ResponseEntity.ok(patientService.getPatient(patNum));
    }

    /**
     * Create Patient
     */
    @PostMapping
    public ResponseEntity<PatientResponse> createPatient(
            @Valid @RequestBody CreatePatientRequest request) {

        PatientResponse response = patientService.createPatient(request);

        return ResponseEntity
                .created(Objects.requireNonNull(URI.create("/api/patients/" + response.getPatNum())))
                .body(response);
    }

    /**
     * Update Patient
     */
    @PutMapping("/{patNum}")
    public ResponseEntity<PatientResponse> updatePatient(
            @PathVariable Long patNum,
            @Valid @RequestBody UpdatePatientRequest request) {

        return ResponseEntity.ok(
                patientService.updatePatient(patNum, request));
    }

    /**
     * Delete Patient
     *
     * Open Dental does not support deleting patients through the API.
     */
    @DeleteMapping("/{patNum}")
    public ResponseEntity<Map<String, String>> deletePatient(
            @PathVariable Long patNum) {

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Map.of(
                        "error",
                        "Patients cannot be deleted via the Open Dental API. Delete is only supported in Open Dental."
                ));
    }
}