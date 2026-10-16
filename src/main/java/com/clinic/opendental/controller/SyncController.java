package com.clinic.opendental.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.opendental.model.Appointment;
import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.model.Document;
import com.clinic.opendental.model.OdSyncTask;
import com.clinic.opendental.model.Patient;
import com.clinic.opendental.model.ProcedureLog;
import com.clinic.opendental.repository.ClinicRepository;
import com.clinic.opendental.security.AuditService;
import com.clinic.opendental.security.CurrentClinic;
import com.clinic.opendental.security.CurrentUser;
import com.clinic.opendental.security.Role;
import com.clinic.opendental.service.SyncService;
import com.clinic.opendental.service.Impl.FullSyncService;
import com.clinic.opendental.service.Impl.OdSyncService;
import com.clinic.opendental.service.Impl.SyncControl;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
public class SyncController {

    private final SyncService syncService;
    private final OdSyncService odSyncService;
    private final FullSyncService fullSyncService;
    private final SyncControl syncControl;
    private final ClinicRepository clinicRepository;
    private final AuditService audit;

    // ========================================================================
    // Pause / resume, and what each clinic syncs (the Sync settings screen)
    // ========================================================================

    /**
     * GET /api/sync/control
     * Whether sync is paused (by whom, since when, why) and whether a full sync is running.
     */
    @GetMapping("/control")
    public ResponseEntity<Map<String, Object>> control() {
        return ResponseEntity.ok(controlBody(syncControl.state()));
    }

    /**
     * POST /api/sync/control/pause  body: {"reason": "..."} (optional)
     * Stops reading from Open Dental and sending edits to it until resumed. A running
     * Force Sync stops after the resource it is on. Webhooks are still saved.
     */
    @PostMapping("/control/pause")
    public ResponseEntity<Map<String, Object>> pause(@RequestBody(required = false) Map<String, Object> body,
                                                     HttpServletRequest request) {
        String reason = body == null || body.get("reason") == null ? null : String.valueOf(body.get("reason"));
        if (reason != null && reason.length() > 500) reason = reason.substring(0, 500);
        SyncControl.State state = syncControl.pause(by(), reason);
        audit.record("SYNC_PAUSED", reason == null || reason.isBlank() ? null : "Reason: " + reason, request);
        return ResponseEntity.ok(controlBody(state));
    }

    /** POST /api/sync/control/resume  Sync picks up where it left off; queued edits go out. */
    @PostMapping("/control/resume")
    public ResponseEntity<Map<String, Object>> resume(HttpServletRequest request) {
        SyncControl.State state = syncControl.resume(by());
        audit.record("SYNC_RESUMED", null, request);
        return ResponseEntity.ok(controlBody(state));
    }

    /**
     * GET /api/sync/clinics
     * Every clinic with what it syncs: everything, or the chosen resources plus the ones
     * synced because a chosen one is read through them. Also lists what can be chosen.
     */
    @GetMapping("/clinics")
    public ResponseEntity<Map<String, Object>> clinicSelections() {
        // The current clinic only: a Dentist's own, or the clinic an Admin chose in the clinic bar.
        List<Map<String, Object>> clinics = new ArrayList<>();
        for (Clinic clinic : CurrentClinic.find(clinicRepository).stream().toList()) {
            Set<String> chosen = syncControl.chosen(clinic.getId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("clinicCode", clinic.getClinicCode());
            row.put("clinicName", clinic.getClinicName());
            row.put("active", Boolean.TRUE.equals(clinic.getIsActive()));
            row.put("everything", chosen == null);
            row.put("resources", chosen == null ? List.of() : List.copyOf(chosen));
            row.put("alsoSynced", chosen == null ? List.of() : SyncControl.addedForDependencies(chosen));
            clinics.add(row);
        }
        return ResponseEntity.ok(Map.of("syncable", SyncControl.SYNCABLE, "clinics", clinics));
    }

    /**
     * PUT /api/sync/clinics/{clinicCode}/resources
     * body: {"everything": true} or {"everything": false, "resources": ["patients", "claims", ...]}
     */
    @PutMapping("/clinics/{clinicCode}/resources")
    public ResponseEntity<Map<String, Object>> chooseResources(@PathVariable String clinicCode,
                                                               @RequestBody Map<String, Object> body,
                                                               HttpServletRequest request) {
        Clinic clinic = CurrentClinic.find(clinicRepository).orElse(null);
        if (clinic == null || !clinic.getClinicCode().equals(clinicCode)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You can only change what your clinic syncs."));
        }
        boolean everything = Boolean.TRUE.equals(body.get("everything"));
        Set<String> resources = null;
        if (!everything) {
            if (!(body.get("resources") instanceof List<?> list) || list.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error",
                        "Choose at least one screen to sync, or sync everything. To stop syncing, pause sync instead."));
            }
            resources = new LinkedHashSet<>();
            for (Object item : list) resources.add(String.valueOf(item));
        }
        try {
            syncControl.choose(clinic.getId(), resources, by());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        audit.record("SYNC_RESOURCES_CHANGED", "Clinic " + clinicCode + ": "
                + (resources == null ? "everything" : String.join(", ", resources)), request);
        return clinicSelections();
    }

    private static String by() {
        String email = CurrentUser.email();
        return email == null ? "unknown" : email;
    }

    private Map<String, Object> controlBody(SyncControl.State state) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("paused", state.paused());
        map.put("pausedBy", state.pausedBy());
        map.put("pausedAt", state.pausedAt() == null ? null : state.pausedAt().toInstant().toString());
        map.put("reason", state.reason());
        map.put("fullSyncRunning", fullSyncService.isRunning());
        return map;
    }

    // ========================================================================
    // Force Sync - everything in Open Dental, for every active clinic
    // ========================================================================

    /**
     * POST /api/sync/force
     * Starts a full copy of Open Dental into Supabase in the background: patients,
     * appointments, documents, procedure logs and the other typed tables, plus every
     * other Open Dental resource (into od_resource_records). Returns 202 with the run's
     * status; if a sync is already running, returns that one.
     */
    @PostMapping("/force")
    public ResponseEntity<Map<String, Object>> forceSync(@RequestParam(defaultValue = "false") boolean all) {
        // Every clinic at once: Admins only. Otherwise the current clinic (the user's own, or the
        // clinic an Admin chose in the clinic bar).
        if (all && CurrentUser.role() == Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(fullSyncService.startFullSync("force"));
        }
        Clinic clinic = CurrentClinic.require(clinicRepository);
        fullSyncService.startFullSync("force", clinic.getId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(fullSyncService.status(clinic));
    }

    /**
     * GET /api/sync/force/status
     * Progress of the running sync, or the result of the last one.
     */
    @GetMapping("/force/status")
    public ResponseEntity<Map<String, Object>> forceSyncStatus() {
        // Admins see the whole run; everyone else only their clinic's part of it.
        if (CurrentUser.role() == Role.ADMIN) return ResponseEntity.ok(fullSyncService.status());
        return ResponseEntity.ok(CurrentClinic.find(clinicRepository)
                .map(fullSyncService::status).orElse(Map.of("state", "idle")));
    }

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

    // ========================================================================
    // Open Dental push queue (changes saved here first, then sent to Open Dental)
    // ========================================================================

    /**
     * GET /api/sync/queue?status=PENDING,FAILED&limit=100
     * Changes still waiting for Open Dental (default: PENDING, IN_PROGRESS, FAILED).
     */
    @GetMapping("/queue")
    public ResponseEntity<List<OdSyncTask>> getQueue(
            @RequestParam(required = false) List<String> status,
            @RequestParam(defaultValue = "100") int limit) {

        List<String> statuses = status == null || status.isEmpty()
                ? List.of(OdSyncTask.PENDING, OdSyncTask.IN_PROGRESS, OdSyncTask.FAILED)
                : status.stream().map(String::toUpperCase).toList();
        return ResponseEntity.ok(odSyncService.list(statuses, Math.max(1, Math.min(limit, 500))));
    }

    /**
     * POST /api/sync/queue/{id}/retry
     * Sends a FAILED change to Open Dental again (e.g. after fixing the API key).
     */
    @PostMapping("/queue/{id}/retry")
    public ResponseEntity<Map<String, Object>> retryQueued(@PathVariable Long id) {
        OdSyncTask task = odSyncService.retry(id);
        return ResponseEntity.ok(Map.of(
                "id", task.getId(),
                "status", task.getStatus(),
                "lastError", task.getLastError() != null ? task.getLastError() : ""
        ));
    }
}
