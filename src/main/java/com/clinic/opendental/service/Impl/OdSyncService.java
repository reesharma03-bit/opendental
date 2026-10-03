package com.clinic.opendental.service.Impl;

import com.clinic.opendental.client.OpenDentalClient;
import com.clinic.opendental.dto.appointment.*;
import com.clinic.opendental.dto.document.DocumentResponse;
import com.clinic.opendental.dto.document.SetByUrlRequest;
import com.clinic.opendental.dto.document.UpdateDocumentRequest;
import com.clinic.opendental.dto.document.UploadDocumentRequest;
import com.clinic.opendental.dto.patient.CreatePatientRequest;
import com.clinic.opendental.dto.patient.PatientResponse;
import com.clinic.opendental.dto.patient.UpdatePatientRequest;
import com.clinic.opendental.dto.procedurelog.CreateProcedureLogRequest;
import com.clinic.opendental.dto.procedurelog.InsuranceHistoryRequest;
import com.clinic.opendental.dto.procedurelog.ProcedureLogResponse;
import com.clinic.opendental.dto.procedurelog.UpdateProcedureLogRequest;
import com.clinic.opendental.exception.ApiException;
import com.clinic.opendental.model.*;
import com.clinic.opendental.repository.*;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongConsumer;

/**
 * "Our database first, then Open Dental".
 *
 * <p>Every change made through the API is written to Supabase and, in the same
 * transaction, recorded in {@code od_sync_queue}. The change is then pushed to
 * Open Dental straight away when it is reachable; otherwise the scheduler keeps
 * retrying with backoff ({@link #pushDue()}).</p>
 *
 * <p>A record created locally has no Open Dental key yet, so it is stored under a
 * temporary negative key equal to {@code -<queue id>}. Once Open Dental assigns
 * the real key the row, the rows that point at it and any later queued changes
 * are moved to the real key.</p>
 *
 * <p>Changes to one record are pushed strictly in order, and a change that refers
 * to a record Open Dental has not created yet (an appointment for a new patient)
 * waits until that record has been created.</p>
 */
@Service
@Slf4j
public class OdSyncService {

    public static final String PATIENT = "patient";
    public static final String APPOINTMENT = "appointment";
    public static final String DOCUMENT = "document";
    public static final String PROCEDURE_LOG = "procedurelog";

    public static final String CREATE = "CREATE";
    public static final String UPDATE = "UPDATE";
    public static final String DELETE = "DELETE";
    public static final String PLANNED = "PLANNED";
    public static final String SCHEDULE_PLANNED = "SCHEDULE_PLANNED";
    public static final String WEBSCHED = "WEBSCHED";
    public static final String BREAK = "BREAK";
    public static final String NOTE = "NOTE";
    public static final String CONFIRM = "CONFIRM";
    public static final String SET_BY_URL = "SET_BY_URL";
    public static final String INSURANCE_HISTORY = "INSURANCE_HISTORY";

    private static final int BATCH_SIZE = 200;
    private static final int WAIT_MINUTES = 1;
    private static final int MAX_BACKOFF_MINUTES = 60;
    private static final int STALE_IN_PROGRESS_MINUTES = 10;

    /**
     * Request DTOs are stored by field name so they read back exactly as written,
     * independent of the global snake_case naming and the DTOs' JSON aliases.
     */
    private static final ObjectMapper PAYLOAD_MAPPER = JsonMapper.builder()
            .visibility(PropertyAccessor.ALL, Visibility.NONE)
            .visibility(PropertyAccessor.FIELD, Visibility.ANY)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    private final OdSyncTaskRepository tasks;
    private final ClinicRepository clinicRepository;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final DocumentRepository documentRepository;
    private final ProcedureLogRepository procedureLogRepository;
    private final OpenDentalClient client;
    private final ReconciliationSyncService mappers;
    private final ResourceRecordStore records;
    private final EntityManager entityManager;
    private final TransactionTemplate tx;
    private final boolean enabled;
    private final int maxAttempts;

    public OdSyncService(OdSyncTaskRepository tasks,
                         ClinicRepository clinicRepository,
                         PatientRepository patientRepository,
                         AppointmentRepository appointmentRepository,
                         DocumentRepository documentRepository,
                         ProcedureLogRepository procedureLogRepository,
                         OpenDentalClient client,
                         ReconciliationSyncService mappers,
                         ResourceRecordStore records,
                         EntityManager entityManager,
                         PlatformTransactionManager transactionManager,
                         @Value("${od-sync.enabled:true}") boolean enabled,
                         @Value("${od-sync.max-attempts:50}") int maxAttempts) {
        this.tasks = tasks;
        this.clinicRepository = clinicRepository;
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.documentRepository = documentRepository;
        this.procedureLogRepository = procedureLogRepository;
        this.client = client;
        this.mappers = mappers;
        this.records = records;
        this.entityManager = entityManager;
        this.tx = new TransactionTemplate(transactionManager);
        this.enabled = enabled;
        this.maxAttempts = maxAttempts;
    }

    // ========================================================================
    // Recording local changes
    // ========================================================================

    /**
     * Saves a new record under a temporary key and queues its creation in Open Dental.
     *
     * @param saveLocal writes the row to our table under the temporary key it is given
     * @return the queue id; the temporary key is its negation
     */
    public long recordCreate(UUID clinicId, String entityType, String operation, Object request,
                             LongConsumer saveLocal) {
        return Objects.requireNonNull(tx.execute(status -> {
            OdSyncTask task = tasks.saveAndFlush(newTask(clinicId, entityType, operation, 0L, request));
            long tempId = -task.getId();
            task.setLocalId(tempId);
            tasks.save(task);
            saveLocal.accept(tempId);
            return task.getId();
        }));
    }

    /**
     * Applies a change to an existing local record and queues it for Open Dental.
     *
     * @return the queue id
     */
    public long recordChange(UUID clinicId, String entityType, String operation, long localId, Object request,
                             Runnable applyLocal) {
        return Objects.requireNonNull(tx.execute(status -> {
            applyLocal.run();
            return tasks.save(newTask(clinicId, entityType, operation, localId, request)).getId();
        }));
    }

    /**
     * Deletes a local record and queues the delete for Open Dental. A record Open Dental
     * never received is simply dropped together with its queued changes.
     *
     * @return the queue id, or {@code null} when nothing has to reach Open Dental
     */
    public Long recordDelete(UUID clinicId, String entityType, long localId, Runnable deleteLocal) {
        return tx.execute(status -> {
            deleteLocal.run();
            if (localId < 0) {
                OdSyncTask creator = tasks.findById(-localId).orElse(null);
                boolean sent = creator != null
                        && (OdSyncTask.IN_PROGRESS.equals(creator.getStatus()) || OdSyncTask.DONE.equals(creator.getStatus()));
                if (!sent) {
                    tasks.cancelForRecord(clinicId, entityType, localId, OdSyncTaskRepository.OPEN);
                    return null;
                }
            }
            return tasks.save(newTask(clinicId, entityType, DELETE, localId, null)).getId();
        });
    }

    /**
     * Makes sure we hold a copy of a record before changing it locally. A record Open
     * Dental has but we have not synced yet is copied in first, with the records it
     * points to.
     */
    public void ensureStored(UUID clinicId, String entityType, Long id) {
        if (id == null) {
            throw notFound(entityType, null);
        }
        boolean stored = switch (entityType) {
            case PATIENT -> patientRepository.existsById(new PatientId(clinicId, id));
            case APPOINTMENT -> appointmentRepository.existsById(new AppointmentId(clinicId, id));
            case DOCUMENT -> documentRepository.existsById(new DocumentId(clinicId, id));
            case PROCEDURE_LOG -> procedureLogRepository.existsById(new ProcedureLogId(clinicId, id));
            default -> throw new IllegalArgumentException("Unknown record type " + entityType);
        };
        if (stored) {
            return;
        }
        if (id < 0) {
            throw notFound(entityType, id);
        }
        Clinic clinic = clinicRepository.findById(clinicId).orElseThrow(() -> notFound(entityType, id));
        String baseUrl = clinic.getBaseUrl();
        try {
            switch (entityType) {
                case PATIENT -> patientRepository.save(mappers.toPatientEntity(client.getPatient(id, baseUrl), clinicId));
                case APPOINTMENT -> {
                    Appointment appointment = mappers.toAppointmentEntity(client.getAppointment(id, baseUrl), clinicId);
                    ensureStored(clinicId, PATIENT, appointment.getPatNum());
                    appointmentRepository.save(appointment);
                }
                case DOCUMENT -> {
                    Document document = mappers.toDocumentEntity(client.getDocument(id, baseUrl), clinicId);
                    ensureStored(clinicId, PATIENT, document.getPatNum());
                    documentRepository.save(document);
                }
                default -> {
                    ProcedureLog procedureLog = mappers.toProcedureLogEntity(client.getProcedureLog(id, baseUrl), clinicId);
                    ensureStored(clinicId, PATIENT, procedureLog.getPatNum());
                    if (procedureLog.getAptNum() != null) {
                        ensureStored(clinicId, APPOINTMENT, procedureLog.getAptNum());
                    }
                    procedureLogRepository.save(procedureLog);
                }
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Could not copy {} {} from Open Dental: {}", entityType, id, e.getMessage());
            throw notFound(entityType, id);
        }
    }

    private static ApiException notFound(String entityType, Long id) {
        return new ApiException(HttpStatus.NOT_FOUND,
                "No " + entityType + " " + id + " in our database or Open Dental");
    }

    /** True while a change to this record is still waiting to reach Open Dental. */
    public boolean hasQueuedChanges(UUID clinicId, String entityType, Long localId) {
        return localId != null && tasks.existsForRecord(clinicId, entityType, localId, OdSyncTaskRepository.OPEN);
    }

    /** Copies a request into another DTO with the same field names (e.g. create to update). */
    public static <T> T convert(Object request, Class<T> type) {
        return PAYLOAD_MAPPER.convertValue(request, type);
    }

    // ========================================================================
    // Pushing to Open Dental
    // ========================================================================

    /**
     * Tries to push one queued change right away, without failing the caller when
     * Open Dental is unreachable (the scheduler retries it).
     *
     * @return the record's current key: the Open Dental key once a create went through,
     *         otherwise the key it is stored under locally
     */
    public long pushNow(long taskId) {
        if (enabled) {
            try {
                process(taskId);
            } catch (Exception e) {
                log.error("Pushing queued change {} to Open Dental failed: {}", taskId, e.getMessage());
            }
        }
        return tasks.findById(taskId)
                .map(t -> OdSyncTask.DONE.equals(t.getStatus()) && t.getOdId() != null && t.getLocalId() < 0
                        ? t.getOdId() : t.getLocalId())
                .orElseThrow(() -> new IllegalStateException("Queued change " + taskId + " disappeared"));
    }

    /** Pushes every change that is due. Called by the scheduler. */
    public int pushDue() {
        if (!enabled) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        tx.executeWithoutResult(status -> {
            int released = tasks.releaseStale(now.minusMinutes(STALE_IN_PROGRESS_MINUTES));
            if (released > 0) {
                log.warn("Released {} queued change(s) left in progress; they will be pushed again", released);
            }
        });
        int pushed = 0;
        for (Long id : tasks.findDueIds(now, PageRequest.of(0, BATCH_SIZE))) {
            try {
                if (process(id)) {
                    pushed++;
                }
            } catch (Exception e) {
                log.error("Pushing queued change {} to Open Dental failed: {}", id, e.getMessage());
            }
        }
        return pushed;
    }

    /** Puts a failed change back in the queue. */
    public OdSyncTask retry(long taskId) {
        OdSyncTask task = tasks.findById(taskId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "No queued change with id " + taskId));
        if (!OdSyncTask.FAILED.equals(task.getStatus())) {
            return task;
        }
        task.setStatus(OdSyncTask.PENDING);
        task.setAttempts(0);
        task.setNextAttemptAt(LocalDateTime.now());
        tasks.save(task);
        pushNow(taskId);
        return tasks.findById(taskId).orElse(task);
    }

    /** Queued changes without their request bodies (which can hold file contents). */
    public List<OdSyncTask> list(List<String> statuses, int limit) {
        List<OdSyncTask> found = tasks.findByStatusInOrderByIdDesc(statuses, PageRequest.of(0, limit));
        found.forEach(t -> t.setPayload(null));
        return found;
    }

    /** @return true when the change reached Open Dental */
    boolean process(long taskId) {
        OdSyncTask task = tasks.findById(taskId).orElse(null);
        if (task == null || !OdSyncTask.PENDING.equals(task.getStatus())) {
            return false;
        }
        if (tasks.existsEarlier(task.getClinicId(), task.getEntityType(), task.getLocalId(), task.getId(),
                OdSyncTaskRepository.OPEN)) {
            return false;
        }
        Integer claimed = tx.execute(status -> tasks.claim(taskId, LocalDateTime.now()));
        if (claimed == null || claimed == 0) {
            return false;
        }

        Clinic clinic = clinicRepository.findById(task.getClinicId()).orElse(null);
        Pushed pushed;
        try {
            if (clinic == null) {
                throw new Gone("clinic " + task.getClinicId() + " no longer exists");
            }
            pushed = send(task, clinic);
        } catch (Wait e) {
            reschedule(task, e.getMessage());
            return false;
        } catch (Gone e) {
            finish(task, OdSyncTask.CANCELLED, e.getMessage());
            return false;
        } catch (Exception e) {
            fail(task, e);
            return false;
        }

        // Open Dental has the change now. A failure from here on must not push it again.
        try {
            tx.executeWithoutResult(status -> {
                pushed.applyLocal().run();
                task.setStatus(OdSyncTask.DONE);
                task.setOdId(pushed.odId());
                task.setPayload(null);
                task.setLastError(null);
                tasks.saveAndFlush(task);
                if (task.getLocalId() < 0 && pushed.odId() != null) {
                    tasks.moveToRealId(task.getClinicId(), task.getEntityType(), task.getLocalId(), pushed.odId(),
                            OdSyncTaskRepository.OPEN);
                }
            });
        } catch (Exception e) {
            log.error("{} {} reached Open Dental (key {}) but updating our database failed: {}",
                    task.getEntityType(), task.getOperation(), pushed.odId(), e.getMessage());
            task.setStatus(OdSyncTask.DONE);
            task.setOdId(pushed.odId());
            task.setPayload(null);
            task.setLastError("Sent to Open Dental, but the local update failed: " + e.getMessage());
            tasks.save(task);
        }
        log.info("Pushed {} {} for local key {} to Open Dental (key {})",
                task.getEntityType(), task.getOperation(), task.getLocalId(), pushed.odId());
        return true;
    }

    private Pushed send(OdSyncTask task, Clinic clinic) {
        String baseUrl = clinic.getBaseUrl();
        String apiKey = clinic.getApiKey();
        UUID clinicId = task.getClinicId();
        long local = task.getLocalId();

        if (task.getEntityType().startsWith("resource:")) {
            return sendResource(task, baseUrl, apiKey);
        }
        switch (task.getEntityType() + ":" + task.getOperation()) {
            case PATIENT + ":" + CREATE -> {
                CreatePatientRequest req = read(task, CreatePatientRequest.class);
                PatientResponse res = client.createPatient(req, baseUrl, apiKey);
                Long patNum = requireKey(res == null ? null : res.getPatNum(), "PatNum");
                return new Pushed(patNum, () -> promotePatient(task, patNum,
                        () -> mappers.toPatientEntity(res, clinicId)));
            }
            case PATIENT + ":" + UPDATE -> {
                PatientResponse res = client.updatePatient(existing(local), read(task, UpdatePatientRequest.class),
                        baseUrl, apiKey);
                return new Pushed(local, () -> {
                    if (res != null && res.getPatNum() != null && isLatest(task)) {
                        patientRepository.save(mappers.toPatientEntity(res, clinicId));
                    }
                });
            }
            case APPOINTMENT + ":" + CREATE -> {
                CreateAppointmentRequest req = read(task, CreateAppointmentRequest.class);
                req.setPatNum(resolve(clinicId, PATIENT, req.getPatNum()));
                return appointmentCreated(task, client.createAppointment(req, baseUrl, apiKey));
            }
            case APPOINTMENT + ":" + PLANNED -> {
                PlannedAppointmentRequest req = read(task, PlannedAppointmentRequest.class);
                req.setPatNum(resolve(clinicId, PATIENT, req.getPatNum()));
                if (req.getProcNums() != null) {
                    req.setProcNums(req.getProcNums().stream()
                            .map(n -> resolve(clinicId, PROCEDURE_LOG, n)).toList());
                }
                return appointmentCreated(task, client.createPlannedAppointment(req, baseUrl, apiKey));
            }
            case APPOINTMENT + ":" + SCHEDULE_PLANNED -> {
                SchedulePlannedRequest req = read(task, SchedulePlannedRequest.class);
                req.setAptNum(resolve(clinicId, APPOINTMENT, req.getAptNum()));
                return appointmentCreated(task, client.schedulePlannedAppointment(req, baseUrl, apiKey));
            }
            case APPOINTMENT + ":" + WEBSCHED -> {
                WebSchedRequest req = read(task, WebSchedRequest.class);
                req.setPatNum(resolve(clinicId, PATIENT, req.getPatNum()));
                return appointmentCreated(task, client.createWebSchedAppointment(req, baseUrl, apiKey));
            }
            case APPOINTMENT + ":" + UPDATE -> {
                AppointmentResponse res = client.updateAppointment(existing(local),
                        read(task, UpdateAppointmentRequest.class), baseUrl, apiKey);
                return new Pushed(local, () -> {
                    if (res != null && res.getAptNum() != null && isLatest(task)) {
                        appointmentRepository.save(mappers.toAppointmentEntity(res, clinicId));
                    }
                });
            }
            case APPOINTMENT + ":" + BREAK -> {
                client.breakAppointment(existing(local), read(task, BreakAppointmentRequest.class), baseUrl, apiKey);
                return new Pushed(local, () -> { });
            }
            case APPOINTMENT + ":" + NOTE -> {
                client.appendNote(existing(local), read(task, NoteRequest.class), baseUrl, apiKey);
                return new Pushed(local, () -> { });
            }
            case APPOINTMENT + ":" + CONFIRM -> {
                client.confirmAppointment(existing(local), read(task, ConfirmAppointmentRequest.class), baseUrl, apiKey);
                return new Pushed(local, () -> { });
            }
            case DOCUMENT + ":" + CREATE -> {
                UploadDocumentRequest req = read(task, UploadDocumentRequest.class);
                req.setPatNum(resolve(clinicId, PATIENT, req.getPatNum()));
                return documentCreated(task, client.uploadDocument(req, baseUrl, apiKey));
            }
            case DOCUMENT + ":" + SET_BY_URL -> {
                SetByUrlRequest req = read(task, SetByUrlRequest.class);
                req.setPatNum(resolve(clinicId, PATIENT, req.getPatNum()));
                return documentCreated(task, client.setByUrl(req, baseUrl, apiKey));
            }
            case DOCUMENT + ":" + UPDATE -> {
                DocumentResponse res = client.updateDocument(existing(local), read(task, UpdateDocumentRequest.class),
                        baseUrl, apiKey);
                return new Pushed(local, () -> {
                    if (res != null && res.getDocNum() != null && isLatest(task)) {
                        documentRepository.save(mappers.toDocumentEntity(res, clinicId));
                    }
                });
            }
            case DOCUMENT + ":" + DELETE -> {
                client.deleteDocument(existing(local), baseUrl, apiKey);
                return new Pushed(local, () -> documentRepository.findById(new DocumentId(clinicId, local))
                        .ifPresent(documentRepository::delete));
            }
            case PROCEDURE_LOG + ":" + CREATE -> {
                CreateProcedureLogRequest req = read(task, CreateProcedureLogRequest.class);
                req.setPatNum(resolve(clinicId, PATIENT, req.getPatNum()));
                req.setAptNum(resolve(clinicId, APPOINTMENT, req.getAptNum()));
                req.setPlannedAptNum(resolve(clinicId, APPOINTMENT, req.getPlannedAptNum()));
                return procedureLogCreated(task, client.createProcedureLog(req, baseUrl, apiKey));
            }
            case PROCEDURE_LOG + ":" + INSURANCE_HISTORY -> {
                InsuranceHistoryRequest req = read(task, InsuranceHistoryRequest.class);
                req.setPatNum(resolve(clinicId, PATIENT, req.getPatNum()));
                return procedureLogCreated(task, client.createInsuranceHistory(req, baseUrl, apiKey));
            }
            case PROCEDURE_LOG + ":" + UPDATE -> {
                UpdateProcedureLogRequest req = read(task, UpdateProcedureLogRequest.class);
                req.setAptNum(resolve(clinicId, APPOINTMENT, req.getAptNum()));
                req.setPlannedAptNum(resolve(clinicId, APPOINTMENT, req.getPlannedAptNum()));
                ProcedureLogResponse res = client.updateProcedureLog(existing(local), req, baseUrl, apiKey);
                return new Pushed(local, () -> {
                    if (res != null && res.getProcNum() != null && isLatest(task)) {
                        procedureLogRepository.save(mappers.toProcedureLogEntity(res, clinicId));
                    }
                });
            }
            case PROCEDURE_LOG + ":" + DELETE -> {
                client.deleteProcedureLog(existing(local), baseUrl, apiKey);
                return new Pushed(local, () -> procedureLogRepository.findById(new ProcedureLogId(clinicId, local))
                        .ifPresent(procedureLogRepository::delete));
            }
            default -> throw new Gone("unsupported queued change " + task.getEntityType() + " " + task.getOperation());
        }
    }

    /** Changes to resources kept in od_resource_records (allergies, allergy definitions, ...). */
    private Pushed sendResource(OdSyncTask task, String baseUrl, String apiKey) {
        String resource = task.getEntityType().substring("resource:".length());
        OdResourceCatalog.Resource def = OdResourceCatalog.find(resource);
        OdResourceCatalog.Writable writable = OdResourceCatalog.WRITABLE.get(resource);
        if (def == null || writable == null) {
            throw new Gone(resource + " cannot be changed from the dashboard");
        }
        UUID clinicId = task.getClinicId();
        long local = task.getLocalId();
        switch (task.getOperation()) {
            case CREATE -> {
                ObjectNode body = readTree(task);
                resolvePatNum(clinicId, body);
                JsonNode res = client.sendRaw(HttpMethod.POST, "/" + resource, body, baseUrl, apiKey);
                ObjectNode created = res != null && res.isObject() ? (ObjectNode) res : null;
                Long key = created != null ? keyOf(created, def.keyField()) : null;
                if (key == null && writable.matchOnCreate() != null) {
                    created = findCreated(def, writable.matchOnCreate(), body, baseUrl, apiKey);
                    key = created != null ? keyOf(created, def.keyField()) : null;
                }
                long realKey = requireKey(key, def.keyField());
                ObjectNode fromOpenDental = created;
                return new Pushed(realKey, () -> promoteResource(task, resource, def.keyField(), realKey, fromOpenDental));
            }
            case UPDATE -> {
                ObjectNode body = readTree(task);
                resolvePatNum(clinicId, body);
                String path = writable.updateOnCollection() ? "/" + resource : "/" + resource + "/" + existing(local);
                JsonNode res = client.sendRaw(HttpMethod.PUT, path, body, baseUrl, apiKey);
                return new Pushed(local, () -> {
                    if (res != null && res.isObject() && keyOf(res, def.keyField()) != null && isLatest(task)) {
                        records.save(clinicId, resource, String.valueOf(local), (ObjectNode) res);
                    }
                });
            }
            case DELETE -> {
                client.sendRaw(HttpMethod.DELETE, "/" + resource + "/" + existing(local), null, baseUrl, apiKey);
                return new Pushed(local, () -> records.delete(clinicId, resource, String.valueOf(local)));
            }
            default -> throw new Gone("unsupported queued change " + task.getEntityType() + " " + task.getOperation());
        }
    }

    /** The record now has Open Dental's key: replace the temporary row. */
    private void promoteResource(OdSyncTask task, String resource, String keyField, long realKey, ObjectNode fromOpenDental) {
        UUID clinicId = task.getClinicId();
        String tempKey = String.valueOf(task.getLocalId());
        boolean latest = isLatest(task);
        ObjectNode local = records.find(clinicId, resource, tempKey).orElse(null);
        records.delete(clinicId, resource, tempKey);
        ObjectNode data = latest && fromOpenDental != null ? fromOpenDental : local;
        if (data != null) {
            data.put(keyField, realKey);
            records.save(clinicId, resource, String.valueOf(realKey), data);
        }
    }

    /** For creates Open Dental answers without a body: the newest record with the same value. */
    private ObjectNode findCreated(OdResourceCatalog.Resource def, String field, ObjectNode body, String baseUrl, String apiKey) {
        JsonNode wanted = body.get(field);
        if (wanted == null) {
            return null;
        }
        ObjectNode newest = null;
        for (JsonNode row : ReconciliationSyncService.fetchAllPages(java.util.Map.of(),
                params -> ResourceMirrorService.rows(client.getRaw(def.path(), params, baseUrl, apiKey)))) {
            Long key = keyOf(row, def.keyField());
            if (row.isObject() && key != null && wanted.asText().equals(row.path(field).asText())
                    && (newest == null || key > keyOf(newest, def.keyField()))) {
                newest = (ObjectNode) row;
            }
        }
        return newest;
    }

    private void resolvePatNum(UUID clinicId, ObjectNode body) {
        JsonNode patNum = body.get("PatNum");
        if (patNum != null && patNum.canConvertToLong() && patNum.asLong() < 0) {
            body.put("PatNum", resolve(clinicId, PATIENT, patNum.asLong()));
        }
    }

    private static Long keyOf(JsonNode row, String keyField) {
        JsonNode key = row.get(keyField);
        if (key == null || key.isNull()) {
            return null;
        }
        if (key.canConvertToLong()) {
            return key.asLong();
        }
        return key.asText().matches("[0-9]+") ? Long.parseLong(key.asText()) : null;
    }

    private static ObjectNode readTree(OdSyncTask task) {
        if (task.getPayload() == null) {
            throw new NotRetryable("queued change has no request body");
        }
        try {
            JsonNode node = PAYLOAD_MAPPER.readTree(task.getPayload());
            if (!node.isObject()) {
                throw new NotRetryable("queued request is not a JSON object");
            }
            return (ObjectNode) node;
        } catch (NotRetryable e) {
            throw e;
        } catch (Exception e) {
            throw new NotRetryable("queued request could not be read: " + e.getMessage());
        }
    }

    private Pushed appointmentCreated(OdSyncTask task, AppointmentResponse res) {
        Long aptNum = requireKey(res == null ? null : res.getAptNum(), "AptNum");
        return new Pushed(aptNum, () -> promoteAppointment(task, aptNum,
                () -> mappers.toAppointmentEntity(res, task.getClinicId())));
    }

    private Pushed documentCreated(OdSyncTask task, DocumentResponse res) {
        Long docNum = requireKey(res == null ? null : res.getDocNum(), "DocNum");
        return new Pushed(docNum, () -> promoteDocument(task, docNum,
                () -> mappers.toDocumentEntity(res, task.getClinicId())));
    }

    private Pushed procedureLogCreated(OdSyncTask task, ProcedureLogResponse res) {
        Long procNum = requireKey(res == null ? null : res.getProcNum(), "ProcNum");
        return new Pushed(procNum, () -> promoteProcedureLog(task, procNum,
                () -> mappers.toProcedureLogEntity(res, task.getClinicId())));
    }

    // ========================================================================
    // Moving a temporary record to the key Open Dental assigned
    // ========================================================================

    private void promotePatient(OdSyncTask task, long patNum, java.util.function.Supplier<Patient> fromOpenDental) {
        UUID clinicId = task.getClinicId();
        long tempId = task.getLocalId();
        boolean latest = isLatest(task);
        patientRepository.findById(new PatientId(clinicId, tempId)).ifPresent(temp -> {
            entityManager.detach(temp);
            temp.setId(new PatientId(clinicId, patNum));
            patientRepository.saveAndFlush(temp);
            appointmentRepository.movePatient(clinicId, tempId, patNum);
            documentRepository.movePatient(clinicId, tempId, patNum);
            procedureLogRepository.movePatient(clinicId, tempId, patNum);
            records.movePatient(clinicId, tempId, patNum);
            patientRepository.deleteById(new PatientId(clinicId, tempId));
        });
        if (latest) {
            // Picks up what Open Dental filled in (balances, provider abbreviations, ...).
            patientRepository.save(fromOpenDental.get());
        }
    }

    private void promoteAppointment(OdSyncTask task, long aptNum, java.util.function.Supplier<Appointment> fromOpenDental) {
        UUID clinicId = task.getClinicId();
        long tempId = task.getLocalId();
        boolean latest = isLatest(task);
        appointmentRepository.findById(new AppointmentId(clinicId, tempId)).ifPresent(temp -> {
            entityManager.detach(temp);
            temp.setId(new AppointmentId(clinicId, aptNum));
            appointmentRepository.saveAndFlush(temp);
            procedureLogRepository.moveAppointment(clinicId, tempId, aptNum);
            procedureLogRepository.movePlannedAppointment(clinicId, tempId, aptNum);
            appointmentRepository.deleteById(new AppointmentId(clinicId, tempId));
        });
        if (latest) {
            appointmentRepository.save(fromOpenDental.get());
        }
    }

    private void promoteDocument(OdSyncTask task, long docNum, java.util.function.Supplier<Document> fromOpenDental) {
        UUID clinicId = task.getClinicId();
        long tempId = task.getLocalId();
        boolean latest = isLatest(task);
        documentRepository.findById(new DocumentId(clinicId, tempId)).ifPresent(temp -> {
            entityManager.detach(temp);
            temp.setId(new DocumentId(clinicId, docNum));
            documentRepository.saveAndFlush(temp);
            documentRepository.deleteById(new DocumentId(clinicId, tempId));
        });
        if (latest) {
            Document fresh = fromOpenDental.get();
            if (fresh.getPatNum() == null) {
                documentRepository.findById(new DocumentId(clinicId, docNum))
                        .ifPresent(moved -> fresh.setPatNum(moved.getPatNum()));
            }
            documentRepository.save(fresh);
        }
    }

    private void promoteProcedureLog(OdSyncTask task, long procNum, java.util.function.Supplier<ProcedureLog> fromOpenDental) {
        UUID clinicId = task.getClinicId();
        long tempId = task.getLocalId();
        boolean latest = isLatest(task);
        procedureLogRepository.findById(new ProcedureLogId(clinicId, tempId)).ifPresent(temp -> {
            entityManager.detach(temp);
            temp.setId(new ProcedureLogId(clinicId, procNum));
            procedureLogRepository.saveAndFlush(temp);
            procedureLogRepository.deleteById(new ProcedureLogId(clinicId, tempId));
        });
        if (latest) {
            procedureLogRepository.save(fromOpenDental.get());
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /**
     * Maps a key that may still be temporary to the key Open Dental knows it by.
     * Throws {@link Wait} while the referenced record is not in Open Dental yet.
     */
    private Long resolve(UUID clinicId, String entityType, Long id) {
        if (id == null || id >= 0) {
            return id;
        }
        OdSyncTask creator = tasks.findById(-id).orElse(null);
        if (creator == null || !creator.getClinicId().equals(clinicId) || !creator.getEntityType().equals(entityType)) {
            throw new Gone(entityType + " " + id + " was never queued for Open Dental");
        }
        if (OdSyncTask.CANCELLED.equals(creator.getStatus())) {
            throw new Gone(entityType + " " + id + " was deleted before it reached Open Dental");
        }
        if (!OdSyncTask.DONE.equals(creator.getStatus()) || creator.getOdId() == null) {
            throw new Wait("waiting for " + entityType + " " + id + " to be created in Open Dental");
        }
        return creator.getOdId();
    }

    /** Updates and deletes run after the record's create, so the key is real by then. */
    private static long existing(long localId) {
        if (localId < 0) {
            throw new Wait("record " + localId + " is not in Open Dental yet");
        }
        return localId;
    }

    private static Long requireKey(Long key, String name) {
        if (key == null || key <= 0) {
            // Retrying could create the record twice, so this is not retried.
            throw new NotRetryable("Open Dental did not return a " + name + " for the new record");
        }
        return key;
    }

    /** No later change for the same record is waiting, so Open Dental's copy is current. */
    private boolean isLatest(OdSyncTask task) {
        return !tasks.existsLater(task.getClinicId(), task.getEntityType(), task.getLocalId(), task.getId(),
                OdSyncTaskRepository.OPEN);
    }

    private OdSyncTask newTask(UUID clinicId, String entityType, String operation, long localId, Object request) {
        return OdSyncTask.builder()
                .clinicId(clinicId)
                .entityType(entityType)
                .operation(operation)
                .localId(localId)
                .payload(request == null ? null : write(request))
                .status(OdSyncTask.PENDING)
                .attempts(0)
                .nextAttemptAt(LocalDateTime.now())
                .build();
    }

    private static String write(Object request) {
        try {
            return PAYLOAD_MAPPER.writeValueAsString(request);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not store request for Open Dental: " + e.getMessage(), e);
        }
    }

    private static <T> T read(OdSyncTask task, Class<T> type) {
        if (task.getPayload() == null) {
            throw new NotRetryable("queued change has no request body");
        }
        try {
            return PAYLOAD_MAPPER.readValue(task.getPayload(), type);
        } catch (Exception e) {
            throw new NotRetryable("queued request could not be read: " + e.getMessage());
        }
    }

    private void reschedule(OdSyncTask task, String reason) {
        task.setStatus(OdSyncTask.PENDING);
        task.setLastError(reason);
        task.setNextAttemptAt(LocalDateTime.now().plusMinutes(WAIT_MINUTES));
        tasks.save(task);
    }

    private void finish(OdSyncTask task, String status, String reason) {
        log.warn("Dropping queued {} {} for local key {}: {}", task.getEntityType(), task.getOperation(),
                task.getLocalId(), reason);
        task.setStatus(status);
        task.setLastError(reason);
        task.setPayload(null);
        tasks.save(task);
    }

    private void fail(OdSyncTask task, Exception e) {
        int attempts = task.getAttempts() + 1;
        boolean giveUp = attempts >= maxAttempts || !isRetryable(e);
        task.setAttempts(attempts);
        task.setLastError(truncate(e.getMessage()));
        task.setStatus(giveUp ? OdSyncTask.FAILED : OdSyncTask.PENDING);
        long backoff = Math.min(1L << Math.min(attempts - 1, 6), MAX_BACKOFF_MINUTES);
        task.setNextAttemptAt(LocalDateTime.now().plusMinutes(backoff));
        tasks.save(task);
        if (giveUp) {
            log.error("Gave up pushing {} {} for local key {} to Open Dental after {} attempt(s): {}",
                    task.getEntityType(), task.getOperation(), task.getLocalId(), attempts, e.getMessage());
        } else {
            log.warn("Could not push {} {} for local key {} to Open Dental (attempt {}), retrying in {} min: {}",
                    task.getEntityType(), task.getOperation(), task.getLocalId(), attempts, backoff, e.getMessage());
        }
    }

    /**
     * Open Dental rejects a request it considers invalid with 400/404; sending it again
     * will not help. Its 400 "Malformed API request." means bad credentials, which an
     * admin can fix, so that one is retried.
     */
    static boolean isRetryable(Exception e) {
        if (e instanceof NotRetryable) {
            return false;
        }
        if (e instanceof HttpClientErrorException http) {
            int code = http.getStatusCode().value();
            String text = http.getMessage() + " " + http.getResponseBodyAsString();
            if (code == 400 && text.contains("Malformed API request")) {
                return true;
            }
            return code != 400 && code != 404 && code != 422;
        }
        return true;
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }

    private record Pushed(Long odId, Runnable applyLocal) {
    }

    /** The change depends on a record Open Dental has not created yet. */
    private static class Wait extends RuntimeException {
        Wait(String message) {
            super(message);
        }
    }

    /** The change can no longer be applied (its record was deleted before reaching Open Dental). */
    private static class Gone extends RuntimeException {
        Gone(String message) {
            super(message);
        }
    }

    private static class NotRetryable extends RuntimeException {
        NotRetryable(String message) {
            super(message);
        }
    }
}
