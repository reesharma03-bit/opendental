package com.clinic.opendental.service.Impl;

import com.clinic.opendental.client.OpenDentalClient;
import com.clinic.opendental.dto.appointment.CreateAppointmentRequest;
import com.clinic.opendental.dto.appointment.UpdateAppointmentRequest;
import com.clinic.opendental.dto.patient.CreatePatientRequest;
import com.clinic.opendental.dto.patient.UpdatePatientRequest;
import com.clinic.opendental.dto.procedurelog.CreateProcedureLogRequest;
import com.clinic.opendental.dto.procedurelog.UpdateProcedureLogRequest;
import com.clinic.opendental.service.AppointmentService;
import com.clinic.opendental.service.PatientService;
import com.clinic.opendental.service.ProcedureLogService;
import com.clinic.opendental.exception.ApiException;
import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.repository.ClinicRepository;
import com.clinic.opendental.service.Impl.OdResourceCatalog.Resource;
import com.clinic.opendental.service.Impl.OdResourceCatalog.Writable;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Open Dental resources as the dashboard sees them: read from our copy in
 * od_resource_records, and changed there first, then in Open Dental (via
 * {@link OdSyncService}).
 *
 * <p>Records come back exactly as Open Dental shapes them (PascalCase fields), so
 * screens built against Open Dental's API work unchanged.</p>
 */
@Service
@Slf4j
public class DatabaseResourceService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ResourceRecordStore records;
    private final OdSyncService odSync;
    private final ClinicRepository clinicRepository;
    private final OpenDentalClient client;
    private final PatientService patientService;
    private final AppointmentService appointmentService;
    private final ProcedureLogService procedureLogService;

    public DatabaseResourceService(ResourceRecordStore records, OdSyncService odSync,
                                   ClinicRepository clinicRepository, OpenDentalClient client,
                                   PatientService patientService, AppointmentService appointmentService,
                                   ProcedureLogService procedureLogService) {
        this.records = records;
        this.odSync = odSync;
        this.clinicRepository = clinicRepository;
        this.client = client;
        this.patientService = patientService;
        this.appointmentService = appointmentService;
        this.procedureLogService = procedureLogService;
    }

    /** Every resource the dashboard can show, with what it may change (drives Add / Edit / Delete). */
    public List<Map<String, Object>> resources() {
        List<Map<String, Object>> out = new ArrayList<>();
        OdResourceCatalog.TYPED.forEach((resource, writable) ->
                out.add(describe(resource, OdResourceCatalog.TYPED_KEYS.get(resource), writable)));
        java.util.stream.Stream.concat(OdResourceCatalog.LISTS.stream(), OdResourceCatalog.PER_PARENT.stream())
                .forEach(r -> out.add(describe(r.resource(), r.keyField(), OdResourceCatalog.WRITABLE.get(r.resource()))));
        out.sort(java.util.Comparator.comparing(m -> (String) m.get("resource")));
        return out;
    }

    private static Map<String, Object> describe(String resource, String keyField, Writable writable) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("resource", resource);
        map.put("keyField", keyField);
        map.put("create", writable != null && writable.create());
        map.put("update", writable != null && writable.update());
        map.put("delete", writable != null && writable.delete());
        return map;
    }

    public List<JsonNode> list(String resource, Long patNum, int limit, int offset) {
        int size = Math.max(1, Math.min(limit, 1000));
        int from = Math.max(0, offset);
        if (OdResourceCatalog.TYPED.containsKey(resource)) {
            List<JsonNode> all = typedList(resource, patNum);
            return all.subList(Math.min(from, all.size()), Math.min(from + size, all.size()));
        }
        known(resource);
        return records.list(clinic().getId(), resource, patNum, size, from);
    }

    public JsonNode get(String resource, String key) {
        if (OdResourceCatalog.TYPED.containsKey(resource)) {
            String keyField = OdResourceCatalog.TYPED_KEYS.get(resource);
            return typedList(resource, "patients".equals(resource) ? numericKey(resource, key) : null).stream()
                    .filter(row -> key.equals(row.path(keyField).asText()))
                    .findFirst()
                    .orElseThrow(() -> notFound(resource, key));
        }
        known(resource);
        Clinic clinic = clinic();
        ensureStored(clinic, resource, key);
        return records.find(clinic.getId(), resource, key).orElseThrow(() -> notFound(resource, key));
    }

    /** Saved here first under a temporary negative key; Open Dental then assigns the real one. */
    public JsonNode create(String resource, Map<String, Object> body) {
        if (OdResourceCatalog.TYPED.containsKey(resource)) {
            typedWritable(resource, "create", Writable::create);
            return tree(switch (resource) {
                case "patients" -> patientService.createPatient(OdSyncService.convert(body, CreatePatientRequest.class));
                case "appointments" -> appointmentService.createAppointment(OdSyncService.convert(body, CreateAppointmentRequest.class));
                default -> procedureLogService.createProcedureLog(OdSyncService.convert(body, CreateProcedureLogRequest.class));
            });
        }
        Resource def = known(resource);
        writable(resource, "create", Writable::create);
        UUID clinicId = clinic().getId();
        long taskId = odSync.recordCreate(clinicId, OdResourceCatalog.entityType(resource), OdSyncService.CREATE, body,
                tempKey -> {
                    Map<String, Object> data = new LinkedHashMap<>(OdResourceCatalog.WRITABLE.get(resource).defaults());
                    data.putAll(body);
                    data.put(def.keyField(), tempKey);
                    records.save(clinicId, resource, String.valueOf(tempKey), JSON.valueToTree(data));
                });
        String key = String.valueOf(odSync.pushNow(taskId));
        return records.find(clinicId, resource, key).orElseThrow(() -> notFound(resource, key));
    }

    public JsonNode update(String resource, String key, Map<String, Object> body) {
        if (OdResourceCatalog.TYPED.containsKey(resource)) {
            typedWritable(resource, "update", Writable::update);
            long id = numericKey(resource, key);
            return tree(switch (resource) {
                case "patients" -> patientService.updatePatient(id, OdSyncService.convert(body, UpdatePatientRequest.class));
                case "appointments" -> appointmentService.updateAppointment(id, OdSyncService.convert(body, UpdateAppointmentRequest.class));
                default -> procedureLogService.updateProcedureLog(id, OdSyncService.convert(body, UpdateProcedureLogRequest.class));
            });
        }
        Resource def = known(resource);
        writable(resource, "update", Writable::update);
        Clinic clinic = clinic();
        ensureStored(clinic, resource, key);
        Map<String, Object> changes = new LinkedHashMap<>(body);
        changes.remove(def.keyField());
        long taskId = odSync.recordChange(clinic.getId(), OdResourceCatalog.entityType(resource), OdSyncService.UPDATE,
                numericKey(resource, key), changes,
                () -> records.merge(clinic.getId(), resource, key, changes));
        odSync.pushNow(taskId);
        return records.find(clinic.getId(), resource, key).orElseThrow(() -> notFound(resource, key));
    }

    public void delete(String resource, String key) {
        if (OdResourceCatalog.TYPED.containsKey(resource)) {
            typedWritable(resource, "delete", Writable::delete);
            procedureLogService.deleteProcedureLog(numericKey(resource, key));
            return;
        }
        known(resource);
        writable(resource, "delete", Writable::delete);
        Clinic clinic = clinic();
        ensureStored(clinic, resource, key);
        Long taskId = odSync.recordDelete(clinic.getId(), OdResourceCatalog.entityType(resource), numericKey(resource, key),
                () -> records.delete(clinic.getId(), resource, key));
        if (taskId != null) {
            odSync.pushNow(taskId);
        }
    }

    /** A record Open Dental has but the last sync did not bring in yet is copied in first. */
    private void ensureStored(Clinic clinic, String resource, String key) {
        if (records.find(clinic.getId(), resource, key).isPresent()) {
            return;
        }
        JsonNode fromOpenDental;
        try {
            fromOpenDental = numericKey(resource, key) > 0
                    ? client.getRaw("/" + resource + "/" + key, Map.of(), clinic.getBaseUrl(), clinic.getApiKey())
                    : null;
        } catch (Exception e) {
            log.warn("Could not load {} {} from Open Dental: {}", resource, key, e.getMessage());
            fromOpenDental = null;
        }
        if (fromOpenDental == null || !fromOpenDental.isObject()) {
            throw notFound(resource, key);
        }
        records.save(clinic.getId(), resource, key, (ObjectNode) fromOpenDental);
    }

    /** Typed records shaped like Open Dental's (PatNum, LName, ...), read from their own tables. */
    private List<JsonNode> typedList(String resource, Long patNum) {
        List<?> rows = switch (resource) {
            case "patients" -> patientService.getPatientsFromDatabase(patNum);
            case "appointments" -> appointmentService.getAppointmentsFromDatabase(
                    patNum == null ? Map.of() : Map.of("PatNum", String.valueOf(patNum)));
            default -> procedureLogService.getProcedureLogsFromDatabase(patNum);
        };
        return rows.stream().map(DatabaseResourceService::tree).toList();
    }

    private static JsonNode tree(Object dto) {
        return OdSyncService.convert(dto, JsonNode.class);
    }

    private static void typedWritable(String resource, String action, java.util.function.Predicate<Writable> allowed) {
        if (!allowed.test(OdResourceCatalog.TYPED.get(resource))) {
            throw new ApiException(HttpStatus.METHOD_NOT_ALLOWED,
                    "Open Dental does not allow " + action + " for " + resource);
        }
    }

    private static Resource known(String resource) {
        Resource def = OdResourceCatalog.find(resource);
        if (def == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Unknown resource: " + resource);
        }
        return def;
    }

    private static void writable(String resource, String action, java.util.function.Predicate<Writable> allowed) {
        Writable writable = OdResourceCatalog.WRITABLE.get(resource);
        if (writable == null || !allowed.test(writable)) {
            throw new ApiException(HttpStatus.METHOD_NOT_ALLOWED,
                    "Open Dental does not allow " + action + " for " + resource + " through its API");
        }
    }

    private static long numericKey(String resource, String key) {
        try {
            return Long.parseLong(key);
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, resource + " key must be a number: " + key);
        }
    }

    private Clinic clinic() {
        return clinicRepository.findByIsActiveTrue().stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "No active clinic configured. Please register a clinic in the clinics table."));
    }

    private static ApiException notFound(String resource, String key) {
        return new ApiException(HttpStatus.NOT_FOUND, "No " + resource + " " + key + " in our database or Open Dental");
    }
}
