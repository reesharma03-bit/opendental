package com.clinic.opendental.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.opendental.client.OpenDentalClient;
import com.clinic.opendental.dto.appointment.AppointmentResponse;
import com.clinic.opendental.dto.appointmentdeleted.AppointmentDeletedResponse;
import com.clinic.opendental.dto.document.DocumentResponse;
import com.clinic.opendental.dto.operatory.OperatoryResponse;
import com.clinic.opendental.dto.patfield.PatFieldResponse;
import com.clinic.opendental.dto.patfielddeleted.PatFieldDeletedResponse;
import com.clinic.opendental.dto.patient.PatientResponse;
import com.clinic.opendental.dto.provider.ProviderResponse;
import com.clinic.opendental.dto.procedurelog.ProcedureLogResponse;
import com.clinic.opendental.dto.schedule.ScheduleResponse;
import com.clinic.opendental.dto.scheduledeleted.ScheduleDeletedResponse;
import com.clinic.opendental.dto.toothinitial.ToothInitialResponse;
import com.clinic.opendental.dto.toothinitialdeleted.ToothInitialDeletedResponse;
import com.clinic.opendental.model.Appointment;
import com.clinic.opendental.model.AppointmentId;
import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.model.Document;
import com.clinic.opendental.model.DocumentId;
import com.clinic.opendental.model.Patient;
import com.clinic.opendental.model.PatientId;
import com.clinic.opendental.model.ProcedureLog;
import com.clinic.opendental.model.ProcedureLogId;
import com.clinic.opendental.model.ref.Operatory;
import com.clinic.opendental.model.ref.OperatoryId;
import com.clinic.opendental.model.ref.PatField;
import com.clinic.opendental.model.ref.PatFieldId;
import com.clinic.opendental.model.ref.Provider;
import com.clinic.opendental.model.ref.ProviderId;
import com.clinic.opendental.model.ref.Schedule;
import com.clinic.opendental.model.ref.ScheduleId;
import com.clinic.opendental.model.ref.ToothInitial;
import com.clinic.opendental.model.ref.ToothInitialId;
import com.clinic.opendental.repository.AppointmentRepository;
import com.clinic.opendental.repository.ClinicRepository;
import com.clinic.opendental.repository.OdSyncTaskRepository;
import com.clinic.opendental.repository.DocumentRepository;
import com.clinic.opendental.repository.PatientRepository;
import com.clinic.opendental.repository.ProcedureLogRepository;
import com.clinic.opendental.repository.ref.OperatoryRepository;
import com.clinic.opendental.repository.ref.PatFieldRepository;
import com.clinic.opendental.repository.ref.ProviderRepository;
import com.clinic.opendental.repository.ref.ScheduleRepository;
import com.clinic.opendental.repository.ref.ToothInitialRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyncService {

    private final ClinicRepository clinicRepository;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final ProcedureLogRepository procedureLogRepository;
    private final DocumentRepository documentRepository;
    private final OperatoryRepository operatoryRepository;
    private final PatFieldRepository patFieldRepository;
    private final ProviderRepository providerRepository;
    private final ScheduleRepository scheduleRepository;
        private final ToothInitialRepository toothInitialRepository;
    private final OpenDentalClient client;

    /** Optional so code that builds this service by hand need not supply it. */
    @Autowired(required = false)
    private OdSyncTaskRepository odSyncTaskRepository;


    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ========================================================================
    // Batch sync methods - Patients
    // ========================================================================

    @Transactional
    public SyncResult syncAllClinics() {
        return syncAllClinics(Map.of());
    }

    @Transactional
    public SyncResult syncAllClinics(Map<String, String> params) {
        List<Clinic> clinics = clinicRepository.findByIsActiveTrue();
        if (clinics.isEmpty()) {
            log.warn("No active clinics configured. Sync aborted.");
            return SyncResult.failure("No active clinics configured");
        }

        int totalSynced = 0;
        int totalFailed = 0;

        for (Clinic clinic : clinics) {
            try {
                SyncResult result = syncClinicPatients(clinic, params);
                totalSynced += result.syncedCount();
                totalFailed += result.failedCount();
                log.info("Clinic {} sync complete: {} synced, {} failed",
                        clinic.getClinicCode(), result.syncedCount(), result.failedCount());
            } catch (Exception e) {
                totalFailed++;
                log.error("Failed to sync clinic {}: {}", clinic.getClinicCode(), e.getMessage());
            }
        }

        return new SyncResult(totalSynced, totalFailed, "Sync completed for " + clinics.size() + " clinics");
    }

    @Transactional
    public SyncResult syncClinicPatients(Clinic clinic) {
        return syncClinicPatients(clinic, Map.of());
    }

    @Transactional
    public SyncResult syncClinicPatients(Clinic clinic, Map<String, String> params) {
        try {
            List<PatientResponse> apiPatients = client.getPatients(params, clinic.getBaseUrl());

            int synced = 0;
            int failed = 0;

            for (PatientResponse dto : apiPatients) {
                try {
                    savePatientToDb(dto, clinic.getId());
                    synced++;
                } catch (Exception e) {
                    failed++;
                    log.error("Failed to sync patient {} from clinic {}: {}",
                            dto.getPatNum(), clinic.getClinicCode(), e.getMessage());
                }
            }

            return new SyncResult(synced, failed,
                    "Synced " + synced + " patients from clinic " + clinic.getClinicCode());
        } catch (Exception e) {
            log.error("Failed to fetch patients from clinic {} at {}: {}",
                    clinic.getClinicCode(), clinic.getBaseUrl(), e.getMessage());
            return SyncResult.failure("Failed to fetch from clinic " + clinic.getClinicCode() + ": " + e.getMessage());
        }
    }

    @Transactional
    public SyncResult syncClinicByCode(String clinicCode) {
        return syncClinicByCode(clinicCode, Map.of());
    }

    @Transactional
    public SyncResult syncClinicByCode(String clinicCode, Map<String, String> params) {
        Clinic clinic = clinicRepository.findByClinicCode(clinicCode)
                .orElseThrow(() -> new IllegalArgumentException("Clinic not found: " + clinicCode));
        return syncClinicPatients(clinic, params);
    }

    // ========================================================================
    // Single patient sync methods
    // ========================================================================

    @Transactional
    public SyncResult syncSinglePatient(String clinicCode, Long patNum) {
        Clinic clinic = clinicRepository.findByClinicCode(clinicCode)
                .orElseThrow(() -> new IllegalArgumentException("Clinic not found: " + clinicCode));

        try {
            PatientResponse patient = client.getPatient(patNum, clinic.getBaseUrl());
            savePatientToDb(patient, clinic.getId());
            return new SyncResult(1, 0,
                    "Synced patient " + patNum + " from clinic " + clinicCode);
        } catch (Exception e) {
            log.error("Failed to fetch patient {} from clinic {} at {}: {}",
                    patNum, clinic.getClinicCode(), clinic.getBaseUrl(), e.getMessage());
            return SyncResult.failure("Failed to fetch patient " + patNum + " from clinic " + clinicCode + ": " + e.getMessage());
        }
    }

    // ========================================================================
    // Batch sync methods - Appointments
    // ========================================================================

    @Transactional
    public SyncResult syncAllClinicsAppointments(Map<String, String> params) {
        List<Clinic> clinics = clinicRepository.findByIsActiveTrue();
        if (clinics.isEmpty()) {
            log.warn("No active clinics configured. Appointment sync aborted.");
            return SyncResult.failure("No active clinics configured");
        }

        int totalSynced = 0;
        int totalFailed = 0;

        for (Clinic clinic : clinics) {
            try {
                SyncResult result = syncClinicAppointments(clinic, params);
                totalSynced += result.syncedCount();
                totalFailed += result.failedCount();
                log.info("Clinic {} appointment sync complete: {} synced, {} failed",
                        clinic.getClinicCode(), result.syncedCount(), result.failedCount());
            } catch (Exception e) {
                totalFailed++;
                log.error("Failed to sync appointments for clinic {}: {}", clinic.getClinicCode(), e.getMessage());
            }
        }

        return new SyncResult(totalSynced, totalFailed,
                "Appointment sync completed for " + clinics.size() + " clinics");
    }

    @Transactional
    public SyncResult syncClinicAppointments(Clinic clinic, Map<String, String> params) {
        try {
            List<AppointmentResponse> apiAppointments = client.getAppointments(params, clinic.getBaseUrl());

            int synced = 0;
            int failed = 0;

            for (AppointmentResponse dto : apiAppointments) {
                try {
                    saveAppointmentToDb(dto, clinic.getId());
                    synced++;
                } catch (Exception e) {
                    failed++;
                    log.error("Failed to sync appointment {} from clinic {}: {}",
                            dto.getAptNum(), clinic.getClinicCode(), e.getMessage());
                }
            }

            return new SyncResult(synced, failed,
                    "Synced " + synced + " appointments from clinic " + clinic.getClinicCode());
        } catch (Exception e) {
            log.error("Failed to fetch appointments from clinic {} at {}: {}",
                    clinic.getClinicCode(), clinic.getBaseUrl(), e.getMessage());
            return SyncResult.failure("Failed to fetch from clinic " + clinic.getClinicCode() + ": " + e.getMessage());
        }
    }

    @Transactional
    public SyncResult syncAppointmentsByCode(String clinicCode, Map<String, String> params) {
        Clinic clinic = clinicRepository.findByClinicCode(clinicCode)
                .orElseThrow(() -> new IllegalArgumentException("Clinic not found: " + clinicCode));
        return syncClinicAppointments(clinic, params);
    }

    // ========================================================================
    // Single appointment sync methods
    // ========================================================================

    @Transactional
    public SyncResult syncSingleAppointment(String clinicCode, Long aptNum) {
        Clinic clinic = clinicRepository.findByClinicCode(clinicCode)
                .orElseThrow(() -> new IllegalArgumentException("Clinic not found: " + clinicCode));

        try {
            AppointmentResponse appointment = client.getAppointment(aptNum, clinic.getBaseUrl());
            saveAppointmentToDb(appointment, clinic.getId());
            return new SyncResult(1, 0,
                    "Synced appointment " + aptNum + " from clinic " + clinicCode);
        } catch (Exception e) {
            log.error("Failed to fetch appointment {} from clinic {} at {}: {}",
                    aptNum, clinic.getClinicCode(), clinic.getBaseUrl(), e.getMessage());
            return SyncResult.failure("Failed to fetch appointment " + aptNum + " from clinic " + clinicCode + ": " + e.getMessage());
        }
    }

    // ========================================================================
    // Batch sync methods - Procedure Logs
    // ========================================================================

    @Transactional
    public SyncResult syncAllClinicsProcedureLogs(Map<String, String> params) {
        List<Clinic> clinics = clinicRepository.findByIsActiveTrue();
        if (clinics.isEmpty()) {
            log.warn("No active clinics configured. Procedure log sync aborted.");
            return SyncResult.failure("No active clinics configured");
        }

        int totalSynced = 0;
        int totalFailed = 0;

        for (Clinic clinic : clinics) {
            try {
                SyncResult result = syncClinicProcedureLogs(clinic, params);
                totalSynced += result.syncedCount();
                totalFailed += result.failedCount();
                log.info("Clinic {} procedure log sync complete: {} synced, {} failed",
                        clinic.getClinicCode(), result.syncedCount(), result.failedCount());
            } catch (Exception e) {
                totalFailed++;
                log.error("Failed to sync procedure logs for clinic {}: {}", clinic.getClinicCode(), e.getMessage());
            }
        }

        return new SyncResult(totalSynced, totalFailed,
                "Procedure log sync completed for " + clinics.size() + " clinics");
    }

    @Transactional
    public SyncResult syncClinicProcedureLogs(Clinic clinic, Map<String, String> params) {
        try {
            List<ProcedureLogResponse> apiProcedureLogs = client.getProcedureLogs(params, clinic.getBaseUrl());

            int synced = 0;
            int failed = 0;

            for (ProcedureLogResponse dto : apiProcedureLogs) {
                try {
                    saveProcedureLogToDb(dto, clinic.getId());
                    synced++;
                } catch (Exception e) {
                    failed++;
                    log.error("Failed to sync procedure log {} from clinic {}: {}",
                            dto.getProcNum(), clinic.getClinicCode(), e.getMessage());
                }
            }

            return new SyncResult(synced, failed,
                    "Synced " + synced + " procedure logs from clinic " + clinic.getClinicCode());
        } catch (Exception e) {
            log.error("Failed to fetch procedure logs from clinic {} at {}: {}",
                    clinic.getClinicCode(), clinic.getBaseUrl(), e.getMessage());
            return SyncResult.failure("Failed to fetch from clinic " + clinic.getClinicCode() + ": " + e.getMessage());
        }
    }

    @Transactional
    public SyncResult syncProcedureLogsByCode(String clinicCode, Map<String, String> params) {
        Clinic clinic = clinicRepository.findByClinicCode(clinicCode)
                .orElseThrow(() -> new IllegalArgumentException("Clinic not found: " + clinicCode));
        return syncClinicProcedureLogs(clinic, params);
    }

    // ========================================================================
    // Single procedure log sync methods
    // ========================================================================

    @Transactional
    public SyncResult syncSingleProcedureLog(String clinicCode, Long procNum) {
        Clinic clinic = clinicRepository.findByClinicCode(clinicCode)
                .orElseThrow(() -> new IllegalArgumentException("Clinic not found: " + clinicCode));

        try {
            ProcedureLogResponse procedureLog = client.getProcedureLog(procNum, clinic.getBaseUrl());
            saveProcedureLogToDb(procedureLog, clinic.getId());
            return new SyncResult(1, 0,
                    "Synced procedure log " + procNum + " from clinic " + clinicCode);
        } catch (Exception e) {
            log.error("Failed to fetch procedure log {} from clinic {} at {}: {}",
                    procNum, clinic.getClinicCode(), clinic.getBaseUrl(), e.getMessage());
            return SyncResult.failure("Failed to fetch procedure log " + procNum + " from clinic " + clinicCode + ": " + e.getMessage());
        }
    }

    // ========================================================================
    // Batch sync methods - Documents
    // ========================================================================

    @Transactional
    public SyncResult syncAllClinicsDocuments(Map<String, String> params) {
        List<Clinic> clinics = clinicRepository.findByIsActiveTrue();
        if (clinics.isEmpty()) {
            log.warn("No active clinics configured. Document sync aborted.");
            return SyncResult.failure("No active clinics configured");
        }

        int totalSynced = 0;
        int totalFailed = 0;

        for (Clinic clinic : clinics) {
            try {
                SyncResult result = syncClinicDocuments(clinic, params);
                totalSynced += result.syncedCount();
                totalFailed += result.failedCount();
                log.info("Clinic {} document sync complete: {} synced, {} failed",
                        clinic.getClinicCode(), result.syncedCount(), result.failedCount());
            } catch (Exception e) {
                totalFailed++;
                log.error("Failed to sync documents for clinic {}: {}", clinic.getClinicCode(), e.getMessage());
            }
        }

        return new SyncResult(totalSynced, totalFailed,
                "Document sync completed for " + clinics.size() + " clinics");
    }

    @Transactional
    public SyncResult syncClinicDocuments(Clinic clinic, Map<String, String> params) {
        try {
            List<DocumentResponse> apiDocuments = client.getDocuments(params, clinic.getBaseUrl());

            int synced = 0;
            int failed = 0;

            for (DocumentResponse dto : apiDocuments) {
                try {
                    saveDocumentToDb(dto, clinic.getId());
                    synced++;
                } catch (Exception e) {
                    failed++;
                    log.error("Failed to sync document {} from clinic {}: {}",
                            dto.getDocNum(), clinic.getClinicCode(), e.getMessage());
                }
            }

            return new SyncResult(synced, failed,
                    "Synced " + synced + " documents from clinic " + clinic.getClinicCode());
        } catch (Exception e) {
            log.error("Failed to fetch documents from clinic {} at {}: {}",
                    clinic.getClinicCode(), clinic.getBaseUrl(), e.getMessage());
            return SyncResult.failure("Failed to fetch from clinic " + clinic.getClinicCode() + ": " + e.getMessage());
        }
    }

    @Transactional
    public SyncResult syncDocumentsByCode(String clinicCode, Map<String, String> params) {
        Clinic clinic = clinicRepository.findByClinicCode(clinicCode)
                .orElseThrow(() -> new IllegalArgumentException("Clinic not found: " + clinicCode));
        return syncClinicDocuments(clinic, params);
    }

    // ========================================================================
    // Single document sync methods
    // ========================================================================

    @Transactional
    public SyncResult syncSingleDocument(String clinicCode, Long docNum) {
        Clinic clinic = clinicRepository.findByClinicCode(clinicCode)
                .orElseThrow(() -> new IllegalArgumentException("Clinic not found: " + clinicCode));

        try {
            DocumentResponse document = client.getDocument(docNum, clinic.getBaseUrl());
            saveDocumentToDb(document, clinic.getId());
            return new SyncResult(1, 0,
                    "Synced document " + docNum + " from clinic " + clinicCode);
        } catch (Exception e) {
            log.error("Failed to fetch document {} from clinic {} at {}: {}",
                    docNum, clinic.getClinicCode(), clinic.getBaseUrl(), e.getMessage());
            return SyncResult.failure("Failed to fetch document " + docNum + " from clinic " + clinicCode + ": " + e.getMessage());
        }
    }

    // ========================================================================
    // Direct-save methods (from webhook payloads - no API fetch)
    // ========================================================================

    /**
     * Save a patient DTO received directly from a webhook payload (no API fetch).
     */
    @Transactional
    public SyncResult savePatientFromDto(PatientResponse dto, String clinicCode) {
        if (dto.getPatNum() == null) {
            log.warn("Skipping patient record with null PatNum from webhook");
            return SyncResult.failure("Patient record has null PatNum - skipped");
        }
        
        Clinic clinic = clinicRepository.findByClinicCode(clinicCode)
                .orElseThrow(() -> new IllegalArgumentException("Clinic not found: " + clinicCode));
        try {
            if (hasQueuedChanges(clinic.getId(), "patient", dto.getPatNum())) {
                return new SyncResult(0, 0, "Skipped patient " + dto.getPatNum()
                        + ": a newer local change is still waiting to reach Open Dental");
            }
            savePatientToDb(dto, clinic.getId());
            return new SyncResult(1, 0,
                    "Saved patient " + dto.getPatNum() + " from clinic " + clinicCode);
        } catch (Exception e) {
            log.error("Failed to save patient {} from webhook: {}", dto.getPatNum(), e.getMessage());
            return SyncResult.failure("Failed to save patient " + dto.getPatNum() + ": " + e.getMessage());
        }
    }

    /**
     * Save an appointment DTO received directly from a webhook payload (no API fetch).
     */
    @Transactional
    public SyncResult saveAppointmentFromDto(AppointmentResponse dto, String clinicCode) {
        Clinic clinic = clinicRepository.findByClinicCode(clinicCode)
                .orElseThrow(() -> new IllegalArgumentException("Clinic not found: " + clinicCode));
        try {
            if (hasQueuedChanges(clinic.getId(), "appointment", dto.getAptNum())) {
                return new SyncResult(0, 0, "Skipped appointment " + dto.getAptNum()
                        + ": a newer local change is still waiting to reach Open Dental");
            }
            ensurePatientExists(dto.getPatNum(), clinic);
            saveAppointmentToDb(dto, clinic.getId());
            return new SyncResult(1, 0,
                    "Saved appointment " + dto.getAptNum() + " from clinic " + clinicCode);
        } catch (Exception e) {
            log.error("Failed to save appointment {} from webhook: {}", dto.getAptNum(), e.getMessage());
            return SyncResult.failure("Failed to save appointment " + dto.getAptNum() + ": " + e.getMessage());
        }
    }

    /** A local change to this record has not reached Open Dental yet (see OdSyncService). */
    private boolean hasQueuedChanges(UUID clinicId, String entityType, Long id) {
        return odSyncTaskRepository != null && id != null
                && odSyncTaskRepository.existsForRecord(clinicId, entityType, id, OdSyncTaskRepository.OPEN);
    }

    /**
     * Appointments reference patients via a NOT NULL foreign key
     * (clinic_id, pat_num). A webhook for an appointment can arrive before the
     * corresponding patient has been synced, so ensure the patient row exists;
     * if missing, fetch it from Open Dental and persist it first.
     */
    private void ensurePatientExists(Long patNum, Clinic clinic) {
        if (patNum == null) {
            throw new IllegalArgumentException(
                    "Appointment references null PatNum; cannot satisfy patients foreign key");
        }
        boolean exists = !patientRepository
                .findByIdClinicIdAndIdPatNum(clinic.getId(), patNum).isEmpty();
        if (exists) {
            return;
        }
        log.info("Patient {} not present for clinic {}; fetching from Open Dental before saving appointment",
                patNum, clinic.getClinicCode());
        PatientResponse patient = client.getPatient(patNum, clinic.getBaseUrl());
        if (patient == null || patient.getPatNum() == null) {
            throw new IllegalArgumentException(
                    "Patient " + patNum + " was not found in Open Dental for clinic " + clinic.getClinicCode());
        }
        savePatientToDb(patient, clinic.getId());
        log.info("Patient {} synced to satisfy appointment reference", patNum);
    }

    /**
     * Save a procedure log DTO received directly from a webhook payload (no API fetch).
     */
    @Transactional
    public SyncResult saveProcedureLogFromDto(ProcedureLogResponse dto, String clinicCode) {
        Clinic clinic = clinicRepository.findByClinicCode(clinicCode)
                .orElseThrow(() -> new IllegalArgumentException("Clinic not found: " + clinicCode));
        try {
            if (hasQueuedChanges(clinic.getId(), "procedurelog", dto.getProcNum())) {
                return new SyncResult(0, 0, "Skipped procedurelog " + dto.getProcNum()
                        + ": a newer local change is still waiting to reach Open Dental");
            }
            saveProcedureLogToDb(dto, clinic.getId());
            return new SyncResult(1, 0,
                    "Saved procedure log " + dto.getProcNum() + " from clinic " + clinicCode);
        } catch (Exception e) {
            log.error("Failed to save procedure log {} from webhook: {}", dto.getProcNum(), e.getMessage());
            return SyncResult.failure("Failed to save procedure log " + dto.getProcNum() + ": " + e.getMessage());
        }
    }

    /**
     * Save a document DTO received directly from a webhook payload (no API fetch).
     */
    @Transactional
    public SyncResult saveDocumentFromDto(DocumentResponse dto, String clinicCode) {
        Clinic clinic = clinicRepository.findByClinicCode(clinicCode)
                .orElseThrow(() -> new IllegalArgumentException("Clinic not found: " + clinicCode));
        try {
            if (hasQueuedChanges(clinic.getId(), "document", dto.getDocNum())) {
                return new SyncResult(0, 0, "Skipped document " + dto.getDocNum()
                        + ": a newer local change is still waiting to reach Open Dental");
            }
            saveDocumentToDb(dto, clinic.getId());
            return new SyncResult(1, 0,
                    "Saved document " + dto.getDocNum() + " from clinic " + clinicCode);
        } catch (Exception e) {
            log.error("Failed to save document {} from webhook: {}", dto.getDocNum(), e.getMessage());
            return SyncResult.failure("Failed to save document " + dto.getDocNum() + ": " + e.getMessage());
        }
    }

    /** Handle an AppointmentDeleted DTO received from a webhook payload (no API fetch). */
    @Transactional
    public SyncResult saveAppointmentDeletedFromDto(AppointmentDeletedResponse dto, String clinicCode) {
        Clinic clinic = clinicRepository.findByClinicCode(clinicCode)
                .orElseThrow(() -> new IllegalArgumentException("Clinic not found: " + clinicCode));
        try {
            if (dto.getAppointmentNum() == null) {
                return SyncResult.failure("AppointmentDeleted record is missing AppointmentNum");
            }

            Appointment appointment = appointmentRepository
                    .findByIdClinicIdAndIdAptNum(clinic.getId(), dto.getAppointmentNum())
                    .stream().findFirst().orElse(null);

            if (appointment == null) {
                log.warn("No appointment {} found for clinic {}; cannot apply deletion.",
                        dto.getAppointmentNum(), clinicCode);
                return SyncResult.failure("Appointment " + dto.getAppointmentNum() + " not found for clinic " + clinicCode);
            }

            appointment.setIsDeleted(true);
            appointment.setDeletedBy(dto.getDeletedBy());
            appointment.setAppointmentNum(dto.getAppointmentNum());
            appointment.setDatetimeDeleted(parseDeleteDateTime(dto.getDateTimeDeleted()));
            appointmentRepository.save(appointment);

            return new SyncResult(1, 0,
                    "Marked appointment " + dto.getAppointmentNum() + " as deleted from clinic " + clinicCode);
        } catch (Exception e) {
            log.error("Failed to update appointment deleted record from webhook: {}", e.getMessage());
            return SyncResult.failure("Failed to update appointment deleted " + e.getMessage());
        }
    }

    /** Parse a webhook date-time string into a LocalDateTime, handling empty/invalid values. */
    private LocalDateTime parseDeleteDateTime(String raw) {
        if (raw == null || raw.isBlank() || "0001-01-01 00:00:00".equals(raw)) {
            return null;
        }
        return LocalDateTime.parse(raw, DATETIME_FORMAT);
    }

    /** Save an Operatory DTO received from a webhook payload (no API fetch). */
    @Transactional
    public SyncResult saveOperatoryFromDto(OperatoryResponse dto, String clinicCode) {
        Clinic clinic = clinicRepository.findByClinicCode(clinicCode)
                .orElseThrow(() -> new IllegalArgumentException("Clinic not found: " + clinicCode));
        try {
            operatoryRepository.save(toOperatoryEntity(dto, clinic.getId()));
            return new SyncResult(1, 0, "Saved operatory " + dto.getOperatoryNum() + " from clinic " + clinicCode);
        } catch (Exception e) {
            log.error("Failed to save operatory record from webhook: {}", e.getMessage());
            return SyncResult.failure("Failed to save operatory " + e.getMessage());
        }
    }

    /** Save a PatField DTO received from a webhook payload (no API fetch). */
    @Transactional
    public SyncResult savePatFieldFromDto(PatFieldResponse dto, String clinicCode) {
        Clinic clinic = clinicRepository.findByClinicCode(clinicCode)
                .orElseThrow(() -> new IllegalArgumentException("Clinic not found: " + clinicCode));
        try {
            patFieldRepository.save(toPatFieldEntity(dto, clinic.getId()));
            // If this pat-field record carries deletion metadata, soft-delete the
            // matching patient (looked up by PatNum).
            applyPatientDeletionFromPatField(dto, clinic.getId());
            return new SyncResult(1, 0, "Saved pat field " + dto.getPatFieldNum() + " from clinic " + clinicCode);
        } catch (Exception e) {
            log.error("Failed to save pat field record from webhook: {}", e.getMessage());
            return SyncResult.failure("Failed to save pat field " + e.getMessage());
        }
    }

    /**
     * Handle a PatFieldDeleted DTO received from a webhook payload (no API fetch).
     * Soft-deletes the matching pat_fields row (keyed by pat_field_num): sets
     * is_deleted = true, deleted_at and deleted_by.
     */
    @Transactional
    public SyncResult savePatFieldDeletedFromDto(PatFieldDeletedResponse dto, String clinicCode) {
        Clinic clinic = clinicRepository.findByClinicCode(clinicCode)
                .orElseThrow(() -> new IllegalArgumentException("Clinic not found: " + clinicCode));
        try {
            if (dto.getPatFieldNum() == null) {
                return SyncResult.failure("PatFieldDeleted record is missing PatFieldNum");
            }

            PatField patField = patFieldRepository
                    .findById(new PatFieldId(clinic.getId(), dto.getPatFieldNum()))
                    .orElse(null);

            if (patField == null) {
                log.warn("No pat_field {} found for clinic {}; cannot apply deletion.",
                        dto.getPatFieldNum(), clinicCode);
                return SyncResult.failure("Pat field " + dto.getPatFieldNum() + " not found for clinic " + clinicCode);
            }

            patField.setIsDeleted(true);
            patField.setDeletedBy(dto.getDeletedBy());
            patField.setDeletedAt(parseDeleteDateTime(dto.getDateTimeDeleted()));
            patFieldRepository.save(patField);

            return new SyncResult(1, 0,
                    "Marked pat field " + dto.getPatFieldNum() + " as deleted from clinic " + clinicCode);
        } catch (Exception e) {
            log.error("Failed to update pat field deleted record from webhook: {}", e.getMessage());
            return SyncResult.failure("Failed to update pat field deleted " + e.getMessage());
        }
    }

    /**
     * Updates the matching patient row (keyed by PatNum) from a webhook pat-field
     * record. Open Dental's patient lifecycle is carried in `pat_status`
     * (Patient / Archived / Deleted), so there is no separate is_deleted flag:
     *   PatStatus            -> pat_status (as Open Dental reports it)
     *   DeletedDateTime      -> pat_status = 'Deleted'
     *   DeletedByUserNum     -> pat_status = 'Deleted'
     */
    private void applyPatientDeletionFromPatField(PatFieldResponse dto, UUID clinicId) {
        if (dto.getPatNum() == null || dto.getFieldName() == null) {
            return;
        }
        String fieldName = dto.getFieldName().trim();
        if (!fieldName.equalsIgnoreCase("PatStatus")
                && !fieldName.equalsIgnoreCase("DeletedDateTime")
                && !fieldName.equalsIgnoreCase("DeletedByUserNum")) {
            return;
        }
        Patient patient = patientRepository
                .findByIdClinicIdAndIdPatNum(clinicId, dto.getPatNum())
                .stream().findFirst().orElse(null);
        if (patient == null) {
            return;
        }
        if (fieldName.equalsIgnoreCase("PatStatus")) {
            patient.setPatStatus(dto.getFieldValue());
        } else {
            // A deletion timestamp/user marker means the patient was deleted.
            patient.setPatStatus("Deleted");
        }
        patientRepository.save(patient);
    }

    /** Save a Provider DTO received from a webhook payload (no API fetch). */
    @Transactional
    public SyncResult saveProviderFromDto(ProviderResponse dto, String clinicCode) {
        Clinic clinic = clinicRepository.findByClinicCode(clinicCode)
                .orElseThrow(() -> new IllegalArgumentException("Clinic not found: " + clinicCode));
        try {
            providerRepository.save(toProviderEntity(dto, clinic.getId()));
            return new SyncResult(1, 0, "Saved provider " + dto.getProvNum() + " from clinic " + clinicCode);
        } catch (Exception e) {
            log.error("Failed to save provider record from webhook: {}", e.getMessage());
            return SyncResult.failure("Failed to save provider " + e.getMessage());
        }
    }

    /** Save a Schedule DTO received from a webhook payload (no API fetch). */
    @Transactional
    public SyncResult saveScheduleFromDto(ScheduleResponse dto, String clinicCode) {
        Clinic clinic = clinicRepository.findByClinicCode(clinicCode)
                .orElseThrow(() -> new IllegalArgumentException("Clinic not found: " + clinicCode));
        try {
            scheduleRepository.save(toScheduleEntity(dto, clinic.getId()));
            return new SyncResult(1, 0, "Saved schedule " + dto.getScheduleNum() + " from clinic " + clinicCode);
        } catch (Exception e) {
            log.error("Failed to save schedule record from webhook: {}", e.getMessage());
            return SyncResult.failure("Failed to save schedule " + e.getMessage());
        }
    }

    /**
     * Handle a ScheduleDeleted DTO received from a webhook payload (no API fetch).
     * Soft-deletes the matching schedules row (keyed by schedule_num): sets
     * is_deleted = true, deleted_at and deleted_by.
     */
    @Transactional
    public SyncResult saveScheduleDeletedFromDto(ScheduleDeletedResponse dto, String clinicCode) {
        Clinic clinic = clinicRepository.findByClinicCode(clinicCode)
                .orElseThrow(() -> new IllegalArgumentException("Clinic not found: " + clinicCode));
        try {
            if (dto.getScheduleNum() == null) {
                return SyncResult.failure("ScheduleDeleted record is missing ScheduleNum");
            }

            Schedule schedule = scheduleRepository
                    .findById(new ScheduleId(clinic.getId(), dto.getScheduleNum()))
                    .orElse(null);

            if (schedule == null) {
                log.warn("No schedule {} found for clinic {}; cannot apply deletion.",
                        dto.getScheduleNum(), clinicCode);
                return SyncResult.failure("Schedule " + dto.getScheduleNum() + " not found for clinic " + clinicCode);
            }

            schedule.setIsDeleted(true);
            schedule.setDeletedBy(dto.getDeletedBy());
            schedule.setDeletedAt(parseDeleteDateTime(dto.getDateTimeDeleted()));
            scheduleRepository.save(schedule);

            return new SyncResult(1, 0,
                    "Marked schedule " + dto.getScheduleNum() + " as deleted from clinic " + clinicCode);
        } catch (Exception e) {
            log.error("Failed to update schedule deleted record from webhook: {}", e.getMessage());
            return SyncResult.failure("Failed to update schedule deleted " + e.getMessage());
        }
    }

    /** Save a ToothInitial DTO received from a webhook payload (no API fetch). */
    @Transactional
    public SyncResult saveToothInitialFromDto(ToothInitialResponse dto, String clinicCode) {
        Clinic clinic = clinicRepository.findByClinicCode(clinicCode)
                .orElseThrow(() -> new IllegalArgumentException("Clinic not found: " + clinicCode));
        try {
            toothInitialRepository.save(toToothInitialEntity(dto, clinic.getId()));
            return new SyncResult(1, 0, "Saved tooth initial " + dto.getToothInitialNum() + " from clinic " + clinicCode);
        } catch (Exception e) {
            log.error("Failed to save tooth initial record from webhook: {}", e.getMessage());
            return SyncResult.failure("Failed to save tooth initial " + e.getMessage());
        }
    }

    /**
     * Handle a ToothInitialDeleted DTO received from a webhook payload (no API fetch).
     * Soft-deletes the matching tooth_initials row (keyed by tooth_initial_num):
     * sets is_deleted = true, deleted_at and deleted_by.
     */
    @Transactional
    public SyncResult saveToothInitialDeletedFromDto(ToothInitialDeletedResponse dto, String clinicCode) {
        Clinic clinic = clinicRepository.findByClinicCode(clinicCode)
                .orElseThrow(() -> new IllegalArgumentException("Clinic not found: " + clinicCode));
        try {
            if (dto.getToothInitialNum() == null) {
                return SyncResult.failure("ToothInitialDeleted record is missing ToothInitialNum");
            }

            ToothInitial toothInitial = toothInitialRepository
                    .findById(new ToothInitialId(clinic.getId(), dto.getToothInitialNum()))
                    .orElse(null);

            if (toothInitial == null) {
                log.warn("No tooth_initial {} found for clinic {}; cannot apply deletion.",
                        dto.getToothInitialNum(), clinicCode);
                return SyncResult.failure("Tooth initial " + dto.getToothInitialNum() + " not found for clinic " + clinicCode);
            }

            toothInitial.setIsDeleted(true);
            toothInitial.setDeletedBy(dto.getDeletedBy());
            toothInitial.setDeletedAt(parseDeleteDateTime(dto.getDateTimeDeleted()));
            toothInitialRepository.save(toothInitial);

            return new SyncResult(1, 0,
                    "Marked tooth initial " + dto.getToothInitialNum() + " as deleted from clinic " + clinicCode);
        } catch (Exception e) {
            log.error("Failed to update tooth initial deleted record from webhook: {}", e.getMessage());
            return SyncResult.failure("Failed to update tooth initial deleted " + e.getMessage());
        }
    }

    // ========================================================================
    // Data access helpers
    // ========================================================================

    @Transactional(readOnly = true)
    public List<Patient> getPatientsByClinicCode(String clinicCode) {
        Clinic clinic = clinicRepository.findByClinicCode(clinicCode)
                .orElseThrow(() -> new IllegalArgumentException("Clinic not found: " + clinicCode));
        return patientRepository.findByIdClinicId(clinic.getId());
    }

    @Transactional(readOnly = true)
    public List<Appointment> getAppointmentsByClinicCode(String clinicCode) {
        Clinic clinic = clinicRepository.findByClinicCode(clinicCode)
                .orElseThrow(() -> new IllegalArgumentException("Clinic not found: " + clinicCode));
        return appointmentRepository.findByIdClinicId(clinic.getId());
    }

    @Transactional(readOnly = true)
    public List<ProcedureLog> getProcedureLogsByClinicCode(String clinicCode) {
        Clinic clinic = clinicRepository.findByClinicCode(clinicCode)
                .orElseThrow(() -> new IllegalArgumentException("Clinic not found: " + clinicCode));
        return procedureLogRepository.findByIdClinicId(clinic.getId());
    }

    @Transactional(readOnly = true)
    public List<Document> getDocumentsByClinicCode(String clinicCode) {
        Clinic clinic = clinicRepository.findByClinicCode(clinicCode)
                .orElseThrow(() -> new IllegalArgumentException("Clinic not found: " + clinicCode));
        return documentRepository.findByIdClinicId(clinic.getId());
    }

    @Transactional
    protected void savePatientToDb(PatientResponse dto, UUID clinicId) {
        if (dto.getPatNum() == null) {
            throw new IllegalArgumentException("Cannot save patient with null PatNum");
        }
        Patient patient = toPatientEntity(dto, clinicId);
        patientRepository.save(patient);
    }

    @Transactional
    protected void saveAppointmentToDb(AppointmentResponse dto, UUID clinicId) {
        Appointment appointment = toAppointmentEntity(dto, clinicId);
        appointmentRepository.save(appointment);
    }

    @Transactional
    protected void saveProcedureLogToDb(ProcedureLogResponse dto, UUID clinicId) {
        ProcedureLog procedureLog = toProcedureLogEntity(dto, clinicId);
        procedureLogRepository.save(procedureLog);
    }

    @Transactional
    protected void saveDocumentToDb(DocumentResponse dto, UUID clinicId) {
        Document document = toDocumentEntity(dto, clinicId);
        documentRepository.save(document);
    }

    // ========================================================================
    // Entity mapping helpers
    // ========================================================================

    private Patient toPatientEntity(PatientResponse dto, UUID clinicId) {
        Patient.PatientBuilder builder = Patient.builder()
                .id(new PatientId(clinicId, dto.getPatNum()))
                .lName(dto.getLName())
                .fName(dto.getFName())
                .middleI(dto.getMiddleI())
                .preferred(dto.getPreferred())
                .patStatus(dto.getPatStatus())
                .gender(dto.getGender())
                .position(dto.getPosition())
                .ssn(dto.getSSN())
                .address(dto.getAddress())
                .address2(dto.getAddress2())
                .city(dto.getCity())
                .state(dto.getState())
                .zip(dto.getZip())
                .hmPhone(dto.getHmPhone())
                .wkPhone(dto.getWkPhone())
                .wirelessPhone(dto.getWirelessPhone())
                .guarantor(dto.getGuarantor())
                .email(dto.getEmail())
                .priProv(dto.getPriProv())
                .secProv(dto.getSecProv())
                .feeSched(dto.getFeeSched())
                .billingType(dto.getBillingType())
                .chartNumber(dto.getChartNumber())
                .medicaidId(dto.getMedicaidID())
                .employerNum(dto.getEmployerNum())
                .clinicNum(dto.getClinicNum())
                .clinicAbbr(dto.getClinicAbbr())
                .hasIns(dto.getHasIns())
                .premed("true".equalsIgnoreCase(dto.getPremed()))
                .ward(dto.getWard())
                .preferConfirmMethod(dto.getPreferConfirmMethod())
                .preferContactMethod(dto.getPreferContactMethod())
                .preferRecallMethod(dto.getPreferRecallMethod())
                .language(dto.getLanguage())
                .siteNum(dto.getSiteNum())
                .siteDesc(dto.getSiteDesc())
                .superFamily(dto.getSuperFamily())
                .txtMsgOk(dto.getTxtMsgOk())
                .secUserNumEntry(dto.getSecUserNumEntry())
                .estBalance(dto.getEstBalance() != null ? BigDecimal.valueOf(dto.getEstBalance()) : BigDecimal.ZERO)
                .bal030(dto.getBal_0_30() != null ? BigDecimal.valueOf(dto.getBal_0_30()) : BigDecimal.ZERO)
                .bal3160(dto.getBal_31_60() != null ? BigDecimal.valueOf(dto.getBal_31_60()) : BigDecimal.ZERO)
                .bal6190(dto.getBal_61_90() != null ? BigDecimal.valueOf(dto.getBal_61_90()) : BigDecimal.ZERO)
                .balOver90(dto.getBalOver90() != null ? BigDecimal.valueOf(dto.getBalOver90()) : BigDecimal.ZERO)
                .insEst(dto.getInsEst() != null ? BigDecimal.valueOf(dto.getInsEst()) : BigDecimal.ZERO)
                .balTotal(dto.getBalTotal() != null ? BigDecimal.valueOf(dto.getBalTotal()) : BigDecimal.ZERO);

        if (dto.getBirthdate() != null && !dto.getBirthdate().isEmpty() && !dto.getBirthdate().equals("0001-01-01")) {
            builder.birthdate(LocalDate.parse(dto.getBirthdate(), DATE_FORMAT));
        }
        if (dto.getDateFirstVisit() != null && !dto.getDateFirstVisit().isEmpty() && !dto.getDateFirstVisit().equals("0001-01-01")) {
            builder.dateFirstVisit(LocalDate.parse(dto.getDateFirstVisit(), DATE_FORMAT));
        }
        if (dto.getAdmitDate() != null && !dto.getAdmitDate().isEmpty() && !dto.getAdmitDate().equals("0001-01-01")) {
            builder.admitDate(LocalDate.parse(dto.getAdmitDate(), DATE_FORMAT));
        }
        if (dto.getSecDateEntry() != null && !dto.getSecDateEntry().isEmpty() && !dto.getSecDateEntry().equals("0001-01-01")) {
            builder.secDateEntry(LocalDate.parse(dto.getSecDateEntry(), DATE_FORMAT));
        }
        if (dto.getDateTimeLastAging() != null && !dto.getDateTimeLastAging().isEmpty() && !dto.getDateTimeLastAging().equals("0001-01-01 00:00:00")) {
            builder.dateTimeLastAging(LocalDateTime.parse(dto.getDateTimeLastAging(), DATETIME_FORMAT));
        }

        return builder.build();
    }

    private Appointment toAppointmentEntity(AppointmentResponse dto, UUID clinicId) {
        Appointment.AppointmentBuilder builder = Appointment.builder()
                .id(new AppointmentId(clinicId, dto.getAptNum()))
                .patNum(dto.getPatNum())
                .aptStatus(dto.getAptStatus())
                .pattern(dto.getPattern())
                .confirmed(dto.getConfirmed())
                .timeLocked(dto.getTimeLocked())
                .op(dto.getOp())
                .note(dto.getNote())
                .provNum(dto.getProvNum())
                .provAbbr(dto.getProvAbbr())
                .provHyg(dto.getProvHyg())
                .nextAptNum(dto.getNextAptNum())
                .unschedStatus(dto.getUnschedStatus())
                .isNewPatient(dto.getIsNewPatient())
                .procDescript(dto.getProcDescript())
                .assistant(dto.getAssistant())
                .clinicNum(dto.getClinicNum())
                .isHygiene(dto.getIsHygiene())
                .insPlan1(dto.getInsPlan1())
                .insPlan2(dto.getInsPlan2())
                .colorOverride(dto.getColorOverride())
                .appointmentTypeNum(dto.getAppointmentTypeNum())
                .secUserNumEntry(dto.getSecUserNumEntry())
                .priority(dto.getPriority())
                .patternSecondary(dto.getPatternSecondary())
                .itemOrderPlanned(dto.getItemOrderPlanned())
                .isMirrored(dto.getIsMirrored())
                .eServiceLogType(dto.getEServiceLogType());

        if (dto.getAptDateTime() != null && !dto.getAptDateTime().isEmpty() && !dto.getAptDateTime().equals("0001-01-01 00:00:00")) {
            builder.aptDateTime(LocalDateTime.parse(dto.getAptDateTime(), DATETIME_FORMAT));
        }
        if (dto.getDateTStamp() != null && !dto.getDateTStamp().isEmpty() && !dto.getDateTStamp().equals("0001-01-01 00:00:00")) {
            builder.dateTStamp(LocalDateTime.parse(dto.getDateTStamp(), DATETIME_FORMAT));
        }
        if (dto.getDateTimeArrived() != null && !dto.getDateTimeArrived().isEmpty() && !dto.getDateTimeArrived().equals("0001-01-01 00:00:00")) {
            builder.dateTimeArrived(LocalDateTime.parse(dto.getDateTimeArrived(), DATETIME_FORMAT));
        }
        if (dto.getDateTimeSeated() != null && !dto.getDateTimeSeated().isEmpty() && !dto.getDateTimeSeated().equals("0001-01-01 00:00:00")) {
            builder.dateTimeSeated(LocalDateTime.parse(dto.getDateTimeSeated(), DATETIME_FORMAT));
        }
        if (dto.getDateTimeDismissed() != null && !dto.getDateTimeDismissed().isEmpty() && !dto.getDateTimeDismissed().equals("0001-01-01 00:00:00")) {
            builder.dateTimeDismissed(LocalDateTime.parse(dto.getDateTimeDismissed(), DATETIME_FORMAT));
        }
        if (dto.getDateTimeAskedToArrive() != null && !dto.getDateTimeAskedToArrive().isEmpty() && !dto.getDateTimeAskedToArrive().equals("0001-01-01 00:00:00")) {
            builder.dateTimeAskedToArrive(LocalDateTime.parse(dto.getDateTimeAskedToArrive(), DATETIME_FORMAT));
        }
        if (dto.getSecDateTEntry() != null && !dto.getSecDateTEntry().isEmpty() && !dto.getSecDateTEntry().equals("0001-01-01 00:00:00")) {
            builder.secDateTEntry(LocalDateTime.parse(dto.getSecDateTEntry(), DATETIME_FORMAT));
        }

        return builder.build();
    }

    private ProcedureLog toProcedureLogEntity(ProcedureLogResponse dto, UUID clinicId) {
        ProcedureLog.ProcedureLogBuilder builder = ProcedureLog.builder()
                .id(new ProcedureLogId(clinicId, dto.getProcNum()))
                .patNum(dto.getPatNum())
                .aptNum(dto.getAptNum() != null && dto.getAptNum() != 0L ? dto.getAptNum() : null)
                .surf(dto.getSurf())
                .toothNum(dto.getToothNum())
                .toothRange(dto.getToothRange())
                .priority(dto.getPriority())
                .procStatus(dto.getProcStatus())
                .provNum(dto.getProvNum())
                .provAbbr(dto.getProvAbbr())
                .dx(dto.getDx())
                .dxName(dto.getDxName())
                .plannedAptNum(dto.getPlannedAptNum())
                .placeService(dto.getPlaceService())
                .prosthesis(dto.getProsthesis())
                .claimNote(dto.getClaimNote())
                .clinicNum(dto.getClinicNum())
                .diagnosticCode(dto.getDiagnosticCode())
                .isPrincDiag(dto.getIsPrincDiag())
                .codeNum(dto.getCodeNum())
                .procCode(dto.getProcCode())
                .descript(dto.getDescript())
                .unitQty(dto.getUnitQty())
                .baseUnits(dto.getBaseUnits())
                .siteNum(dto.getSiteNum())
                .hideGraphics(dto.getHideGraphics())
                .canadianTypeCodes(dto.getCanadianTypeCodes())
                .procTime(dto.getProcTime())
                .procTimeEnd(dto.getProcTimeEnd())
                .prognosis(dto.getPrognosis())
                .isLocked(dto.getIsLocked())
                .billingNote(dto.getBillingNote())
                .snomedBodySite(dto.getSnomedBodySite())
                .diagnosticCode2(dto.getDiagnosticCode2())
                .diagnosticCode3(dto.getDiagnosticCode3())
                .diagnosticCode4(dto.getDiagnosticCode4())
                .isDateProsthEst(dto.getIsDateProsthEst())
                .icdVersion(dto.getIcdVersion());

        if (dto.getProcFee() != null && !dto.getProcFee().isEmpty()) {
            builder.procFee(new BigDecimal(dto.getProcFee()));
        }
        if (dto.getDiscount() != null) {
            builder.discount(BigDecimal.valueOf(dto.getDiscount()));
        }
        if (dto.getDiscountPlanAmt() != null) {
            builder.discountPlanAmt(BigDecimal.valueOf(dto.getDiscountPlanAmt()));
        }

        if (dto.getProcDate() != null && !dto.getProcDate().isEmpty() && !dto.getProcDate().equals("0001-01-01")) {
            builder.procDate(LocalDate.parse(dto.getProcDate(), DATE_FORMAT));
        }
        if (dto.getDateOriginalProsth() != null && !dto.getDateOriginalProsth().isEmpty() && !dto.getDateOriginalProsth().equals("0001-01-01")) {
            builder.dateOriginalProsth(LocalDate.parse(dto.getDateOriginalProsth(), DATE_FORMAT));
        }
        if (dto.getDateEntryC() != null && !dto.getDateEntryC().isEmpty() && !dto.getDateEntryC().equals("0001-01-01")) {
            builder.dateEntryC(LocalDate.parse(dto.getDateEntryC(), DATE_FORMAT));
        }
        if (dto.getDateTP() != null && !dto.getDateTP().isEmpty() && !dto.getDateTP().equals("0001-01-01")) {
            builder.dateTP(LocalDate.parse(dto.getDateTP(), DATE_FORMAT));
        }
        if (dto.getDateTStamp() != null && !dto.getDateTStamp().isEmpty() && !dto.getDateTStamp().equals("0001-01-01 00:00:00")) {
            builder.dateTStamp(LocalDateTime.parse(dto.getDateTStamp(), DATETIME_FORMAT));
        }
        if (dto.getSecDateEntry() != null && !dto.getSecDateEntry().isEmpty() && !dto.getSecDateEntry().equals("0001-01-01 00:00:00")) {
            builder.secDateEntry(LocalDateTime.parse(dto.getSecDateEntry(), DATETIME_FORMAT));
        }

        return builder.build();
    }

    /** Open Dental returns PatNum as text on documents. */
    private static Long parsePatNum(String patNum) {
        if (patNum == null || patNum.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(patNum.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Document toDocumentEntity(DocumentResponse dto, UUID clinicId) {
        Document.DocumentBuilder builder = Document.builder()
                .id(new DocumentId(clinicId, dto.getDocNum()))
                .patNum(parsePatNum(dto.getPatNum()))
                .description(dto.getDescription())
                .note(dto.getNote())
                .imgType(dto.getImgType())
                .toothNumbers(dto.getToothNumbers())
                .provNum(dto.getProvNum())
                .printHeading(dto.getPrintHeading());

        if (dto.getDocCategory() != null && !dto.getDocCategory().isEmpty()) {
            try {
                builder.docCategory(Long.parseLong(dto.getDocCategory()));
            } catch (NumberFormatException e) {
                log.warn("Failed to parse docCategory '{}' as Long", dto.getDocCategory());
            }
        }

        if (dto.getFileName() != null && !dto.getFileName().isEmpty()) {
            builder.fileName(dto.getFileName());
        }

        if (dto.getDateCreated() != null && !dto.getDateCreated().isEmpty()
                && !dto.getDateCreated().equals("0001-01-01 00:00:00")
                && !dto.getDateCreated().equals("0001-01-01")) {
            try {
                builder.dateCreated(LocalDateTime.parse(dto.getDateCreated(), DATETIME_FORMAT));
            } catch (Exception e) {
                try {
                    builder.dateCreated(LocalDate.parse(dto.getDateCreated(), DATE_FORMAT).atStartOfDay());
                } catch (Exception ex) {
                    // ignore parse errors
                }
            }
        }

        if (dto.getDateTStamp() != null && !dto.getDateTStamp().isEmpty()
                && !dto.getDateTStamp().equals("0001-01-01 00:00:00")) {
            try {
                builder.dateTStamp(LocalDateTime.parse(dto.getDateTStamp(), DATETIME_FORMAT));
            } catch (Exception e) {
                // ignore parse errors
            }
        }

        return builder.build();
    }

    private Operatory toOperatoryEntity(OperatoryResponse dto, UUID clinicId) {
        Operatory operatory = new Operatory();
        operatory.setId(new OperatoryId(clinicId, dto.getOperatoryNum()));
        operatory.setAbbrev(dto.getAbbrev());
        operatory.setDescription(dto.getDescription());
        operatory.setClinicNum(dto.getClinicNum());
        operatory.setIsHygiene(dto.getIsHygiene());
        operatory.setIsDisabled(dto.getIsDisabled());
        operatory.setIsWebSched(dto.getIsWebSched());
        operatory.setOrderValue(dto.getOrderValue());
        return operatory;
    }

    private PatField toPatFieldEntity(PatFieldResponse dto, UUID clinicId) {
        PatField patField = new PatField();
        patField.setId(new PatFieldId(clinicId, dto.getPatFieldNum()));
        patField.setPatNum(dto.getPatNum());
        patField.setFieldName(dto.getFieldName());
        patField.setFieldValue(dto.getFieldValue());
        patField.setFieldDesc(dto.getFieldDesc());
        patField.setFieldType(dto.getFieldType());
        patField.setClinicNum(dto.getClinicNum());
        return patField;
    }

    private Provider toProviderEntity(ProviderResponse dto, UUID clinicId) {
        Provider provider = new Provider();
        provider.setId(new ProviderId(clinicId, dto.getProvNum()));
        provider.setAbbrev(dto.getAbbrev());
        provider.setFName(dto.getFName());
        provider.setLName(dto.getLName());
        provider.setSuffix(dto.getSuffix());
        provider.setSpecialty(dto.getSpecialty());
        provider.setProvStatus(dto.getProvStatus());
        provider.setProvType(dto.getProvType());
        provider.setClinicNum(dto.getClinicNum());
        return provider;
    }

    private Schedule toScheduleEntity(ScheduleResponse dto, UUID clinicId) {
        Schedule schedule = new Schedule();
        schedule.setId(new ScheduleId(clinicId, dto.getScheduleNum()));
        schedule.setSchedTypeNum(dto.getSchedTypeNum());
        schedule.setProvNum(dto.getProvNum());
        schedule.setClinicNum(dto.getClinicNum());
        schedule.setStartTime(dto.getStartTime());
        schedule.setStopTime(dto.getStopTime());
        schedule.setBlockout(dto.getBlockout());
        if (dto.getSchedDate() != null && !dto.getSchedDate().isEmpty()
                && !dto.getSchedDate().equals("0001-01-01")) {
            try {
                schedule.setSchedDate(LocalDate.parse(dto.getSchedDate(), DATE_FORMAT));
            } catch (Exception e) {
                log.warn("Failed to parse SchedDate '{}'", dto.getSchedDate());
            }
        }
        return schedule;
    }

    private ToothInitial toToothInitialEntity(ToothInitialResponse dto, UUID clinicId) {
        ToothInitial toothInitial = new ToothInitial();
        toothInitial.setId(new ToothInitialId(clinicId, dto.getToothInitialNum()));
        toothInitial.setPatNum(dto.getPatNum());
        toothInitial.setToothNum(dto.getToothNum());
        toothInitial.setToothType(dto.getToothType());
        toothInitial.setToothGroup(dto.getToothGroup());
        toothInitial.setMobility(dto.getMobility());
        if (dto.getDateTStamp() != null && !dto.getDateTStamp().isEmpty()
                && !dto.getDateTStamp().equals("0001-01-01 00:00:00")) {
            try {
                toothInitial.setDateTStamp(LocalDateTime.parse(dto.getDateTStamp(), DATETIME_FORMAT));
            } catch (Exception e) {
                log.warn("Failed to parse DateTStamp '{}'", dto.getDateTStamp());
            }
        }
        return toothInitial;
    }

    /**
     * Result record for sync operations.
     */
    public record SyncResult(int syncedCount, int failedCount, String message) {
        public static SyncResult failure(String message) {
            return new SyncResult(0, 0, message);
        }
    }
}
