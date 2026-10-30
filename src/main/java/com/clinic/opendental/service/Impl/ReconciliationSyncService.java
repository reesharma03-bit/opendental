package com.clinic.opendental.service.Impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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

    /** Batched writes (see {@link RowWriter}); absent in unit tests, which then save row by row. */
    @PersistenceContext
    private EntityManager entityManager;

    @Autowired(required = false)
    private PlatformTransactionManager transactionManager;

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
        return reconcileClinic(clinic, table -> true);
    }

    /** As {@link #reconcileClinic(Clinic)}, for the tables {@code include} accepts (clinics always). */
    public ReconciliationResult reconcileClinic(Clinic clinic, java.util.function.Predicate<String> include) {
        return reconcileClinic(clinic, include, (table, stats) -> { });
    }

    /**
     * As above; {@code finished} is told each table once it is done, so a stopped sync
     * ({@link SyncStoppedException}) can continue with the tables it hadn't finished.
     */
    public ReconciliationResult reconcileClinic(Clinic clinic, java.util.function.Predicate<String> include,
                                                BiConsumer<String, Stats> finished) {
        Stats total = new Stats();

        Stats clinics = reconcileClinics(clinic);
        logSummary("clinics", clinic, clinics);
        total.add(clinics);

        Map<String, Supplier<Stats>> tables = new LinkedHashMap<>();
        tables.put("patients", () -> reconcilePatients(clinic));
        tables.put("appointments", () -> reconcileAppointments(clinic));
        tables.put("documents", () -> reconcileDocuments(clinic));
        tables.put("procedurelogs", () -> reconcileProcedureLogs(clinic));
        tables.put("patfields", () -> reconcilePatFields(clinic));
        tables.put("schedules", () -> reconcileSchedules(clinic));
        tables.put("toothinitials", () -> reconcileToothInitials(clinic));
        tables.put("providers", () -> reconcileProviders(clinic));
        tables.put("operatories", () -> reconcileOperatories(clinic));
        for (Map.Entry<String, Supplier<Stats>> table : tables.entrySet()) {
            if (!include.test(table.getKey())) continue;
            Stats stats = table.getValue().get();
            logSummary(table.getKey(), clinic, stats);
            total.add(stats);
            finished.accept(table.getKey(), stats);
        }

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

    /**
     * How one table is reconciled by {@link #reconcile}.
     *
     * @param table      the core table's name in Sync settings (and where a stopped sync continues)
     * @param entity     name in log lines, e.g. "procedurelog"
     * @param plural     name in summary lines, e.g. "procedure logs"
     * @param idLabel    Open Dental's key, e.g. "ProcNum"
     * @param idField    that key in the entity's embedded id, e.g. "procNum"
     * @param stamp      the record's DateTStamp (change pulls resume from the newest); null when none
     * @param queueType  od_sync_queue type whose waiting local changes are not overwritten; null when none
     * @param softDelete marks a row deleted in Open Dental (true when it changed it); null: only reported
     * @param allRows    every stored row of a clinic; only used without an EntityManager (unit tests)
     * @param parents    rows a record must refer to (its patient, its appointment), checked per page
     */
    record Table<D, E, I>(String table, String entity, String plural, String idLabel, Class<E> type, String idField,
                          Function<D, Long> dtoId, Function<D, String> stamp, BiFunction<UUID, Long, I> key,
                          BiFunction<D, UUID, E> toEntity, Function<E, Long> rowId, Function<E, String> signature,
                          CrudRepository<E, I> repository, Function<UUID, List<E>> allRows, String queueType,
                          java.util.function.Predicate<E> softDelete, List<Parent<E>> parents) {
        Table {
            parents = parents == null ? List.of() : parents;
        }
    }

    /**
     * A row a record refers to by foreign key. A record whose parent isn't stored (its screen isn't
     * synced, or it hasn't arrived yet) would fail its whole batch: an optional link ({@code clear})
     * is saved empty and filled in by a later sync once the parent is there; a required one skips
     * the record.
     */
    record Parent<E>(String name, Class<?> type, String idField, Function<E, Long> ref, Consumer<E> clear) {
    }

    /** Hands {@link #reconcile} the records page by page, from row {@code start} on. */
    interface Pages<D> {
        void read(int start, Consumer<List<D>> page, Stats stats);
    }

    /** A paged Open Dental list. */
    private static <D> Pages<D> paged(Map<String, String> params, Function<Map<String, String>, List<D>> fetch) {
        return (start, page, stats) -> fetchPages(params, start, fetch, page);
    }

    /** Records already read (a change pull's list, or an endpoint without paging). */
    private static <D> Pages<D> given(Supplier<List<D>> records) {
        return (start, page, stats) -> {
            List<D> list = records.get();
            if (list != null && !list.isEmpty()) page.accept(list);
        };
    }

    /**
     * Reconciles one table page by page: each page of Open Dental records is compared with the
     * stored rows of the same ids and written right away, so memory holds one page (plus the ids
     * seen) however large the practice. With {@code full}, rows stored but no longer in Open Dental
     * are then reported stale (or soft-deleted). A Force Sync stopped part-way continues after the
     * last page it wrote.
     */
    private <D, E, I> Stats reconcile(Clinic clinic, Table<D, E, I> t, Pages<D> pages, boolean full) {
        Stats stats = new Stats();
        RowWriter<E> writer = writer(t.entity(), clinic, stats, t.repository(), t.rowId());
        CoreProgress progress = progress();
        int start = full && progress != null ? progress.startAt(t.table()) : 0;
        Set<Long> seen = new HashSet<>();
        Set<Long> queued = t.queueType() == null ? Set.of() : queuedLocalIds(clinic.getId(), t.queueType());
        int[] read = {0};
        try {
            pages.read(start, page -> {
                read[0] += page.size();
                if (progress != null) progress.read = start + read[0];
                savePage(clinic, t, page, seen, queued, writer, stats);
                writer.commit();
                if (progress != null) progress.done = start + read[0];
            }, stats);
            writer.commit();
            if (full && start == 0) {
                markStale(clinic, t, seen, stats);
            } else if (start > 0) {
                log.info("{} of clinic {} continued after row {}; records deleted in Open Dental are found on the next full sync",
                        t.plural(), clinic.getClinicCode(), start);
            }
            log.info("Reconciled {} {} from clinic {} ({} inserted, {} updated, {} unchanged, {} stale, {} duplicates)",
                    start + read[0], t.plural(), clinic.getClinicCode(), stats.inserted, stats.updated,
                    stats.unchanged, stats.stale, stats.duplicates);
        } catch (SyncStoppedException e) {
            throw e;
        } catch (Exception e) {
            stats.failed++;
            log.error("Failed to fetch {} from clinic {}: {}", t.plural(), clinic.getClinicCode(), e.getMessage());
        }
        return stats;
    }

    private <D, E, I> void savePage(Clinic clinic, Table<D, E, I> t, List<D> page, Set<Long> seen, Set<Long> queued,
                                    RowWriter<E> writer, Stats stats) {
        UUID clinicId = clinic.getId();
        Map<Long, E> incoming = new LinkedHashMap<>();
        for (D dto : page) {
            Long id = null;
            try {
                id = t.dtoId().apply(dto);
                if (t.stamp() != null) stats.seen(t.stamp().apply(dto));
                // A record with no usable key (e.g. a field-casing mismatch swallowing it) would
                // violate NOT NULL on insert, or look "changed" and overwrite an existing row.
                if (id == null) {
                    stats.failed++;
                    log.warn("Skipping {} with null {} returned by Open Dental for clinic {} (cannot insert/update without a primary key)",
                            t.entity(), t.idLabel(), clinic.getClinicCode());
                    continue;
                }
                if (!seen.add(id)) {
                    stats.duplicates++;
                    log.warn("Duplicate {} {}={} returned by Open Dental for clinic {}", t.entity(), t.idLabel(), id, clinic.getClinicCode());
                    continue;
                }
                if (queued.contains(id)) {
                    // A local change is still waiting to reach Open Dental; don't overwrite it.
                    stats.unchanged++;
                    continue;
                }
                incoming.put(id, t.toEntity().apply(dto, clinicId));
            } catch (Exception e) {
                stats.failed++;
                log.error("Failed to reconcile {} {} from clinic {}: {}", t.entity(), id, clinic.getClinicCode(), e.getMessage());
            }
        }
        checkParents(clinic, t, incoming, stats);
        Map<Long, E> stored = new HashMap<>();
        for (E row : t.repository().findAllById(incoming.keySet().stream().map(id -> t.key().apply(clinicId, id)).toList())) {
            stored.put(t.rowId().apply(row), row);
        }
        incoming.forEach((id, row) -> {
            E current = stored.get(id);
            if (current == null) {
                writer.insert(row);
            } else if (t.signature().apply(current).equals(t.signature().apply(row))) {
                stats.unchanged++;
            } else {
                writer.update(row);
            }
        });
    }

    /** Drops (or unlinks) records whose patient or appointment isn't stored; one query per parent per page. */
    private <D, E, I> void checkParents(Clinic clinic, Table<D, E, I> t, Map<Long, E> incoming, Stats stats) {
        if (entityManager == null || incoming.isEmpty()) return;
        for (Parent<E> parent : t.parents()) {
            List<Long> refs = incoming.values().stream().map(parent.ref()).filter(java.util.Objects::nonNull).distinct().toList();
            if (refs.isEmpty()) continue;
            Set<Long> present = new HashSet<>(entityManager.createQuery("select e.id." + parent.idField() + " from "
                            + parent.type().getSimpleName() + " e where e.id.clinicId = :clinic and e.id." + parent.idField()
                            + " in :refs", Long.class)
                    .setParameter("clinic", clinic.getId())
                    .setParameter("refs", refs)
                    .getResultList());
            int unlinked = 0;
            for (java.util.Iterator<Map.Entry<Long, E>> it = incoming.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Long, E> entry = it.next();
                Long ref = parent.ref().apply(entry.getValue());
                if (ref == null || present.contains(ref)) continue;
                if (parent.clear() != null) {
                    parent.clear().accept(entry.getValue());
                    unlinked++;
                } else {
                    it.remove();
                    stats.failed++;
                    log.warn("Skipping {} {}={} for clinic {}: its {} {} isn't in SmileOS yet (is its screen synced?)",
                            t.entity(), t.idLabel(), entry.getKey(), clinic.getClinicCode(), parent.name(), ref);
                }
            }
            if (unlinked > 0) {
                log.info("{} {} of clinic {} saved without their {} for now: it isn't in SmileOS yet (a later sync links them)",
                        unlinked, t.plural(), clinic.getClinicCode(), parent.name());
            }
        }
    }

    /** Rows stored but no longer in Open Dental: reported, or soft-deleted where the table keeps deletions. */
    private <D, E, I> void markStale(Clinic clinic, Table<D, E, I> t, Set<Long> seen, Stats stats) {
        for (Long id : storedIds(clinic, t)) {
            if (seen.contains(id)) continue;
            stats.stale++;
            if (t.softDelete() == null) {
                log.warn("Stale {} {}={} present in Supabase but missing from Open Dental (clinic {})",
                        t.entity(), t.idLabel(), id, clinic.getClinicCode());
                continue;
            }
            E row = t.repository().findById(t.key().apply(clinic.getId(), id)).orElse(null);
            if (row != null && t.softDelete().test(row)) {
                t.repository().save(row);
                logReconciled(t.entity(), id, clinic, "SOFT-DELETE (missing in Open Dental)");
            } else {
                log.warn("Stale {} {}={} already soft-deleted (clinic {})", t.entity(), t.idLabel(), id, clinic.getClinicCode());
            }
        }
    }

    /** Only the ids of the clinic's stored rows (not whole rows: a large practice has hundreds of thousands). */
    private <D, E, I> List<Long> storedIds(Clinic clinic, Table<D, E, I> t) {
        if (entityManager == null) {
            return t.allRows().apply(clinic.getId()).stream().map(t.rowId()).toList();
        }
        return entityManager.createQuery("select e.id." + t.idField() + " from " + t.type().getSimpleName()
                        + " e where e.id.clinicId = :clinic", Long.class)
                .setParameter("clinic", clinic.getId())
                .getResultList();
    }

    private static <E> java.util.function.Predicate<E> softDelete(Function<E, Boolean> isDeleted, Consumer<E> markDeleted) {
        return row -> {
            if (Boolean.TRUE.equals(isDeleted.apply(row))) return false;
            markDeleted.accept(row);
            return true;
        };
    }

    private Stats reconcileClinics(Clinic clinic) {
        return reconcile(clinic, new Table<>("clinics", "clinic", "Open Dental clinics", "ClinicNum", OdClinic.class, "clinicNum",
                ClinicResponse::getClinicNum, null, OdClinicId::new, this::toOdClinicEntity, c -> c.getId().getClinicNum(),
                this::clinicSignature, odClinicRepository, odClinicRepository::findByIdClinicId, null, null, null),
                given(() -> client.getClinics(clinic.getBaseUrl(), clinic.getApiKey())), true);
    }

    private Stats reconcilePatients(Clinic clinic) {
        return reconcilePatients(clinic, null);
    }

    /**
     * Full read when {@code changed} is null; otherwise only those patients (a change pull):
     * then nothing is reported stale.
     */
    Stats reconcilePatients(Clinic clinic, List<PatientResponse> changed) {
        Table<PatientResponse, Patient, PatientId> patients = new Table<>("patients", "patient", "patients", "PatNum",
                Patient.class, "patNum", PatientResponse::getPatNum, null, PatientId::new, this::toPatientEntity,
                p -> p.getId().getPatNum(), this::patientSignature, patientRepository, patientRepository::findByIdClinicId,
                OdSyncService.PATIENT, null, null);
        return changed != null
                ? reconcile(clinic, patients, given(() -> changed), false)
                : reconcile(clinic, patients, paged(Map.of(), params -> client.getPatients(params, clinic.getBaseUrl(), clinic.getApiKey())), true);
    }

    private Stats reconcilePatFields(Clinic clinic) {
        return reconcile(clinic, new Table<>("patfields", "pat_field", "pat_fields", "PatFieldNum", PatField.class, "patFieldNum",
                PatFieldResponse::getPatFieldNum, null, PatFieldId::new, this::toPatFieldEntity, p -> p.getId().getPatFieldNum(),
                this::patFieldSignature, patFieldRepository, patFieldRepository::findByIdClinicId, null,
                // PatFieldDeleted: one missing from Open Dental was deleted there (a missed delete webhook).
                softDelete(PatField::getIsDeleted, p -> {
                    p.setIsDeleted(true);
                    p.setDeletedAt(LocalDateTime.now());
                }), null),
                paged(new HashMap<>(), params -> client.getPatFields(params, clinic.getBaseUrl(), clinic.getApiKey())), true);
    }

    private Stats reconcileAppointments(Clinic clinic) {
        return reconcileAppointments(clinic, Map.of());
    }

    /** Full read with no filter; with one (DateTStamp) a change pull: see {@link #reconcilePatients(Clinic, List)}. */
    Stats reconcileAppointments(Clinic clinic, Map<String, String> filter) {
        return reconcile(clinic, new Table<>("appointments", "appointment", "appointments", "AptNum", Appointment.class, "aptNum",
                AppointmentResponse::getAptNum, AppointmentResponse::getDateTStamp, AppointmentId::new, this::toAppointmentEntity,
                a -> a.getId().getAptNum(), this::appointmentSignature, appointmentRepository,
                appointmentRepository::findByIdClinicId, OdSyncService.APPOINTMENT, null,
                List.of(new Parent<>("patient", Patient.class, "patNum", Appointment::getPatNum, null))),
                paged(filter, params -> client.getAppointments(params, clinic.getBaseUrl(), clinic.getApiKey())), filter.isEmpty());
    }

    /**
     * Open Dental's /documents endpoint is patient-scoped: it requires PatNum on every request
     * (empty params -> 400 "PatNum is required"), so documents are read patient by patient.
     */
    private Stats reconcileDocuments(Clinic clinic) {
        Pages<DocumentResponse> perPatient = (start, page, stats) -> fetchPages(Map.of(), 0,
                params -> client.getPatients(params, clinic.getBaseUrl(), clinic.getApiKey()), patients -> {
                    for (PatientResponse patient : patients) {
                        Long patNum = patient.getPatNum();
                        if (patNum == null) continue;
                        try {
                            fetchPages(Map.of("PatNum", String.valueOf(patNum)), 0,
                                    params -> client.getDocuments(params, clinic.getBaseUrl(), clinic.getApiKey()), page);
                        } catch (SyncStoppedException e) {
                            throw e;
                        } catch (Exception e) {
                            stats.failed++;
                            log.error("Failed to fetch documents for PatNum={} from clinic {}: {}", patNum, clinic.getClinicCode(), e.getMessage());
                        }
                    }
                });
        return reconcile(clinic, new Table<>("documents", "document", "documents", "DocNum", Document.class, "docNum",
                DocumentResponse::getDocNum, DocumentResponse::getDateTStamp, DocumentId::new, this::toDocumentEntity, d -> d.getId().getDocNum(),
                this::documentSignature, documentRepository, documentRepository::findByIdClinicId, OdSyncService.DOCUMENT, null,
                List.of(new Parent<>("patient", Patient.class, "patNum", Document::getPatNum, null))),
                perPatient, true);
    }

    private Stats reconcileProcedureLogs(Clinic clinic) {
        return reconcileProcedureLogs(clinic, Map.of());
    }

    /** Full read with no filter; with one (DateTStamp) a change pull: see {@link #reconcilePatients(Clinic, List)}. */
    Stats reconcileProcedureLogs(Clinic clinic, Map<String, String> filter) {
        return reconcile(clinic, new Table<>("procedurelogs", "procedurelog", "procedure logs", "ProcNum", ProcedureLog.class, "procNum",
                ProcedureLogResponse::getProcNum, ProcedureLogResponse::getDateTStamp, ProcedureLogId::new, this::toProcedureLogEntity,
                pl -> pl.getId().getProcNum(), this::procedureLogSignature, procedureLogRepository,
                procedureLogRepository::findByIdClinicId, OdSyncService.PROCEDURE_LOG, null,
                List.of(new Parent<>("patient", Patient.class, "patNum", ProcedureLog::getPatNum, null),
                        new Parent<>("appointment", Appointment.class, "aptNum", ProcedureLog::getAptNum, pl -> pl.setAptNum(null)))),
                paged(filter, params -> client.getProcedureLogs(params, clinic.getBaseUrl(), clinic.getApiKey())), filter.isEmpty());
    }

    Stats reconcileProviders(Clinic clinic) {
        return reconcileProviders(clinic, Map.of());
    }

    /** Full read with no filter; with one (DateTStamp) a change pull, which marks nothing stale. */
    Stats reconcileProviders(Clinic clinic, Map<String, String> filter) {
        return reconcile(clinic, new Table<>("providers", "provider", "providers", "ProvNum", Provider.class, "provNum",
                ProviderResponse::getProvNum, ProviderResponse::getDateTStamp, ProviderId::new, this::toProviderEntity,
                p -> p.getId().getProvNum(), this::providerSignature, providerRepository, providerRepository::findByIdClinicId,
                null, null, null),
                paged(filter, params -> client.getProviders(params, clinic.getBaseUrl(), clinic.getApiKey())), filter.isEmpty());
    }

    Stats reconcileOperatories(Clinic clinic) {
        return reconcile(clinic, new Table<>("operatories", "operatory", "operatories", "OperatoryNum", Operatory.class, "operatoryNum",
                OperatoryResponse::getOperatoryNum, null, OperatoryId::new, this::toOperatoryEntity, o -> o.getId().getOperatoryNum(),
                this::operatorySignature, operatoryRepository, operatoryRepository::findByIdClinicId, null, null, null),
                paged(new HashMap<>(), params -> client.getOperatories(params, clinic.getBaseUrl(), clinic.getApiKey())), true);
    }

    /**
     * Reconciles schedules. Covers both the Schedule upsert and ScheduleDeleted: any schedule row
     * present in Supabase but missing from Open Dental's current snapshot is soft-deleted.
     */
    Stats reconcileSchedules(Clinic clinic) {
        return reconcile(clinic, new Table<>("schedules", "schedule", "schedules", "ScheduleNum", Schedule.class, "scheduleNum",
                ScheduleResponse::getScheduleNum, null, ScheduleId::new, this::toScheduleEntity, s -> s.getId().getScheduleNum(),
                this::scheduleSignature, scheduleRepository, scheduleRepository::findByIdClinicId, null,
                softDelete(Schedule::getIsDeleted, s -> {
                    s.setIsDeleted(true);
                    s.setDeletedAt(LocalDateTime.now());
                }), null),
                paged(new HashMap<>(), params -> client.getSchedules(params, clinic.getBaseUrl(), clinic.getApiKey())), true);
    }

    /**
     * Reconciles tooth initial charting. Covers both the ToothInitial upsert and ToothInitialDeleted:
     * any row present in Supabase but missing from Open Dental's current snapshot is soft-deleted.
     */
    private Stats reconcileToothInitials(Clinic clinic) {
        return reconcile(clinic, new Table<>("toothinitials", "tooth_initial", "tooth initials", "ToothInitialNum", ToothInitial.class,
                "toothInitialNum", ToothInitialResponse::getToothInitialNum, null, ToothInitialId::new, this::toToothInitialEntity,
                t -> t.getId().getToothInitialNum(), this::toothInitialSignature, toothInitialRepository,
                toothInitialRepository::findByIdClinicId, null,
                softDelete(ToothInitial::getIsDeleted, t -> {
                    t.setIsDeleted(true);
                    t.setDeletedAt(LocalDateTime.now());
                }), null),
                paged(new HashMap<>(), params -> client.getToothInitials(params, clinic.getBaseUrl(), clinic.getApiKey())), true);
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

    /**
     * A value as compared in a signature. Amounts compare by value: Open Dental sends 0.0 and
     * the database gives back 0.00, which must not count as a change (it made every patient
     * look changed, and rewrote them all, on every full sync).
     */
    static String nvl(Object o) {
        if (o == null) return "";
        if (o instanceof BigDecimal d) return d.signum() == 0 ? "0" : d.stripTrailingZeros().toPlainString();
        return o.toString();
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
     * Live progress of the core table a Force Sync is copying, and whether it should stop.
     * Set for the thread running the sync; other callers (webhooks, change pulls) have none.
     */
    public static final class CoreProgress {
        final BooleanSupplier stop;
        /** Rows read from Open Dental so far. */
        public volatile int read;
        /** Rows inserted or updated in Supabase so far. */
        public volatile int written;
        /** Rows of the table read and written completely: where a stopped sync continues. */
        public volatile int done;
        /** Where each table continues (rows already done by a stopped sync); empty: from the start. */
        final Map<String, Integer> resumeAt;

        public CoreProgress(BooleanSupplier stop) {
            this(stop, Map.of());
        }

        public CoreProgress(BooleanSupplier stop, Map<String, Integer> resumeAt) {
            this.stop = stop == null ? () -> false : stop;
            this.resumeAt = resumeAt == null ? Map.of() : resumeAt;
        }

        /** A new table starts. */
        public void reset() {
            read = 0;
            written = 0;
            done = 0;
        }

        int startAt(String table) {
            return resumeAt.getOrDefault(table, 0);
        }
    }

    private static final ThreadLocal<CoreProgress> PROGRESS = new ThreadLocal<>();

    /** Runs {@code work} reporting into {@code progress} and stopping when it says so. */
    static <T> T withProgress(CoreProgress progress, Supplier<T> work) {
        PROGRESS.set(progress);
        try {
            return work.get();
        } finally {
            PROGRESS.remove();
        }
    }

    static CoreProgress progress() {
        return PROGRESS.get();
    }

    static boolean stopRequested() {
        CoreProgress progress = PROGRESS.get();
        return progress != null && progress.stop.getAsBoolean();
    }

    private <E> RowWriter<E> writer(String entity, Clinic clinic, Stats stats, CrudRepository<E, ?> repository,
                                    Function<E, Long> idOf) {
        return new RowWriter<>(entity, clinic, stats, repository, batch(), idOf,
                (id, action) -> logReconciled(entity, id, clinic, action));
    }

    /** One transaction per batch: new rows are inserted without a lookup first, changed ones merged. */
    private <E> RowWriter.Batch<E> batch() {
        if (entityManager == null || transactionManager == null) return null;
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return (inserts, updates) -> tx.executeWithoutResult(status -> {
            inserts.forEach(entityManager::persist);
            updates.forEach(entityManager::merge);
            entityManager.flush();
            entityManager.clear();
        });
    }

    /**
     * Walks every page of an Open Dental list. The first call is made with the given
     * parameters as-is; later calls add {@code Offset}. Stops on a short or empty page,
     * or when an endpoint that ignores {@code Offset} returns the same page again.
     */
    static <T> List<T> fetchAllPages(Map<String, String> params, Function<Map<String, String>, List<T>> fetch) {
        List<T> all = new ArrayList<>();
        fetchPages(params, 0, fetch, all::addAll);
        return all;
    }

    /** As {@link #fetchAllPages}, handing each page to {@code onPage} as it arrives, from row {@code start} on. */
    static <T> void fetchPages(Map<String, String> params, int start, Function<Map<String, String>, List<T>> fetch,
                               Consumer<List<T>> onPage) {
        List<T> previous = null;
        int offset = start;
        for (int page = 0; page < MAX_PAGES; page++) {
            if (stopRequested()) {
                throw new SyncStoppedException();
            }
            Map<String, String> pageParams = new HashMap<>(params);
            if (offset > 0) {
                pageParams.put("Offset", String.valueOf(offset));
            }
            List<T> rows = fetch.apply(pageParams);
            if (rows == null || rows.isEmpty() || rows.equals(previous)) {
                break;
            }
            CoreProgress progress = PROGRESS.get();
            if (progress != null) {
                progress.read = offset + rows.size();
            }
            onPage.accept(rows);
            if (rows.size() < OD_PAGE_SIZE) {
                break;
            }
            offset += rows.size();
            previous = rows;
        }
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

        /** Records read from Open Dental and handled (written, unchanged or duplicate). */
        int fetched() {
            return inserted + updated + unchanged + duplicates;
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