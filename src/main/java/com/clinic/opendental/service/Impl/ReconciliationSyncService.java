package com.clinic.opendental.service.Impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.opendental.client.OpenDentalClient;
import com.clinic.opendental.dto.appointment.AppointmentResponse;
import com.clinic.opendental.dto.clinic.ClinicResponse;
import com.clinic.opendental.dto.document.DocumentResponse;
import com.clinic.opendental.dto.operatory.OperatoryResponse;
import com.clinic.opendental.dto.patfield.PatFieldResponse;
import com.clinic.opendental.dto.patient.PatientResponse;
import com.clinic.opendental.dto.procedurelog.ProcedureLogResponse;
import com.clinic.opendental.dto.provider.ProviderResponse;
import com.clinic.opendental.dto.schedule.ScheduleResponse;
import com.clinic.opendental.dto.toothinitial.ToothInitialResponse;
import com.clinic.opendental.model.Appointment;
import com.clinic.opendental.model.AppointmentId;
import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.model.Document;
import com.clinic.opendental.model.DocumentId;
import com.clinic.opendental.model.OdClinic;
import com.clinic.opendental.model.OdClinicId;
import com.clinic.opendental.model.Patient;
import com.clinic.opendental.model.PatientId;
import com.clinic.opendental.model.ProcedureLog;
import com.clinic.opendental.model.ProcedureLogId;
import com.clinic.opendental.repository.OdSyncTaskRepository;
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
import com.clinic.opendental.repository.DocumentRepository;
import com.clinic.opendental.repository.OdClinicRepository;
import com.clinic.opendental.repository.PatientRepository;
import com.clinic.opendental.repository.ProcedureLogRepository;
import com.clinic.opendental.repository.ref.OperatoryRepository;
import com.clinic.opendental.repository.ref.PatFieldRepository;
import com.clinic.opendental.repository.ref.ProviderRepository;
import com.clinic.opendental.repository.ref.ScheduleRepository;
import com.clinic.opendental.repository.ref.ToothInitialRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reconciliation sync service.
 *
 * Reconciles Open Dental data into Supabase for ALL entity types
 * (patients, appointments, documents, procedure logs) for each active clinic.
 *
 * This is used by the scheduled reconciliation job to catch any webhook
 * events that may have been missed due to temporary outages.
 */
@Service
@RequiredArgsConstructor
@Slf4j
// JDT null-analysis produces false "unchecked conversion to @NonNull" warnings
// on Spring Data generic repository calls (save/findByIdClinicId). Class-level
// suppression silences all of them; this is only a null-safety hint, not a bug.
@SuppressWarnings("null")
public class ReconciliationSyncService {

    private final ClinicRepository clinicRepository;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final DocumentRepository documentRepository;
    private final ProcedureLogRepository procedureLogRepository;
    private final PatFieldRepository patFieldRepository;
    private final OdClinicRepository odClinicRepository;
    private final ScheduleRepository scheduleRepository;
    private final ToothInitialRepository toothInitialRepository;
    private final ProviderRepository providerRepository;
    private final OperatoryRepository operatoryRepository;
    private final OpenDentalClient client;

    /** Optional so unit tests that build this service by hand need not supply it. */
    @Autowired(required = false)
    private OdSyncTaskRepository odSyncTaskRepository;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ========================================================================
    // Full reconciliation for ALL active clinics + ALL entity types
    // ========================================================================

    /**
     * Reconcile all entity types for all active clinics.
     * Returns a summary of what was reconciled.
     *
     * <p>Intentionally NOT @Transactional: each repository.save(...) is its own
     * transaction, so we never hold a DB connection open across the slow external
     * Open Dental HTTP calls (which would cause HikariCP "connection leak" after
     * the leakDetectionThreshold).
     */
    public ReconciliationResult reconcileAll() {
        List<Clinic> clinics = clinicRepository.findByIsActiveTrue();
        if (clinics.isEmpty()) {
            log.warn("Reconciliation aborted: no active clinics configured.");
            return ReconciliationResult.failure("No active clinics configured");
        }

        int totalSynced = 0;
        int totalFailed = 0;
        int totalEntities = 0;

        for (Clinic clinic : clinics) {
            try {
                ReconciliationResult result = reconcileClinic(clinic);
                totalSynced += result.syncedCount();
                totalFailed += result.failedCount();
                totalEntities += result.entityCount();
                log.info("Reconciliation for clinic {} complete: {} synced, {} failed",
                        clinic.getClinicCode(), result.syncedCount(), result.failedCount());
            } catch (Exception e) {
                totalFailed++;
                log.error("Failed to reconcile clinic {}: {}", clinic.getClinicCode(), e.getMessage());
            }
        }

        String message = String.format("Reconciliation completed for %d clinics (%d entities, %d synced, %d failed)",
                clinics.size(), totalEntities, totalSynced, totalFailed);
        log.info(message);
        return new ReconciliationResult(totalSynced, totalFailed, totalEntities, message);
    }

    /**
     * Reconcile all entity types for a single clinic using full difference detection.
     *
     * For each entity type we:
     *   1. Fetch all records from Open Dental (source of truth).
     *   2. Load all existing records from Supabase for this clinic.
     *   3. Compare by external ID:
     *      - Open Dental record not in Supabase        -> INSERT (missing)
     *      - Open Dental record present with changes   -> UPDATE (changed)
     *      - Open Dental record present and identical  -> skip (unchanged)
     *      - Supabase record absent from Open Dental   -> STALE (kept, logged for review)
     *      - Duplicate IDs in either source            -> detected and logged
     *
     * <p>NOT @Transactional for the same reason as reconcileAll(): external HTTP
     * calls must not hold a pooled DB connection. Each entity's repository.save()
     * is its own transaction.
     */
    public ReconciliationResult reconcileClinic(Clinic clinic) {
        Stats total = new Stats();

        Stats clinics = reconcileClinics(clinic);
        logSummary("clinics", clinic, clinics);
        total.add(clinics);

        Stats patients = reconcilePatients(clinic);
        logSummary("patients", clinic, patients);
        total.add(patients);

        Stats appointments = reconcileAppointments(clinic);
        logSummary("appointments", clinic, appointments);
        total.add(appointments);

        Stats documents = reconcileDocuments(clinic);
        logSummary("documents", clinic, documents);
        total.add(documents);

        Stats procedureLogs = reconcileProcedureLogs(clinic);
        logSummary("procedurelogs", clinic, procedureLogs);
        total.add(procedureLogs);

        Stats patFields = reconcilePatFields(clinic);
        logSummary("patfields", clinic, patFields);
        total.add(patFields);

        Stats schedules = reconcileSchedules(clinic);
        logSummary("schedules", clinic, schedules);
        total.add(schedules);

        Stats toothInitials = reconcileToothInitials(clinic);
        logSummary("toothinitials", clinic, toothInitials);
        total.add(toothInitials);

        Stats providers = reconcileProviders(clinic);
        logSummary("providers", clinic, providers);
        total.add(providers);

        Stats operatories = reconcileOperatories(clinic);
        logSummary("operatories", clinic, operatories);
        total.add(operatories);

        String message = String.format(
                "Reconciliation for clinic %s complete: %d processed, %d inserted, %d updated, %d unchanged, %d stale, %d duplicates, %d failed",
                clinic.getClinicCode(), total.processed(), total.inserted, total.updated,
                total.unchanged, total.stale, total.duplicates, total.failed);
        log.info(message);
        return new ReconciliationResult(total.synced(), total.failed, total.processed(), message);
    }

    // ========================================================================
    // Per-entity reconciliation (difference detection)
    // ========================================================================

    @SuppressWarnings("null")
    private Stats reconcileClinics(Clinic clinic) {
        Stats stats = new Stats();
        try {
            List<ClinicResponse> dtos = client.getClinics(clinic.getBaseUrl(), clinic.getApiKey());
            Map<Long, ClinicResponse> byId = new HashMap<>();
            for (ClinicResponse dto : dtos) {
                if (byId.put(dto.getClinicNum(), dto) != null) {
                    stats.duplicates++;
                    log.warn("Duplicate Open Dental clinic ClinicNum={} returned for installation {}",
                            dto.getClinicNum(), clinic.getClinicCode());
                }
            }
            List<OdClinic> existing = odClinicRepository.findByIdClinicId(clinic.getId());
            Map<Long, OdClinic> existingById = new HashMap<>();
            for (OdClinic c : existing) {
                if (existingById.put(c.getId().getClinicNum(), c) != null) {
                    stats.duplicates++;
                    log.warn("Duplicate Open Dental clinic ClinicNum={} found in Supabase for installation {}",
                            c.getId().getClinicNum(), clinic.getClinicCode());
                }
            }
            for (ClinicResponse dto : dtos) {
                Long clinicNum = null;
                try {
                    clinicNum = dto.getClinicNum();
                    if (clinicNum == null) {
                        stats.failed++;
                        log.warn("Skipping clinic with null ClinicNum returned by Open Dental for installation {} "
                                + "(cannot insert/update without a primary key)", clinic.getClinicCode());
                        continue;
                    }
                    OdClinic stored = existingById.get(clinicNum);
                    if (stored == null) {
                        odClinicRepository.save(toOdClinicEntity(dto, clinic.getId()));
                        stats.inserted++;
                        logReconciled("clinic", clinicNum, clinic, "INSERT (missing)");
                    } else {
                        OdClinic incoming = toOdClinicEntity(dto, clinic.getId());
                        if (clinicSignature(stored).equals(clinicSignature(incoming))) {
                            stats.unchanged++;
                        } else {
                            odClinicRepository.save(incoming);
                            stats.updated++;
                            logReconciled("clinic", clinicNum, clinic, "UPDATE (changed)");
                        }
                    }
                } catch (Exception e) {
                    stats.failed++;
                    log.error("Failed to reconcile Open Dental clinic {} from installation {}: {}",
                            clinicNum, clinic.getClinicCode(), e.getMessage());
                }
            }
            for (OdClinic c : existing) {
                if (!byId.containsKey(c.getId().getClinicNum())) {
                    stats.stale++;
                    log.warn("Stale Open Dental clinic ClinicNum={} present in Supabase but missing from Open Dental (installation {})",
                            c.getId().getClinicNum(), clinic.getClinicCode());
                }
            }
            log.info("Reconciled {} Open Dental clinics from installation {} ({} inserted, {} updated, {} unchanged, {} stale, {} duplicates)",
                    dtos.size(), clinic.getClinicCode(), stats.inserted, stats.updated,
                    stats.unchanged, stats.stale, stats.duplicates);
        } catch (Exception e) {
            stats.failed++;
            log.error("Failed to fetch Open Dental clinics from installation {}: {}",
                    clinic.getClinicCode(), e.getMessage());
        }
        return stats;
    }

    private Stats reconcilePatients(Clinic clinic) {
        return reconcilePatients(clinic, null);
    }

    /**
     * Full read when {@code changed} is null; otherwise only those patients (a change pull):
     * then only their stored rows are loaded and nothing is reported stale.
     */
    Stats reconcilePatients(Clinic clinic, List<PatientResponse> changed) {
        Stats stats = new Stats();
        try {
            List<PatientResponse> dtos = changed != null ? changed
                    : fetchAllPages(Map.of(), params -> client.getPatients(params, clinic.getBaseUrl(), clinic.getApiKey()));
            Map<Long, PatientResponse> byId = new HashMap<>();
            for (PatientResponse dto : dtos) {
                if (byId.put(dto.getPatNum(), dto) != null) {
                    stats.duplicates++;
                    log.warn("Duplicate patient PatNum={} returned by Open Dental for clinic {}",
                            dto.getPatNum(), clinic.getClinicCode());
                }
            }
            List<Patient> existing = changed != null
                    ? patientRepository.findAllById(dtos.stream().filter(d -> d.getPatNum() != null)
                            .map(d -> new PatientId(clinic.getId(), d.getPatNum())).toList())
                    : patientRepository.findByIdClinicId(clinic.getId());
            Set<Long> queued = queuedLocalIds(clinic.getId(), OdSyncService.PATIENT);
            Map<Long, Patient> existingById = new HashMap<>();
            for (Patient p : existing) {
                if (existingById.put(p.getId().getPatNum(), p) != null) {
                    stats.duplicates++;
                    log.warn("Duplicate patient PatNum={} found in Supabase for clinic {}",
                            p.getId().getPatNum(), clinic.getClinicCode());
                }
            }
            for (PatientResponse dto : dtos) {
                Long patNum = null;
                try {
                    patNum = dto.getPatNum();
                    // Guard against a source record with no usable primary key
                    // (e.g. a field-casing mismatch swallowing PatNum). Without this,
                    // a null pat_num would either violate the NOT NULL constraint on
                    // insert, or look "changed" and blindly overwrite an existing row.
                    if (patNum == null) {
                        stats.failed++;
                        log.warn("Skipping patient with null PatNum returned by Open Dental for clinic {} "
                                + "(cannot insert/update without a primary key)", clinic.getClinicCode());
                        continue;
                    }
                    if (queued.contains(patNum)) {
                        // A local change is still waiting to reach Open Dental; don't overwrite it.
                        stats.unchanged++;
                        continue;
                    }
                    Patient stored = existingById.get(patNum);
                    if (stored == null) {
                        patientRepository.save(toPatientEntity(dto, clinic.getId()));
                        stats.inserted++;
                        logReconciled("patient", patNum, clinic, "INSERT (missing)");
                    } else {
                        Patient incoming = toPatientEntity(dto, clinic.getId());
                        if (patientSignature(stored).equals(patientSignature(incoming))) {
                            stats.unchanged++;
                        } else {
                            patientRepository.save(incoming);
                            stats.updated++;
                            logReconciled("patient", patNum, clinic, "UPDATE (changed)");
                        }
                    }
                } catch (Exception e) {
                    stats.failed++;
                    log.error("Failed to reconcile patient {} from clinic {}: {}",
                            patNum, clinic.getClinicCode(), e.getMessage());
                }
            }
            for (Patient p : changed != null ? List.<Patient>of() : existing) {
                if (!byId.containsKey(p.getId().getPatNum())) {
                    stats.stale++;
                    log.warn("Stale patient PatNum={} present in Supabase but missing from Open Dental (clinic {})",
                            p.getId().getPatNum(), clinic.getClinicCode());
                }
            }
            log.info("Reconciled {} patients from clinic {} ({} inserted, {} updated, {} unchanged, {} stale, {} duplicates)",
                    dtos.size(), clinic.getClinicCode(), stats.inserted, stats.updated,
                    stats.unchanged, stats.stale, stats.duplicates);
        } catch (Exception e) {
            stats.failed++;
            log.error("Failed to fetch patients from clinic {}: {}", clinic.getClinicCode(), e.getMessage());
        }
        return stats;
    }

    private Stats reconcilePatFields(Clinic clinic) {
        Stats stats = new Stats();
        try {
            List<PatFieldResponse> dtos = fetchAllPages(new HashMap<>(), params -> client.getPatFields(params, clinic.getBaseUrl(), clinic.getApiKey()));
            Map<Long, PatFieldResponse> byId = new HashMap<>();
            for (PatFieldResponse dto : dtos) {
                if (byId.put(dto.getPatFieldNum(), dto) != null) {
                    stats.duplicates++;
                    log.warn("Duplicate pat_field PatFieldNum={} returned by Open Dental for clinic {}", dto.getPatFieldNum(), clinic.getClinicCode());
                }
            }
            List<PatField> existing = patFieldRepository.findByIdClinicId(clinic.getId());
            Map<Long, PatField> existingById = new HashMap<>();
            for (PatField p : existing) {
                if (existingById.put(p.getId().getPatFieldNum(), p) != null) {
                    stats.duplicates++;
                    log.warn("Duplicate pat_field PatFieldNum={} found in Supabase for clinic {}", p.getId().getPatFieldNum(), clinic.getClinicCode());
                }
            }
            for (PatFieldResponse dto : dtos) {
                Long patFieldNum = null;
                try {
                    patFieldNum = dto.getPatFieldNum();
                    if (patFieldNum == null) {
                        stats.failed++;
                        log.warn("Skipping pat_field with null PatFieldNum returned by Open Dental for clinic {} (cannot insert/update without a primary key)", clinic.getClinicCode());
                        continue;
                    }
                    PatField stored = existingById.get(patFieldNum);
                    if (stored == null) {
                        patFieldRepository.save(toPatFieldEntity(dto, clinic.getId()));
                        stats.inserted++;
                        logReconciled("pat_field", patFieldNum, clinic, "INSERT (missing)");
                    } else {
                        PatField incoming = toPatFieldEntity(dto, clinic.getId());
                        if (patFieldSignature(stored).equals(patFieldSignature(incoming))) {
                            stats.unchanged++;
                        } else {
                            patFieldRepository.save(incoming);
                            stats.updated++;
                            logReconciled("pat_field", patFieldNum, clinic, "UPDATE (changed)");
                        }
                    }
                } catch (Exception e) {
                    stats.failed++;
                    log.error("Failed to reconcile pat_field {} from clinic {}: {}", patFieldNum, clinic.getClinicCode(), e.getMessage());
                }
            }
            for (PatField p : existing) {
                if (!byId.containsKey(p.getId().getPatFieldNum())) {
                    stats.stale++;
                    // PatFieldDeleted reconciliation: a pat_field present in Supabase but
                    // missing from Open Dental's current snapshot was deleted on the source.
                    // Soft-delete it so a missed/duplicate delete webhook is reconciled.
                    if (!Boolean.TRUE.equals(p.getIsDeleted())) {
                        p.setIsDeleted(true);
                        p.setDeletedAt(LocalDateTime.now());
                        patFieldRepository.save(p);
                        logReconciled("pat_field", p.getId().getPatFieldNum(), clinic, "SOFT-DELETE (missing in Open Dental)");
                    } else {
                        log.warn("Stale pat_field PatFieldNum={} already soft-deleted (clinic {})",
                                p.getId().getPatFieldNum(), clinic.getClinicCode());
                    }
                }
            }
            log.info("Reconciled {} pat_fields from clinic {} ({} inserted, {} updated, {} unchanged, {} stale, {} duplicates)", dtos.size(), clinic.getClinicCode(), stats.inserted, stats.updated, stats.unchanged, stats.stale, stats.duplicates);
        } catch (Exception e) {
            stats.failed++;
            log.error("Failed to fetch pat_fields from clinic {}: {}", clinic.getClinicCode(), e.getMessage());
        }
        return stats;
    }

    private Stats reconcileAppointments(Clinic clinic) {
        return reconcileAppointments(clinic, Map.of());
    }

    /** Full read with no filter; with one (DateTStamp) a change pull: see {@link #reconcilePatients(Clinic, List)}. */
    Stats reconcileAppointments(Clinic clinic, Map<String, String> filter) {
        Stats stats = new Stats();
        boolean changesOnly = !filter.isEmpty();
        try {
            List<AppointmentResponse> dtos = fetchAllPages(filter, params -> client.getAppointments(params, clinic.getBaseUrl(), clinic.getApiKey()));
            dtos.forEach(d -> stats.seen(d.getDateTStamp()));
            Map<Long, AppointmentResponse> byId = new HashMap<>();
            for (AppointmentResponse dto : dtos) {
                if (byId.put(dto.getAptNum(), dto) != null) {
                    stats.duplicates++;
                    log.warn("Duplicate appointment AptNum={} returned by Open Dental for clinic {}",
                            dto.getAptNum(), clinic.getClinicCode());
                }
            }
            List<Appointment> existing = changesOnly
                    ? appointmentRepository.findAllById(dtos.stream().filter(d -> d.getAptNum() != null)
                            .map(d -> new AppointmentId(clinic.getId(), d.getAptNum())).toList())
                    : appointmentRepository.findByIdClinicId(clinic.getId());
            Set<Long> queued = queuedLocalIds(clinic.getId(), OdSyncService.APPOINTMENT);
            Map<Long, Appointment> existingById = new HashMap<>();
            for (Appointment a : existing) {
                if (existingById.put(a.getId().getAptNum(), a) != null) {
                    stats.duplicates++;
                    log.warn("Duplicate appointment AptNum={} found in Supabase for clinic {}",
                            a.getId().getAptNum(), clinic.getClinicCode());
                }
            }
            for (AppointmentResponse dto : dtos) {
                Long aptNum = null;
                try {
                    aptNum = dto.getAptNum();
                    if (aptNum == null) {
                        stats.failed++;
                        log.warn("Skipping appointment with null AptNum returned by Open Dental for clinic {} "
                                + "(cannot insert/update without a primary key)", clinic.getClinicCode());
                        continue;
                    }
                    if (queued.contains(aptNum)) {
                        // A local change is still waiting to reach Open Dental; don't overwrite it.
                        stats.unchanged++;
                        continue;
                    }
                    Appointment stored = existingById.get(aptNum);
                    if (stored == null) {
                        appointmentRepository.save(toAppointmentEntity(dto, clinic.getId()));
                        stats.inserted++;
                        logReconciled("appointment", aptNum, clinic, "INSERT (missing)");
                    } else {
                        Appointment incoming = toAppointmentEntity(dto, clinic.getId());
                        if (appointmentSignature(stored).equals(appointmentSignature(incoming))) {
                            stats.unchanged++;
                        } else {
                            appointmentRepository.save(incoming);
                            stats.updated++;
                            logReconciled("appointment", aptNum, clinic, "UPDATE (changed)");
                        }
                    }
                } catch (Exception e) {
                    stats.failed++;
                    log.error("Failed to reconcile appointment {} from clinic {}: {}",
                            aptNum, clinic.getClinicCode(), e.getMessage());
                }
            }
            for (Appointment a : changesOnly ? List.<Appointment>of() : existing) {
                if (!byId.containsKey(a.getId().getAptNum())) {
                    stats.stale++;
                    log.warn("Stale appointment AptNum={} present in Supabase but missing from Open Dental (clinic {})",
                            a.getId().getAptNum(), clinic.getClinicCode());
                }
            }
            log.info("Reconciled {} appointments from clinic {} ({} inserted, {} updated, {} unchanged, {} stale, {} duplicates)",
                    dtos.size(), clinic.getClinicCode(), stats.inserted, stats.updated,
                    stats.unchanged, stats.stale, stats.duplicates);
        } catch (Exception e) {
            stats.failed++;
            log.error("Failed to fetch appointments from clinic {}: {}", clinic.getClinicCode(), e.getMessage());
        }
        return stats;
    }

    private Stats reconcileDocuments(Clinic clinic) {
        Stats stats = new Stats();
        try {
                        // Open Dental's /documents endpoint is patient-scoped: it requires PatNum on
            // every request (empty params -> 400 "PatNum is required"). Fetch documents for
            // each patient in the clinic and merge them into a single deduped set.
            List<DocumentResponse> dtos = new ArrayList<>();
            Map<Long, DocumentResponse> byId = new HashMap<>();
            List<PatientResponse> patients = fetchAllPages(Map.of(), params -> client.getPatients(params, clinic.getBaseUrl(), clinic.getApiKey()));
            if (patients == null) {
                patients = List.of();
            }
            for (PatientResponse patient : patients) {
                Long patNum = patient.getPatNum();
                if (patNum == null) {
                    continue;
                }
                try {
                    List<DocumentResponse> perPatient = fetchAllPages(
                            Map.of("PatNum", String.valueOf(patNum)),
                            params -> client.getDocuments(params, clinic.getBaseUrl(), clinic.getApiKey()));
                    if (perPatient == null) {
                        continue;
                    }
                    for (DocumentResponse dto : perPatient) {
                        if (byId.put(dto.getDocNum(), dto) == null) {
                            dtos.add(dto);
                        } else {
                            stats.duplicates++;
                            log.warn("Duplicate document DocNum={} returned by Open Dental for clinic {}",
                                    dto.getDocNum(), clinic.getClinicCode());
                        }
                    }
                } catch (Exception e) {
                    stats.failed++;
                    log.error("Failed to fetch documents for PatNum={} from clinic {}: {}",
                            patNum, clinic.getClinicCode(), e.getMessage());
                }
            }
            List<Document> existing = documentRepository.findByIdClinicId(clinic.getId());
            Set<Long> queued = queuedLocalIds(clinic.getId(), OdSyncService.DOCUMENT);
            Map<Long, Document> existingById = new HashMap<>();
            for (Document d : existing) {
                if (existingById.put(d.getId().getDocNum(), d) != null) {
                    stats.duplicates++;
                    log.warn("Duplicate document DocNum={} found in Supabase for clinic {}",
                            d.getId().getDocNum(), clinic.getClinicCode());
                }
            }
            for (DocumentResponse dto : dtos) {
                Long docNum = null;
                try {
                    docNum = dto.getDocNum();
                    if (docNum == null) {
                        stats.failed++;
                        log.warn("Skipping document with null DocNum returned by Open Dental for clinic {} "
                                + "(cannot insert/update without a primary key)", clinic.getClinicCode());
                        continue;
                    }
                    if (queued.contains(docNum)) {
                        // A local change is still waiting to reach Open Dental; don't overwrite it.
                        stats.unchanged++;
                        continue;
                    }
                    Document stored = existingById.get(docNum);
                    if (stored == null) {
                        documentRepository.save(toDocumentEntity(dto, clinic.getId()));
                        stats.inserted++;
                        logReconciled("document", docNum, clinic, "INSERT (missing)");
                    } else {
                        Document incoming = toDocumentEntity(dto, clinic.getId());
                        if (documentSignature(stored).equals(documentSignature(incoming))) {
                            stats.unchanged++;
                        } else {
                            documentRepository.save(incoming);
                            stats.updated++;
                            logReconciled("document", docNum, clinic, "UPDATE (changed)");
                        }
                    }
                } catch (Exception e) {
                    stats.failed++;
                    log.error("Failed to reconcile document {} from clinic {}: {}",
                            docNum, clinic.getClinicCode(), e.getMessage());
                }
            }
            for (Document d : existing) {
                if (!byId.containsKey(d.getId().getDocNum())) {
                    stats.stale++;
                    log.warn("Stale document DocNum={} present in Supabase but missing from Open Dental (clinic {})",
                            d.getId().getDocNum(), clinic.getClinicCode());
                }
            }
            log.info("Reconciled {} documents from clinic {} ({} inserted, {} updated, {} unchanged, {} stale, {} duplicates)",
                    dtos.size(), clinic.getClinicCode(), stats.inserted, stats.updated,
                    stats.unchanged, stats.stale, stats.duplicates);
        } catch (Exception e) {
            stats.failed++;
            log.error("Failed to fetch documents from clinic {}: {}", clinic.getClinicCode(), e.getMessage());
        }
        return stats;
    }

    private Stats reconcileProcedureLogs(Clinic clinic) {
        return reconcileProcedureLogs(clinic, Map.of());
    }

    /** Full read with no filter; with one (DateTStamp) a change pull: see {@link #reconcilePatients(Clinic, List)}. */
    Stats reconcileProcedureLogs(Clinic clinic, Map<String, String> filter) {
        Stats stats = new Stats();
        boolean changesOnly = !filter.isEmpty();
        try {
            List<ProcedureLogResponse> dtos = fetchAllPages(filter, params -> client.getProcedureLogs(params, clinic.getBaseUrl(), clinic.getApiKey()));
            dtos.forEach(d -> stats.seen(d.getDateTStamp()));
            Map<Long, ProcedureLogResponse> byId = new HashMap<>();
            for (ProcedureLogResponse dto : dtos) {
                if (byId.put(dto.getProcNum(), dto) != null) {
                    stats.duplicates++;
                    log.warn("Duplicate procedurelog ProcNum={} returned by Open Dental for clinic {}",
                            dto.getProcNum(), clinic.getClinicCode());
                }
            }
            List<ProcedureLog> existing = changesOnly
                    ? procedureLogRepository.findAllById(dtos.stream().filter(d -> d.getProcNum() != null)
                            .map(d -> new ProcedureLogId(clinic.getId(), d.getProcNum())).toList())
                    : procedureLogRepository.findByIdClinicId(clinic.getId());
            Set<Long> queued = queuedLocalIds(clinic.getId(), OdSyncService.PROCEDURE_LOG);
            Map<Long, ProcedureLog> existingById = new HashMap<>();
            for (ProcedureLog pl : existing) {
                if (existingById.put(pl.getId().getProcNum(), pl) != null) {
                    stats.duplicates++;
                    log.warn("Duplicate procedurelog ProcNum={} found in Supabase for clinic {}",
                            pl.getId().getProcNum(), clinic.getClinicCode());
                }
            }
            for (ProcedureLogResponse dto : dtos) {
                Long procNum = null;
                try {
                    procNum = dto.getProcNum();
                    if (procNum == null) {
                        stats.failed++;
                        log.warn("Skipping procedurelog with null ProcNum returned by Open Dental for clinic {} "
                                + "(cannot insert/update without a primary key)", clinic.getClinicCode());
                        continue;
                    }
                    if (queued.contains(procNum)) {
                        // A local change is still waiting to reach Open Dental; don't overwrite it.
                        stats.unchanged++;
                        continue;
                    }
                    ProcedureLog stored = existingById.get(procNum);
                    if (stored == null) {
                        procedureLogRepository.save(toProcedureLogEntity(dto, clinic.getId()));
                        stats.inserted++;
                        logReconciled("procedurelog", procNum, clinic, "INSERT (missing)");
                    } else {
                        ProcedureLog incoming = toProcedureLogEntity(dto, clinic.getId());
                        if (procedureLogSignature(stored).equals(procedureLogSignature(incoming))) {
                            stats.unchanged++;
                        } else {
                            procedureLogRepository.save(incoming);
                            stats.updated++;
                            logReconciled("procedurelog", procNum, clinic, "UPDATE (changed)");
                        }
                    }
                } catch (Exception e) {
                    stats.failed++;
                    log.error("Failed to reconcile procedurelog {} from clinic {}: {}",
                            procNum, clinic.getClinicCode(), e.getMessage());
                }
            }
            for (ProcedureLog pl : changesOnly ? List.<ProcedureLog>of() : existing) {
                if (!byId.containsKey(pl.getId().getProcNum())) {
                    stats.stale++;
                    log.warn("Stale procedurelog ProcNum={} present in Supabase but missing from Open Dental (clinic {})",
                            pl.getId().getProcNum(), clinic.getClinicCode());
                }
            }
            log.info("Reconciled {} procedure logs from clinic {} ({} inserted, {} updated, {} unchanged, {} stale, {} duplicates)",
                    dtos.size(), clinic.getClinicCode(), stats.inserted, stats.updated,
                    stats.unchanged, stats.stale, stats.duplicates);
        } catch (Exception e) {
            stats.failed++;
            log.error("Failed to fetch procedure logs from clinic {}: {}", clinic.getClinicCode(), e.getMessage());
        }
        return stats;
    }

    Stats reconcileProviders(Clinic clinic) {
        Stats stats = new Stats();
        try {
            List<ProviderResponse> dtos = fetchAllPages(new HashMap<>(), params -> client.getProviders(params, clinic.getBaseUrl(), clinic.getApiKey()));
            Map<Long, ProviderResponse> byId = new HashMap<>();
            for (ProviderResponse dto : dtos) {
                if (dto.getProvNum() == null) {
                    continue;
                }
                if (byId.put(dto.getProvNum(), dto) != null) {
                    stats.duplicates++;
                    log.warn("Duplicate provider ProvNum={} returned by Open Dental for clinic {}",
                            dto.getProvNum(), clinic.getClinicCode());
                }
            }
            List<Provider> existing = providerRepository.findByIdClinicId(clinic.getId());
            Map<Long, Provider> existingById = new HashMap<>();
            for (Provider p : existing) {
                if (existingById.put(p.getId().getProvNum(), p) != null) {
                    stats.duplicates++;
                    log.warn("Duplicate provider ProvNum={} found in Supabase for clinic {}",
                            p.getId().getProvNum(), clinic.getClinicCode());
                }
            }
            for (ProviderResponse dto : dtos) {
                Long provNum = null;
                try {
                    provNum = dto.getProvNum();
                    if (provNum == null) {
                        stats.failed++;
                        log.warn("Skipping provider with null ProvNum returned by Open Dental for clinic {} "
                                + "(cannot insert/update without a primary key)", clinic.getClinicCode());
                        continue;
                    }
                    Provider stored = existingById.get(provNum);
                    if (stored == null) {
                        providerRepository.save(toProviderEntity(dto, clinic.getId()));
                        stats.inserted++;
                        logReconciled("provider", provNum, clinic, "INSERT (missing)");
                    } else {
                        Provider incoming = toProviderEntity(dto, clinic.getId());
                        if (providerSignature(stored).equals(providerSignature(incoming))) {
                            stats.unchanged++;
                        } else {
                            providerRepository.save(incoming);
                            stats.updated++;
                            logReconciled("provider", provNum, clinic, "UPDATE (changed)");
                        }
                    }
                } catch (Exception e) {
                    stats.failed++;
                    log.error("Failed to reconcile provider {} from clinic {}: {}",
                            provNum, clinic.getClinicCode(), e.getMessage());
                }
            }
            for (Provider p : existing) {
                if (!byId.containsKey(p.getId().getProvNum())) {
                    stats.stale++;
                    log.warn("Stale provider ProvNum={} present in Supabase but missing from Open Dental (clinic {})",
                            p.getId().getProvNum(), clinic.getClinicCode());
                }
            }
            log.info("Reconciled {} providers from clinic {} ({} inserted, {} updated, {} unchanged, {} stale, {} duplicates)",
                    dtos.size(), clinic.getClinicCode(), stats.inserted, stats.updated,
                    stats.unchanged, stats.stale, stats.duplicates);
        } catch (Exception e) {
            stats.failed++;
            log.error("Failed to fetch providers from clinic {}: {}", clinic.getClinicCode(), e.getMessage());
        }
        return stats;
    }
    Stats reconcileOperatories(Clinic clinic) {
        Stats stats = new Stats();
        try {
            List<OperatoryResponse> dtos = fetchAllPages(new HashMap<>(), params -> client.getOperatories(params, clinic.getBaseUrl(), clinic.getApiKey()));
            Map<Long, OperatoryResponse> byId = new HashMap<>();
            for (OperatoryResponse dto : dtos) {
                if (dto.getOperatoryNum() == null) {
                    continue;
                }
                if (byId.put(dto.getOperatoryNum(), dto) != null) {
                    stats.duplicates++;
                    log.warn("Duplicate operatory OperatoryNum={} returned by Open Dental for clinic {}",
                            dto.getOperatoryNum(), clinic.getClinicCode());
                }
            }
            List<Operatory> existing = operatoryRepository.findByIdClinicId(clinic.getId());
            Map<Long, Operatory> existingById = new HashMap<>();
            for (Operatory o : existing) {
                if (existingById.put(o.getId().getOperatoryNum(), o) != null) {
                    stats.duplicates++;
                    log.warn("Duplicate operatory OperatoryNum={} found in Supabase for clinic {}",
                            o.getId().getOperatoryNum(), clinic.getClinicCode());
                }
            }
            for (OperatoryResponse dto : dtos) {
                Long operatoryNum = null;
                try {
                    operatoryNum = dto.getOperatoryNum();
                    if (operatoryNum == null) {
                        stats.failed++;
                        log.warn("Skipping operatory with null OperatoryNum returned by Open Dental for clinic {} "
                                + "(cannot insert/update without a primary key)", clinic.getClinicCode());
                        continue;
                    }
                    Operatory stored = existingById.get(operatoryNum);
                    if (stored == null) {
                        operatoryRepository.save(toOperatoryEntity(dto, clinic.getId()));
                        stats.inserted++;
                        logReconciled("operatory", operatoryNum, clinic, "INSERT (missing)");
                    } else {
                        Operatory incoming = toOperatoryEntity(dto, clinic.getId());
                        if (operatorySignature(stored).equals(operatorySignature(incoming))) {
                            stats.unchanged++;
                        } else {
                            operatoryRepository.save(incoming);
                            stats.updated++;
                            logReconciled("operatory", operatoryNum, clinic, "UPDATE (changed)");
                        }
                    }
                } catch (Exception e) {
                    stats.failed++;
                    log.error("Failed to reconcile operatory {} from clinic {}: {}",
                            operatoryNum, clinic.getClinicCode(), e.getMessage());
                }
            }
            for (Operatory o : existing) {
                if (!byId.containsKey(o.getId().getOperatoryNum())) {
                    stats.stale++;
                    log.warn("Stale operatory OperatoryNum={} present in Supabase but missing from Open Dental (clinic {})",
                            o.getId().getOperatoryNum(), clinic.getClinicCode());
                }
            }
            log.info("Reconciled {} operatories from clinic {} ({} inserted, {} updated, {} unchanged, {} stale, {} duplicates)",
                    dtos.size(), clinic.getClinicCode(), stats.inserted, stats.updated,
                    stats.unchanged, stats.stale, stats.duplicates);
        } catch (Exception e) {
            stats.failed++;
            log.error("Failed to fetch operatories from clinic {}: {}", clinic.getClinicCode(), e.getMessage());
        }
        return stats;
    }
/**
     * Reconciles schedules. Covers both the Schedule upsert and ScheduleDeleted:
     * any schedule row present in Supabase but missing from Open Dental's current
     * snapshot is soft-deleted (is_deleted=true, deleted_at).
     */
    Stats reconcileSchedules(Clinic clinic) {
        Stats stats = new Stats();
        try {
            List<ScheduleResponse> dtos = fetchAllPages(new HashMap<>(), params -> client.getSchedules(params, clinic.getBaseUrl(), clinic.getApiKey()));
            Map<Long, ScheduleResponse> byId = new HashMap<>();
            for (ScheduleResponse dto : dtos) {
                if (dto.getScheduleNum() == null) {
                    continue;
                }
                if (byId.put(dto.getScheduleNum(), dto) != null) {
                    stats.duplicates++;
                    log.warn("Duplicate schedule ScheduleNum={} returned by Open Dental for clinic {}",
                            dto.getScheduleNum(), clinic.getClinicCode());
                }
            }
            List<Schedule> existing = scheduleRepository.findByIdClinicId(clinic.getId());
            Map<Long, Schedule> existingById = new HashMap<>();
            for (Schedule s : existing) {
                if (existingById.put(s.getId().getScheduleNum(), s) != null) {
                    stats.duplicates++;
                    log.warn("Duplicate schedule ScheduleNum={} found in Supabase for clinic {}",
                            s.getId().getScheduleNum(), clinic.getClinicCode());
                }
            }
            for (ScheduleResponse dto : dtos) {
                Long scheduleNum = null;
                try {
                    scheduleNum = dto.getScheduleNum();
                    if (scheduleNum == null) {
                        stats.failed++;
                        log.warn("Skipping schedule with null ScheduleNum returned by Open Dental for clinic {} "
                                + "(cannot insert/update without a primary key)", clinic.getClinicCode());
                        continue;
                    }
                    Schedule stored = existingById.get(scheduleNum);
                    if (stored == null) {
                        scheduleRepository.save(toScheduleEntity(dto, clinic.getId()));
                        stats.inserted++;
                        logReconciled("schedule", scheduleNum, clinic, "INSERT (missing)");
                    } else {
                        Schedule incoming = toScheduleEntity(dto, clinic.getId());
                        if (scheduleSignature(stored).equals(scheduleSignature(incoming))) {
                            stats.unchanged++;
                        } else {
                            scheduleRepository.save(incoming);
                            stats.updated++;
                            logReconciled("schedule", scheduleNum, clinic, "UPDATE (changed)");
                        }
                    }
                } catch (Exception e) {
                    stats.failed++;
                    log.error("Failed to reconcile schedule {} from clinic {}: {}",
                            scheduleNum, clinic.getClinicCode(), e.getMessage());
                }
            }
            for (Schedule s : existing) {
                if (!byId.containsKey(s.getId().getScheduleNum())) {
                    stats.stale++;
                    if (!Boolean.TRUE.equals(s.getIsDeleted())) {
                        s.setIsDeleted(true);
                        s.setDeletedAt(LocalDateTime.now());
                        scheduleRepository.save(s);
                        logReconciled("schedule", s.getId().getScheduleNum(), clinic, "SOFT-DELETE (missing in Open Dental)");
                    } else {
                        log.warn("Stale schedule ScheduleNum={} already soft-deleted (clinic {})",
                                s.getId().getScheduleNum(), clinic.getClinicCode());
                    }
                }
            }
            log.info("Reconciled {} schedules from clinic {} ({} inserted, {} updated, {} unchanged, {} stale, {} duplicates)",
                    dtos.size(), clinic.getClinicCode(), stats.inserted, stats.updated,
                    stats.unchanged, stats.stale, stats.duplicates);
        } catch (Exception e) {
            stats.failed++;
            log.error("Failed to fetch schedules from clinic {}: {}", clinic.getClinicCode(), e.getMessage());
        }
        return stats;
    }
/**
     * Reconciles tooth initial charting. Covers both the ToothInitial upsert and
     * ToothInitialDeleted: any tooth_initials row present in Supabase but missing
     * from Open Dental's current snapshot is soft-deleted (is_deleted=true).
     */
    private Stats reconcileToothInitials(Clinic clinic) {
        Stats stats = new Stats();
        try {
            List<ToothInitialResponse> dtos = fetchAllPages(new HashMap<>(), params -> client.getToothInitials(params, clinic.getBaseUrl(), clinic.getApiKey()));
            Map<Long, ToothInitialResponse> byId = new HashMap<>();
            for (ToothInitialResponse dto : dtos) {
                if (dto.getToothInitialNum() == null) {
                    continue;
                }
                if (byId.put(dto.getToothInitialNum(), dto) != null) {
                    stats.duplicates++;
                    log.warn("Duplicate tooth_initial ToothInitialNum={} returned by Open Dental for clinic {}",
                            dto.getToothInitialNum(), clinic.getClinicCode());
                }
            }
            List<ToothInitial> existing = toothInitialRepository.findByIdClinicId(clinic.getId());
            Map<Long, ToothInitial> existingById = new HashMap<>();
            for (ToothInitial t : existing) {
                if (existingById.put(t.getId().getToothInitialNum(), t) != null) {
                    stats.duplicates++;
                    log.warn("Duplicate tooth_initial ToothInitialNum={} found in Supabase for clinic {}",
                            t.getId().getToothInitialNum(), clinic.getClinicCode());
                }
            }
            for (ToothInitialResponse dto : dtos) {
                Long toothInitialNum = null;
                try {
                    toothInitialNum = dto.getToothInitialNum();
                    if (toothInitialNum == null) {
                        stats.failed++;
                        log.warn("Skipping tooth_initial with null ToothInitialNum returned by Open Dental for clinic {} "
                                + "(cannot insert/update without a primary key)", clinic.getClinicCode());
                        continue;
                    }
                    ToothInitial stored = existingById.get(toothInitialNum);
                    if (stored == null) {
                        toothInitialRepository.save(toToothInitialEntity(dto, clinic.getId()));
                        stats.inserted++;
                        logReconciled("tooth_initial", toothInitialNum, clinic, "INSERT (missing)");
                    } else {
                        ToothInitial incoming = toToothInitialEntity(dto, clinic.getId());
                        if (toothInitialSignature(stored).equals(toothInitialSignature(incoming))) {
                            stats.unchanged++;
                        } else {
                            toothInitialRepository.save(incoming);
                            stats.updated++;
                            logReconciled("tooth_initial", toothInitialNum, clinic, "UPDATE (changed)");
                        }
                    }
                } catch (Exception e) {
                    stats.failed++;
                    log.error("Failed to reconcile tooth_initial {} from clinic {}: {}",
                            toothInitialNum, clinic.getClinicCode(), e.getMessage());
                }
            }
            for (ToothInitial t : existing) {
                if (!byId.containsKey(t.getId().getToothInitialNum())) {
                    stats.stale++;
                    if (!Boolean.TRUE.equals(t.getIsDeleted())) {
                        t.setIsDeleted(true);
                        t.setDeletedAt(LocalDateTime.now());
                        toothInitialRepository.save(t);
                        logReconciled("tooth_initial", t.getId().getToothInitialNum(), clinic, "SOFT-DELETE (missing in Open Dental)");
                    } else {
                        log.warn("Stale tooth_initial ToothInitialNum={} already soft-deleted (clinic {})",
                                t.getId().getToothInitialNum(), clinic.getClinicCode());
                    }
                }
            }
            log.info("Reconciled {} tooth initials from clinic {} ({} inserted, {} updated, {} unchanged, {} stale, {} duplicates)",
                    dtos.size(), clinic.getClinicCode(), stats.inserted, stats.updated,
                    stats.unchanged, stats.stale, stats.duplicates);
        } catch (Exception e) {
            stats.failed++;
            log.error("Failed to fetch tooth initials from clinic {}: {}", clinic.getClinicCode(), e.getMessage());
        }
        return stats;
    }
    // ========================================================================
    // Difference-detection helpers
    // ========================================================================

    private void logSummary(String entity, Clinic clinic, Stats s) {
        log.info("RECON entity={} clinic={} inserted={} updated={} unchanged={} stale={} duplicates={} failed={}",
                entity, clinic.getClinicCode(), s.inserted, s.updated,
                s.unchanged, s.stale, s.duplicates, s.failed);
    }

    private void logReconciled(String entity, Long id, Clinic clinic, String action) {
        log.info("RECON_UPSERT integrationId={} entity={} recordId={} action={}",
                clinic.getId(), entity, id, action);
    }

    private static String nvl(Object o) {
        return o == null ? "" : o.toString();
    }

    private String patientSignature(Patient p) {
        return nvl(p.getId().getPatNum()) + "|" + nvl(p.getLName()) + "|" + nvl(p.getFName()) + "|"
                + nvl(p.getMiddleI()) + "|" + nvl(p.getPreferred()) + "|" + nvl(p.getPatStatus()) + "|"
                + nvl(p.getGender()) + "|" + nvl(p.getPosition()) + "|" + nvl(p.getBirthdate()) + "|"
                + nvl(p.getAddress()) + "|" + nvl(p.getAddress2()) + "|" + nvl(p.getCity()) + "|"
                + nvl(p.getState()) + "|" + nvl(p.getZip()) + "|" + nvl(p.getHmPhone()) + "|"
                + nvl(p.getWkPhone()) + "|" + nvl(p.getWirelessPhone()) + "|" + nvl(p.getEmail()) + "|"
                + nvl(p.getClinicNum()) + "|" + nvl(p.getClinicAbbr()) + "|" + nvl(p.getHasIns()) + "|"
                + nvl(p.getPriProv()) + "|" + nvl(p.getSecProv()) + "|" + nvl(p.getEstBalance()) + "|"
                + nvl(p.getBalTotal()) + "|" + nvl(p.getSsn()) + "|" + nvl(p.getGuarantor()) + "|"
                + nvl(p.getFeeSched()) + "|" + nvl(p.getBillingType()) + "|" + nvl(p.getChartNumber()) + "|"
                + nvl(p.getMedicaidId()) + "|" + nvl(p.getEmployerNum()) + "|" + nvl(p.getDateFirstVisit()) + "|"
                + nvl(p.getPremed()) + "|" + nvl(p.getWard()) + "|" + nvl(p.getPreferConfirmMethod()) + "|"
                + nvl(p.getPreferContactMethod()) + "|" + nvl(p.getPreferRecallMethod()) + "|"
                + nvl(p.getLanguage()) + "|" + nvl(p.getAdmitDate()) + "|" + nvl(p.getSiteNum()) + "|"
                + nvl(p.getSiteDesc()) + "|" + nvl(p.getSuperFamily()) + "|" + nvl(p.getTxtMsgOk()) + "|"
                + nvl(p.getSecUserNumEntry()) + "|" + nvl(p.getSecDateEntry()) + "|"
                + nvl(p.getBal030()) + "|" + nvl(p.getBal3160()) + "|" + nvl(p.getBal6190()) + "|"
                + nvl(p.getBalOver90()) + "|" + nvl(p.getInsEst()) + "|" + nvl(p.getDateTimeLastAging());
    }

    private String clinicSignature(OdClinic c) {
        return nvl(c.getId().getClinicNum()) + "|" + nvl(c.getAbbr()) + "|" + nvl(c.getDescription());
    }

    private String appointmentSignature(Appointment a) {
        return nvl(a.getId().getAptNum()) + "|" + nvl(a.getPatNum()) + "|" + nvl(a.getAptStatus()) + "|"
                + nvl(a.getAptDateTime()) + "|" + nvl(a.getProvNum()) + "|" + nvl(a.getProvHyg()) + "|"
                + nvl(a.getOp()) + "|" + nvl(a.getNote()) + "|" + nvl(a.getClinicNum()) + "|"
                + nvl(a.getConfirmed()) + "|" + nvl(a.getIsNewPatient()) + "|" + nvl(a.getNextAptNum()) + "|"
                + nvl(a.getUnschedStatus()) + "|" + nvl(a.getInsPlan1()) + "|" + nvl(a.getInsPlan2());
    }

    private String documentSignature(Document d) {
        return nvl(d.getId().getDocNum()) + "|" + nvl(d.getDescription()) + "|" + nvl(d.getNote()) + "|"
                + nvl(d.getFileName()) + "|" + nvl(d.getImgType()) + "|" + nvl(d.getDocCategory()) + "|"
                + nvl(d.getProvNum());
    }

    private String procedureLogSignature(ProcedureLog pl) {
        return nvl(pl.getId().getProcNum()) + "|" + nvl(pl.getAptNum()) + "|" + nvl(pl.getProcDate()) + "|"
                + nvl(pl.getProcFee()) + "|" + nvl(pl.getProcStatus()) + "|" + nvl(pl.getProvNum()) + "|"
                + nvl(pl.getCodeNum()) + "|" + nvl(pl.getProcCode()) + "|" + nvl(pl.getDescript()) + "|"
                + nvl(pl.getClinicNum()) + "|" + nvl(pl.getPlaceService()) + "|" + nvl(pl.getDx()) + "|"
                + nvl(pl.getUnitQty());
    }

    /**
     * Aggregated difference-detection statistics for a single entity type.
     */
    /** Open Dental returns at most this many rows per list call. */
    static final int OD_PAGE_SIZE = 100;
    private static final int MAX_PAGES = 10_000;

    /**
     * Walks every page of an Open Dental list. The first call is made with the given
     * parameters as-is; later calls add {@code Offset}. Stops on a short or empty page,
     * or when an endpoint that ignores {@code Offset} returns the same page again.
     */
    static <T> List<T> fetchAllPages(Map<String, String> params, java.util.function.Function<Map<String, String>, List<T>> fetch) {
        List<T> all = new ArrayList<>();
        List<T> previous = null;
        int offset = 0;
        for (int page = 0; page < MAX_PAGES; page++) {
            Map<String, String> pageParams = new HashMap<>(params);
            if (offset > 0) {
                pageParams.put("Offset", String.valueOf(offset));
            }
            List<T> rows = fetch.apply(pageParams);
            if (rows == null || rows.isEmpty() || rows.equals(previous)) {
                break;
            }
            all.addAll(rows);
            if (rows.size() < OD_PAGE_SIZE) {
                break;
            }
            offset += rows.size();
            previous = rows;
        }
        return all;
    }

    static final class Stats {
        int inserted;
        int updated;
        int unchanged;
        int stale;
        int duplicates;
        int failed;
        /** Newest Open Dental DateTStamp among the records read ("yyyy-MM-dd HH:mm:ss" sorts as text). */
        String latestStamp;

        void seen(String dateTStamp) {
            if (dateTStamp != null && !dateTStamp.isBlank() && !dateTStamp.startsWith("0001")
                    && (latestStamp == null || dateTStamp.compareTo(latestStamp) > 0)) {
                latestStamp = dateTStamp;
            }
        }

        void add(Stats other) {
            inserted += other.inserted;
            updated += other.updated;
            unchanged += other.unchanged;
            stale += other.stale;
            duplicates += other.duplicates;
            failed += other.failed;
        }

        int synced() {
            return inserted + updated;
        }

        int processed() {
            return inserted + updated + unchanged + stale + duplicates + failed;
        }
    }

    // ========================================================================
    // Entity mapping helpers (per-clinic)
    // ========================================================================

    @Transactional
    protected void savePatientToDb(PatientResponse dto, UUID clinicId) {
        Patient patient = toPatientEntity(dto, clinicId);
        patientRepository.save(patient);
    }

    @Transactional
    protected void saveAppointmentToDb(AppointmentResponse dto, UUID clinicId) {
        Appointment appointment = toAppointmentEntity(dto, clinicId);
        appointmentRepository.save(appointment);
    }

    @Transactional
    protected void saveDocumentToDb(DocumentResponse dto, UUID clinicId) {
        Document document = toDocumentEntity(dto, clinicId);
        documentRepository.save(document);
    }

    @Transactional
    protected void saveProcedureLogToDb(ProcedureLogResponse dto, UUID clinicId) {
        ProcedureLog procedureLog = toProcedureLogEntity(dto, clinicId);
        procedureLogRepository.save(procedureLog);
    }

    /** Records with changes still queued for Open Dental (see OdSyncService). */
    private Set<Long> queuedLocalIds(UUID clinicId, String entityType) {
        if (odSyncTaskRepository == null) {
            return Set.of();
        }
        return odSyncTaskRepository.findLocalIds(clinicId, entityType, OdSyncTaskRepository.OPEN);
    }

    // --- Open Dental Clinic mapping ---

    private OdClinic toOdClinicEntity(ClinicResponse dto, UUID clinicId) {
        return OdClinic.builder()
                .id(new OdClinicId(clinicId, dto.getClinicNum()))
                .abbr(dto.getAbbr())
                .description(dto.getDescription())
                .build();
    }

    // --- Patient mapping ---

    Patient toPatientEntity(PatientResponse dto, UUID clinicId) {
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

    // --- Appointment mapping ---

    Appointment toAppointmentEntity(AppointmentResponse dto, UUID clinicId) {
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

    // --- Document mapping ---

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

    Document toDocumentEntity(DocumentResponse dto, UUID clinicId) {
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

    // --- ProcedureLog mapping ---

    ProcedureLog toProcedureLogEntity(ProcedureLogResponse dto, UUID clinicId) {
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

    private String patFieldSignature(PatField p) {
        return nvl(p.getId().getPatFieldNum()) + "|" + nvl(p.getFieldName()) + "|" + nvl(p.getFieldValue()) + "|"
                + nvl(p.getFieldDesc()) + "|" + nvl(p.getFieldType()) + "|" + nvl(p.getClinicNum());
    }

    // --- Schedule mapping ---

    private Schedule toScheduleEntity(ScheduleResponse dto, UUID clinicId) {
        Schedule schedule = new Schedule();
        schedule.setId(new ScheduleId(clinicId, dto.getScheduleNum()));
        schedule.setSchedTypeNum(dto.getSchedTypeNum());
        schedule.setProvNum(dto.getProvNum());
        schedule.setClinicNum(dto.getClinicNum());
        schedule.setStartTime(dto.getStartTime());
        schedule.setStopTime(dto.getStopTime());
        schedule.setBlockout(dto.getBlockout());
        if (dto.getSchedDate() != null && !dto.getSchedDate().isEmpty() && !dto.getSchedDate().equals("0001-01-01")) {
            try {
                schedule.setSchedDate(LocalDate.parse(dto.getSchedDate(), DATE_FORMAT));
            } catch (Exception e) {
                log.warn("Failed to parse SchedDate '{}'", dto.getSchedDate());
            }
        }
        return schedule;
    }

    private String scheduleSignature(Schedule s) {
        return nvl(s.getId().getScheduleNum()) + "|" + nvl(s.getSchedDate()) + "|" + nvl(s.getSchedTypeNum())
                + "|" + nvl(s.getProvNum()) + "|" + nvl(s.getClinicNum()) + "|" + nvl(s.getStartTime())
                + "|" + nvl(s.getStopTime()) + "|" + nvl(s.getBlockout());
    }

    // --- ToothInitial mapping ---

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

    private String toothInitialSignature(ToothInitial t) {
        return nvl(t.getId().getToothInitialNum()) + "|" + nvl(t.getPatNum()) + "|" + nvl(t.getToothNum())
                + "|" + nvl(t.getToothType()) + "|" + nvl(t.getToothGroup()) + "|" + nvl(t.getMobility())
                + "|" + nvl(t.getDateTStamp());
    }

    // --- Provider mapping ---

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

    private String providerSignature(Provider p) {
        return nvl(p.getId().getProvNum()) + "|" + nvl(p.getAbbrev()) + "|" + nvl(p.getFName()) + "|"
                + nvl(p.getLName()) + "|" + nvl(p.getSuffix()) + "|" + nvl(p.getSpecialty()) + "|"
                + nvl(p.getProvStatus()) + "|" + nvl(p.getProvType()) + "|" + nvl(p.getClinicNum());
    }

    // --- Operatory mapping ---

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

    private String operatorySignature(Operatory o) {
        return nvl(o.getId().getOperatoryNum()) + "|" + nvl(o.getAbbrev()) + "|" + nvl(o.getDescription())
                + "|" + nvl(o.getClinicNum()) + "|" + nvl(o.getIsHygiene()) + "|" + nvl(o.getIsDisabled())
                + "|" + nvl(o.getIsWebSched()) + "|" + nvl(o.getOrderValue());
    }

    // ========================================================================
    // Result record
    // ========================================================================

    public record ReconciliationResult(int syncedCount, int failedCount, int entityCount, String message) {
        public static ReconciliationResult failure(String message) {
            return new ReconciliationResult(0, 0, 0, message);
        }
    }
}