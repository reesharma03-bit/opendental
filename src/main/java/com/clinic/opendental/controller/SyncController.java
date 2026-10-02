package com.clinic.opendental.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.opendental.model.Appointment;
import com.clinic.opendental.model.Document;
import com.clinic.opendental.model.Patient;
import com.clinic.opendental.model.ProcedureLog;
import com.clinic.opendental.service.SyncService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
public class SyncController {

    private final SyncService syncService;

    // ========================================================================
    // Batch Sync - All Clinics
    // ========================================================================

    /**
     * POST /api/sync/patients/all
     * Fetch patients from ALL active clinics and save to Supabase.
     *
     * Optional query params are passed through to the Open Dental API:
     * LName, FName, PatNum, Birthdate, etc.
     */
    @PostMapping("/patients/all")
    public ResponseEntity<Map<String, Object>> syncAllClinics(
            @RequestParam Map<String, String> params) {

        SyncService.SyncResult result = syncService.syncAllClinics(params);
        return ResponseEntity.ok(Map.of(
                "synced", result.syncedCount(),
                "failed", result.failedCount(),
                "message", result.message()
        ));
    }

    // ========================================================================
    // Batch Sync - Single Clinic
    // ========================================================================

    /**
     * POST /api/sync/patients/clinic/{clinicCode}
     * Fetch patients from a specific clinic's Open Dental instance and save to Supabase.
     *
     * Optional query params are passed through to the Open Dental API:
     * LName, FName, PatNum, Birthdate, etc.
     *
     * Example: POST /api/sync/patients/clinic/CLINIC_A?LName=Smith
     */
    @PostMapping("/patients/clinic/{clinicCode}")
    public ResponseEntity<Map<String, Object>> syncClinic(
            @PathVariable String clinicCode,
            @RequestParam Map<String, String> params) {

        try {
            SyncService.SyncResult result = syncService.syncClinicByCode(clinicCode, params);
            return ResponseEntity.ok(Map.of(
                    "clinicCode", clinicCode,
                    "synced", result.syncedCount(),
                    "failed", result.failedCount(),
                    "message", result.message()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    // ========================================================================
    // Single Patient Sync
    // ========================================================================

    /**
     * POST /api/sync/patients/clinic/{clinicCode}/{patNum}
     * Fetch a single patient by PatNum from a specific clinic's Open Dental instance
     * and save it to Supabase.
     */
    @PostMapping("/patients/clinic/{clinicCode}/{patNum}")
    public ResponseEntity<Map<String, Object>> syncSinglePatient(
            @PathVariable String clinicCode,
            @PathVariable Long patNum) {

        try {
            SyncService.SyncResult result = syncService.syncSinglePatient(clinicCode, patNum);
            return ResponseEntity.ok(Map.of(
                    "clinicCode", clinicCode,
                    "patNum", patNum,
                    "synced", result.syncedCount(),
                    "failed", result.failedCount(),
                    "message", result.message()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    // ========================================================================
    // Query synced patients from Supabase (by clinic)
    // ========================================================================

    /**
     * GET /api/sync/patients/clinic/{clinicCode}
     * List patients currently stored in Supabase for a specific clinic.
     */
    @GetMapping("/patients/clinic/{clinicCode}")
    public ResponseEntity<?> getSyncedPatientsByClinic(
            @PathVariable String clinicCode) {

        try {
            List<Patient> patients = syncService.getPatientsByClinicCode(clinicCode);
            // We return the raw patient entities with their composite keys only.
            // Extend this if richer DTO output is needed.
            List<Map<String, Object>> result = patients.stream()
                    .map(p -> Map.<String, Object>of(
                            "clinicCode", clinicCode,
                            "patNum", p.getId().getPatNum(),
                            "lName", p.getLName(),
                            "fName", p.getFName(),
                            "patStatus", p.getPatStatus() != null ? p.getPatStatus() : "",
                            "city", p.getCity() != null ? p.getCity() : "",
                            "state", p.getState() != null ? p.getState() : ""
                    ))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "clinicCode", clinicCode,
                    "count", patients.size(),
                    "patients", result
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    // ========================================================================
    // Appointment Sync - All Clinics
    // ========================================================================

    /**
     * POST /api/sync/appointments/all
     * Fetch appointments from ALL active clinics and save to Supabase.
     *
     * Optional query params are passed through to the Open Dental API:
     * PatNum, AptStatus, DateStart, DateEnd, etc.
     */
    @PostMapping("/appointments/all")
    public ResponseEntity<Map<String, Object>> syncAllClinicsAppointments(
            @RequestParam Map<String, String> params) {

        SyncService.SyncResult result = syncService.syncAllClinicsAppointments(params);
        return ResponseEntity.ok(Map.of(
                "synced", result.syncedCount(),
                "failed", result.failedCount(),
                "message", result.message()
        ));
    }

    // ========================================================================
    // Appointment Sync - Single Clinic
    // ========================================================================

    /**
     * POST /api/sync/appointments/clinic/{clinicCode}
     * Fetch appointments from a specific clinic's Open Dental instance and save to Supabase.
     *
     * Optional query params are passed through to the Open Dental API:
     * PatNum, AptStatus, DateStart, DateEnd, etc.
     *
     * Example: POST /api/sync/appointments/clinic/CLINIC_A?PatNum=123
     */
    @PostMapping("/appointments/clinic/{clinicCode}")
    public ResponseEntity<Map<String, Object>> syncClinicAppointments(
            @PathVariable String clinicCode,
            @RequestParam Map<String, String> params) {

        try {
            SyncService.SyncResult result = syncService.syncAppointmentsByCode(clinicCode, params);
            return ResponseEntity.ok(Map.of(
                    "clinicCode", clinicCode,
                    "synced", result.syncedCount(),
                    "failed", result.failedCount(),
                    "message", result.message()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    // ========================================================================
    // Single Appointment Sync
    // ========================================================================

    /**
     * POST /api/sync/appointments/clinic/{clinicCode}/{aptNum}
     * Fetch a single appointment by AptNum from a specific clinic's Open Dental instance
     * and save it to Supabase.
     */
    @PostMapping("/appointments/clinic/{clinicCode}/{aptNum}")
    public ResponseEntity<Map<String, Object>> syncSingleAppointment(
            @PathVariable String clinicCode,
            @PathVariable Long aptNum) {

        try {
            SyncService.SyncResult result = syncService.syncSingleAppointment(clinicCode, aptNum);
            return ResponseEntity.ok(Map.of(
                    "clinicCode", clinicCode,
                    "aptNum", aptNum,
                    "synced", result.syncedCount(),
                    "failed", result.failedCount(),
                    "message", result.message()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    // ========================================================================
    // Query synced appointments from Supabase (by clinic)
    // ========================================================================

    /**
     * GET /api/sync/appointments/clinic/{clinicCode}
     * List appointments currently stored in Supabase for a specific clinic.
     */
    @GetMapping("/appointments/clinic/{clinicCode}")
    public ResponseEntity<?> getSyncedAppointmentsByClinic(
            @PathVariable String clinicCode) {

        try {
            List<Appointment> appointments = syncService.getAppointmentsByClinicCode(clinicCode);
            List<Map<String, Object>> result = appointments.stream()
                    .map(a -> Map.<String, Object>of(
                            "clinicCode", clinicCode,
                            "aptNum", a.getId().getAptNum(),
                            "aptStatus", a.getAptStatus() != null ? a.getAptStatus() : "",
                            "provNum", a.getProvNum() != null ? a.getProvNum() : 0,
                            "clinicNum", a.getClinicNum() != null ? a.getClinicNum() : 0
                    ))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "clinicCode", clinicCode,
                    "count", appointments.size(),
                    "appointments", result
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    // ========================================================================
    // Procedure Log Sync - All Clinics
    // ========================================================================

    /**
     * POST /api/sync/procedurelogs/all
     * Fetch procedure logs from ALL active clinics and save to Supabase.
     *
     * Optional query params are passed through to the Open Dental API:
     * PatNum, ProcStatus, DateStart, DateEnd, etc.
     */
    @PostMapping("/procedurelogs/all")
    public ResponseEntity<Map<String, Object>> syncAllClinicsProcedureLogs(
            @RequestParam Map<String, String> params) {

        SyncService.SyncResult result = syncService.syncAllClinicsProcedureLogs(params);
        return ResponseEntity.ok(Map.of(
                "synced", result.syncedCount(),
                "failed", result.failedCount(),
                "message", result.message()
        ));
    }

    // ========================================================================
    // Procedure Log Sync - Single Clinic
    // ========================================================================

    /**
     * POST /api/sync/procedurelogs/clinic/{clinicCode}
     * Fetch procedure logs from a specific clinic's Open Dental instance and save to Supabase.
     *
     * Optional query params are passed through to the Open Dental API:
     * PatNum, ProcStatus, DateStart, DateEnd, etc.
     *
     * Example: POST /api/sync/procedurelogs/clinic/CLINIC_A?PatNum=123
     */
    @PostMapping("/procedurelogs/clinic/{clinicCode}")
    public ResponseEntity<Map<String, Object>> syncClinicProcedureLogs(
            @PathVariable String clinicCode,
            @RequestParam Map<String, String> params) {

        try {
            SyncService.SyncResult result = syncService.syncProcedureLogsByCode(clinicCode, params);
            return ResponseEntity.ok(Map.of(
                    "clinicCode", clinicCode,
                    "synced", result.syncedCount(),
                    "failed", result.failedCount(),
                    "message", result.message()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    // ========================================================================
    // Single Procedure Log Sync
    // ========================================================================

    /**
     * POST /api/sync/procedurelogs/clinic/{clinicCode}/{procNum}
     * Fetch a single procedure log by ProcNum from a specific clinic's Open Dental instance
     * and save it to Supabase.
     */
    @PostMapping("/procedurelogs/clinic/{clinicCode}/{procNum}")
    public ResponseEntity<Map<String, Object>> syncSingleProcedureLog(
            @PathVariable String clinicCode,
            @PathVariable Long procNum) {

        try {
            SyncService.SyncResult result = syncService.syncSingleProcedureLog(clinicCode, procNum);
            return ResponseEntity.ok(Map.of(
                    "clinicCode", clinicCode,
                    "procNum", procNum,
                    "synced", result.syncedCount(),
                    "failed", result.failedCount(),
                    "message", result.message()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    // ========================================================================
    // Query synced procedure logs from Supabase (by clinic)
    // ========================================================================

    /**
     * GET /api/sync/procedurelogs/clinic/{clinicCode}
     * List procedure logs currently stored in Supabase for a specific clinic.
     */
    @GetMapping("/procedurelogs/clinic/{clinicCode}")
    public ResponseEntity<?> getSyncedProcedureLogsByClinic(
            @PathVariable String clinicCode) {

        try {
            List<ProcedureLog> procedureLogs = syncService.getProcedureLogsByClinicCode(clinicCode);
            List<Map<String, Object>> result = procedureLogs.stream()
                    .map(p -> Map.<String, Object>of(
                            "clinicCode", clinicCode,
                            "procNum", p.getId().getProcNum(),
                            "procStatus", p.getProcStatus() != null ? p.getProcStatus() : "",
                            "procCode", p.getProcCode() != null ? p.getProcCode() : "",
                            "descript", p.getDescript() != null ? p.getDescript() : ""
                    ))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "clinicCode", clinicCode,
                    "count", procedureLogs.size(),
                    "procedureLogs", result
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    // ========================================================================
    // Document Sync - All Clinics
    // ========================================================================

    /**
     * POST /api/sync/documents/all
     * Fetch documents from ALL active clinics and save to Supabase.
     *
     * Optional query params are passed through to the Open Dental API:
     * PatNum, DocCategory, etc.
     */
    @PostMapping("/documents/all")
    public ResponseEntity<Map<String, Object>> syncAllClinicsDocuments(
            @RequestParam Map<String, String> params) {

        SyncService.SyncResult result = syncService.syncAllClinicsDocuments(params);
        return ResponseEntity.ok(Map.of(
                "synced", result.syncedCount(),
                "failed", result.failedCount(),
                "message", result.message()
        ));
    }

    // ========================================================================
    // Document Sync - Single Clinic
    // ========================================================================

    /**
     * POST /api/sync/documents/clinic/{clinicCode}
     * Fetch documents from a specific clinic's Open Dental instance and save to Supabase.
     *
     * Optional query params are passed through to the Open Dental API:
     * PatNum, DocCategory, etc.
     *
     * Example: POST /api/sync/documents/clinic/CLINIC_A?PatNum=123
     */
    @PostMapping("/documents/clinic/{clinicCode}")
    public ResponseEntity<Map<String, Object>> syncClinicDocuments(
            @PathVariable String clinicCode,
            @RequestParam Map<String, String> params) {

        try {
            SyncService.SyncResult result = syncService.syncDocumentsByCode(clinicCode, params);
            return ResponseEntity.ok(Map.of(
                    "clinicCode", clinicCode,
                    "synced", result.syncedCount(),
                    "failed", result.failedCount(),
                    "message", result.message()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    // ========================================================================
    // Single Document Sync
    // ========================================================================

    /**
     * POST /api/sync/documents/clinic/{clinicCode}/{docNum}
     * Fetch a single document by DocNum from a specific clinic's Open Dental instance
     * and save it to Supabase.
     */
    @PostMapping("/documents/clinic/{clinicCode}/{docNum}")
    public ResponseEntity<Map<String, Object>> syncSingleDocument(
            @PathVariable String clinicCode,
            @PathVariable Long docNum) {

        try {
            SyncService.SyncResult result = syncService.syncSingleDocument(clinicCode, docNum);
            return ResponseEntity.ok(Map.of(
                    "clinicCode", clinicCode,
                    "docNum", docNum,
                    "synced", result.syncedCount(),
                    "failed", result.failedCount(),
                    "message", result.message()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    // ========================================================================
    // Query synced documents from Supabase (by clinic)
    // ========================================================================

    /**
     * GET /api/sync/documents/clinic/{clinicCode}
     * List documents currently stored in Supabase for a specific clinic.
     */
    @GetMapping("/documents/clinic/{clinicCode}")
    public ResponseEntity<?> getSyncedDocumentsByClinic(
            @PathVariable String clinicCode) {

        try {
            List<Document> documents = syncService.getDocumentsByClinicCode(clinicCode);
            List<Map<String, Object>> result = documents.stream()
                    .map(d -> Map.<String, Object>of(
                            "clinicCode", clinicCode,
                            "docNum", d.getId().getDocNum(),
                            "description", d.getDescription() != null ? d.getDescription() : "",
                            "fileName", d.getFileName() != null ? d.getFileName() : "",
                            "imgType", d.getImgType() != null ? d.getImgType() : ""
                    ))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "clinicCode", clinicCode,
                    "count", documents.size(),
                    "documents", result
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }
}
