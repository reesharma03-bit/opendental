package com.clinic.opendental.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.opendental.dto.appointment.AppointmentResponse;
import com.clinic.opendental.dto.appointmentdeleted.AppointmentDeletedResponse;
import com.clinic.opendental.dto.operatory.OperatoryResponse;
import com.clinic.opendental.dto.patfield.PatFieldResponse;
import com.clinic.opendental.dto.patfielddeleted.PatFieldDeletedResponse;
import com.clinic.opendental.dto.patient.PatientResponse;
import com.clinic.opendental.dto.provider.ProviderResponse;
import com.clinic.opendental.dto.schedule.ScheduleResponse;
import com.clinic.opendental.dto.scheduledeleted.ScheduleDeletedResponse;
import com.clinic.opendental.dto.toothinitial.ToothInitialResponse;
import com.clinic.opendental.dto.toothinitialdeleted.ToothInitialDeletedResponse;
import com.clinic.opendental.dto.labcase.LabCaseResponse;
import com.clinic.opendental.dto.labcasedeleted.LabCaseDeletedResponse;
import com.clinic.opendental.dto.medicationpat.MedicationPatResponse;
import com.clinic.opendental.dto.medicationpatdeleted.MedicationPatDeletedResponse;
import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.service.SelectedPatients;
import com.clinic.opendental.service.WebhookClinics;
import com.clinic.opendental.service.WebhookService;
import com.clinic.opendental.service.Impl.ResourceMirrorService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/webhooks/opendental")
@RequiredArgsConstructor
@Slf4j
public class OpenDentalWebhookController {

    /**
     * Open Dental sends webhook payloads using PascalCase keys (PatNum, LName, ...),
     * but the application's primary Jackson mapper is configured with SNAKE_CASE
     * for its REST API. We therefore parse webhook bodies with a dedicated mapper
     * that uses the exact (PascalCase) field names, so values map correctly here
     * without affecting the snake_case REST API.
     */
    private static final ObjectMapper WEBHOOK_MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            // Open Dental's PascalCase keys (PatNum, LName, ...) must map to the
            // DTO fields exactly. Lombok generates public getters (getPatNum...),
            // and getter-based naming decapitalizes to "patNum", which does not
            // match the incoming "PatNum". Using FIELD-based detection makes the
            // property name equal the literal PascalCase field name.
            .visibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
            .visibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE)
            .visibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE)
            // Setters must also be ignored: Lombok generates setConfirmed(Long)
            // etc., and Jackson decapitalizes those to lowercase property names
            // ("confirmed"), which would make a string like "Confirmed" collide
            // with the numeric Long field. Relying purely on fields keeps the
            // property name equal to the literal PascalCase field name.
            .visibility(PropertyAccessor.SETTER, JsonAutoDetect.Visibility.NONE)
            .build();

    private final WebhookService webhookService;
    private final WebhookClinics webhookClinics;
    private final ResourceMirrorService resourceMirror;
    private final SelectedPatients selectedPatients;

    private <T> List<T> parseArray(String rawBody, Class<T> type) throws JsonProcessingException {
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }
        return WEBHOOK_MAPPER.readValue(
                rawBody,
                WEBHOOK_MAPPER.getTypeFactory().constructCollectionType(List.class, type));
    }

    /**
     * Patient webhook endpoint.
     *
     * Open Dental sends subscription events here. The webhook payload is a JSON
     * array of patient records, which is parsed and saved directly to Supabase
     * — no API fetch is needed.
     *
     * Example payload:
     * [{"PatNum":26,"LName":"Test12","FName":"Tesat12",...}]
     *
     * Subscription configuration:
     * {
     *   "EndPointUrl": "https://hamzha-open.onrender.com/api/webhooks/opendental/patient",
     *   "Workstation": "Gaming",
     *   "WatchTable": "Patient",
     *   "PollingSeconds": 5
     * }
     */
    @PostMapping("/patient")
    public ResponseEntity<String> patientWebhook(
            @RequestBody(required = false) String rawBody) throws JsonProcessingException {

        List<PatientResponse> patients = parseArray(rawBody, PatientResponse.class);

        log.info("Webhook received: {} patient(s)", patients != null ? patients.size() : 0);

        webhookService.processPatientWebhook(patients);

        return ResponseEntity.ok("OK");
    }

    /**
     * Appointment webhook endpoint.
     *
     * Open Dental sends subscription events here. The webhook payload is a JSON
     * array of appointment records, which is parsed and saved directly to Supabase
     * — no API fetch is needed.
     *
     * Subscription configuration:
     * {
     *   "EndPointUrl": "https://hamzha-open.onrender.com/api/webhooks/opendental/appointment",
     *   "Workstation": "Gaming",
     *   "WatchTable": "Appointment",
     *   "PollingSeconds": 5
     * }
     */
    @PostMapping("/appointment")
    public ResponseEntity<String> appointmentWebhook(
            @RequestBody(required = false) String rawBody) {

        // Log the moment the endpoint is hit, BEFORE parsing, so we can tell
        // whether Open Dental is actually reaching this URL.
        log.info(">>> Appointment webhook endpoint hit. Raw body length={}, body={}",
                rawBody == null ? 0 : rawBody.length(), rawBody);

        List<AppointmentResponse> appointments;
        try {
            appointments = parseArray(rawBody, AppointmentResponse.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse appointment webhook payload: {}. Body={}",
                    e.getMessage(), rawBody);
            return ResponseEntity.badRequest().body("Invalid appointment webhook payload");
        }

        log.info("Webhook received: {} appointment(s)", appointments != null ? appointments.size() : 0);
        if (appointments != null) {
            for (AppointmentResponse a : appointments) {
                log.info("Webhook appointment -> AptNum={}, PatNum={}, AptStatus={}, AptDateTime={}",
                        a.getAptNum(), a.getPatNum(), a.getAptStatus(), a.getAptDateTime());
            }
        }

        webhookService.processAppointmentWebhook(appointments);

        return ResponseEntity.ok("OK");
    }

    /**
     * AppointmentDeleted webhook endpoint.
     *
     * Open Dental sends subscription events here. The webhook payload is a JSON
     * array of deleted-appointment records, which is parsed and saved directly
     * to Supabase — no API fetch is needed.
     *
     * Subscription configuration:
     * {
     *   "EndPointUrl": "https://hamzha-open.onrender.com/api/webhooks/opendental/appointmentdeleted",
     *   "Workstation": "Gaming",
     *   "WatchTable": "AppointmentDeleted",
     *   "PollingSeconds": 5
     * }
     */
    @PostMapping("/appointmentdeleted")
    public ResponseEntity<String> appointmentDeletedWebhook(
            @RequestBody(required = false) String rawBody) throws JsonProcessingException {

        List<AppointmentDeletedResponse> records =
                parseArray(rawBody, AppointmentDeletedResponse.class);

        log.info("Webhook received: {} appointmentDeleted record(s)",
                records != null ? records.size() : 0);

        webhookService.processAppointmentDeletedWebhook(records);

        return ResponseEntity.ok("OK");
    }

    /**
     * Operatory webhook endpoint.
     *
     * Subscription configuration:
     * {
     *   "EndPointUrl": "https://hamzha-open.onrender.com/api/webhooks/opendental/operatory",
     *   "Workstation": "Gaming",
     *   "WatchTable": "Operatory",
     *   "PollingSeconds": 5
     * }
     */
    @PostMapping("/operatory")
    public ResponseEntity<String> operatoryWebhook(
            @RequestBody(required = false) String rawBody) throws JsonProcessingException {

        List<OperatoryResponse> records = parseArray(rawBody, OperatoryResponse.class);

        log.info("Webhook received: {} operatory record(s)",
                records != null ? records.size() : 0);

        webhookService.processOperatoryWebhook(records);
        mirror("operatories", rawBody, false);

        return ResponseEntity.ok("OK");
    }

    /**
     * PatField webhook endpoint.
     *
     * Subscription configuration:
     * {
     *   "EndPointUrl": "https://hamzha-open.onrender.com/api/webhooks/opendental/patfield",
     *   "Workstation": "Gaming",
     *   "WatchTable": "PatField",
     *   "PollingSeconds": 5
     * }
     */
    @PostMapping("/patfield")
    public ResponseEntity<String> patFieldWebhook(
            @RequestBody(required = false) String rawBody) throws JsonProcessingException {

        List<PatFieldResponse> records = parseArray(rawBody, PatFieldResponse.class);

        log.info("Webhook received: {} patField record(s)",
                records != null ? records.size() : 0);

        webhookService.processPatFieldWebhook(records);
        mirror("patfields", rawBody, false);

        return ResponseEntity.ok("OK");
    }

    /**
     * PatFieldDeleted webhook endpoint.
     *
     * Open Dental sends subscription events here when a patient custom field is
     * deleted. The payload is a JSON array of deleted pat-field records, which is
     * parsed and applied directly — the matching pat_fields row is soft-deleted
     * (is_deleted = true, deleted_at, deleted_by) keyed by pat_field_num.
     *
     * Subscription configuration:
     * {
     *   "EndPointUrl": "https://hamzha-open.onrender.com/api/webhooks/opendental/patfielddeleted",
     *   "Workstation": "Gaming",
     *   "WatchTable": "PatFieldDeleted",
     *   "PollingSeconds": 5
     * }
     */
    @PostMapping("/patfielddeleted")
    public ResponseEntity<String> patFieldDeletedWebhook(
            @RequestBody(required = false) String rawBody) throws JsonProcessingException {

        List<PatFieldDeletedResponse> records = parseArray(rawBody, PatFieldDeletedResponse.class);

        log.info("Webhook received: {} patFieldDeleted record(s)",
                records != null ? records.size() : 0);

        webhookService.processPatFieldDeletedWebhook(records);
        mirror("patfields", rawBody, true);

        return ResponseEntity.ok("OK");
    }

    /**
     * Provider webhook endpoint.
     *
     * Subscription configuration:
     * {
     *   "EndPointUrl": "https://hamzha-open.onrender.com/api/webhooks/opendental/provider",
     *   "Workstation": "Gaming",
     *   "WatchTable": "Provider",
     *   "PollingSeconds": 5
     * }
     */
    @PostMapping("/provider")
    public ResponseEntity<String> providerWebhook(
            @RequestBody(required = false) String rawBody) throws JsonProcessingException {

        List<ProviderResponse> records = parseArray(rawBody, ProviderResponse.class);

        log.info("Webhook received: {} provider record(s)",
                records != null ? records.size() : 0);

        webhookService.processProviderWebhook(records);
        mirror("providers", rawBody, false);

        return ResponseEntity.ok("OK");
    }

    /**
     * Schedule webhook endpoint.
     *
     * Subscription configuration:
     * {
     *   "EndPointUrl": "https://hamzha-open.onrender.com/api/webhooks/opendental/schedule",
     *   "Workstation": "Gaming",
     *   "WatchTable": "Schedule",
     *   "PollingSeconds": 5
     * }
     */
    @PostMapping("/schedule")
    public ResponseEntity<String> scheduleWebhook(
            @RequestBody(required = false) String rawBody) throws JsonProcessingException {

        List<ScheduleResponse> records = parseArray(rawBody, ScheduleResponse.class);

        log.info("Webhook received: {} schedule record(s)",
                records != null ? records.size() : 0);

        webhookService.processScheduleWebhook(records);
        mirror("schedules", rawBody, false);

        return ResponseEntity.ok("OK");
    }

    /**
     * ScheduleDeleted webhook endpoint.
     *
     * Open Dental sends subscription events here when a schedule is deleted.
     * The payload is a JSON array of deleted schedule records, parsed and applied
     * directly — the matching schedules row is soft-deleted
     * (is_deleted = true, deleted_at, deleted_by) keyed by schedule_num.
     *
     * Subscription configuration:
     * {
     *   "EndPointUrl": "https://hamzha-open.onrender.com/api/webhooks/opendental/scheduledeleted",
     *   "Workstation": "Gaming",
     *   "WatchTable": "ScheduleDeleted",
     *   "PollingSeconds": 5
     * }
     */
    @PostMapping("/scheduledeleted")
    public ResponseEntity<String> scheduleDeletedWebhook(
            @RequestBody(required = false) String rawBody) throws JsonProcessingException {

        List<ScheduleDeletedResponse> records = parseArray(rawBody, ScheduleDeletedResponse.class);

        log.info("Webhook received: {} scheduleDeleted record(s)",
                records != null ? records.size() : 0);

        webhookService.processScheduleDeletedWebhook(records);
        mirror("schedules", rawBody, true);

        return ResponseEntity.ok("OK");
    }

    /**
     * ToothInitial webhook endpoint.
     *
     * Subscription configuration:
     * {
     *   "EndPointUrl": "https://hamzha-open.onrender.com/api/webhooks/opendental/toothinitial",
     *   "Workstation": "Gaming",
     *   "WatchTable": "ToothInitial",
     *   "PollingSeconds": 5
     * }
     */
    @PostMapping("/toothinitial")
    public ResponseEntity<String> toothInitialWebhook(
            @RequestBody(required = false) String rawBody) throws JsonProcessingException {

        List<ToothInitialResponse> records = parseArray(rawBody, ToothInitialResponse.class);

        log.info("Webhook received: {} toothInitial record(s)",
                records != null ? records.size() : 0);

        webhookService.processToothInitialWebhook(records);
        mirror("toothinitials", rawBody, false);

        return ResponseEntity.ok("OK");
    }

    /**
     * ToothInitialDeleted webhook endpoint.
     *
     * Open Dental sends subscription events here when a tooth-initial record is
     * deleted. The payload is a JSON array of deleted tooth-initial records,
     * parsed and applied directly — the matching tooth_initials row is
     * soft-deleted (is_deleted = true, deleted_at, deleted_by) keyed by
     * tooth_initial_num.
     *
     * Subscription configuration:
     * {
     *   "EndPointUrl": "https://hamzha-open.onrender.com/api/webhooks/opendental/toothinitialdeleted",
     *   "Workstation": "Gaming",
     *   "WatchTable": "ToothInitialDeleted",
     *   "PollingSeconds": 5
     * }
     */
    @PostMapping("/toothinitialdeleted")
    public ResponseEntity<String> toothInitialDeletedWebhook(
            @RequestBody(required = false) String rawBody) throws JsonProcessingException {

        List<ToothInitialDeletedResponse> records = parseArray(rawBody, ToothInitialDeletedResponse.class);

        log.info("Webhook received: {} toothInitialDeleted record(s)",
                records != null ? records.size() : 0);

        webhookService.processToothInitialDeletedWebhook(records);
        mirror("toothinitials", rawBody, true);

        return ResponseEntity.ok("OK");
    }

    // ========================================================================
    // LabCase / MedicationPat: own tables (lab_cases, medication_pats) and the
    // catalog copy in od_resource_records
    // ========================================================================

    /** LabCase: lab cases created or changed in Open Dental. Payload: array of LabCase rows. */
    @PostMapping("/labcase")
    public ResponseEntity<String> labCaseWebhook(@RequestBody(required = false) String rawBody) throws JsonProcessingException {
        List<LabCaseResponse> records = parseArray(rawBody, LabCaseResponse.class);
        log.info("Webhook received: {} LabCase record(s)", records != null ? records.size() : 0);
        webhookService.processLabCaseWebhook(records);
        mirror("labcases", rawBody, false);
        return ResponseEntity.ok("OK");
    }

    /** LabCaseDeleted: lab cases deleted in Open Dental. */
    @PostMapping("/labcasedeleted")
    public ResponseEntity<String> labCaseDeletedWebhook(@RequestBody(required = false) String rawBody) throws JsonProcessingException {
        List<LabCaseDeletedResponse> records = parseArray(rawBody, LabCaseDeletedResponse.class);
        log.info("Webhook received: {} LabCaseDeleted record(s)", records != null ? records.size() : 0);
        webhookService.processLabCaseDeletedWebhook(records);
        mirror("labcases", rawBody, true);
        return ResponseEntity.ok("OK");
    }

    /** MedicationPat: a patient's medications created or changed in Open Dental. */
    @PostMapping("/medicationpat")
    public ResponseEntity<String> medicationPatWebhook(@RequestBody(required = false) String rawBody) throws JsonProcessingException {
        List<MedicationPatResponse> records = parseArray(rawBody, MedicationPatResponse.class);
        log.info("Webhook received: {} MedicationPat record(s)", records != null ? records.size() : 0);
        webhookService.processMedicationPatWebhook(records);
        mirror("medicationpats", rawBody, false);
        return ResponseEntity.ok("OK");
    }

    /** MedicationPatDeleted: a patient's medications removed in Open Dental. */
    @PostMapping("/medicationpatdeleted")
    public ResponseEntity<String> medicationPatDeletedWebhook(@RequestBody(required = false) String rawBody) throws JsonProcessingException {
        List<MedicationPatDeletedResponse> records = parseArray(rawBody, MedicationPatDeletedResponse.class);
        log.info("Webhook received: {} MedicationPatDeleted record(s)", records != null ? records.size() : 0);
        webhookService.processMedicationPatDeletedWebhook(records);
        mirror("medicationpats", rawBody, true);
        return ResponseEntity.ok("OK");
    }

    // ========================================================================
    // UI event: PatientSelected
    // ========================================================================

    /**
     * PatientSelected (UI event): a patient was opened in Open Dental on a workstation. The
     * body is a single patient object, not an array; the workstation comes in the
     * "Workstation" header. Remembered in memory so the dashboard can offer that patient.
     */
    @PostMapping("/patientselected")
    public ResponseEntity<String> patientSelectedWebhook(
            @RequestBody(required = false) String rawBody,
            @RequestHeader(value = "Workstation", required = false) String workstation) throws JsonProcessingException {
        if (rawBody == null || rawBody.isBlank()) {
            return ResponseEntity.ok("OK");
        }
        JsonNode patient = WEBHOOK_MAPPER.readTree(rawBody);
        if (patient.isArray() && !patient.isEmpty()) {
            patient = patient.get(0);
        }
        long patNum = patient.path("PatNum").asLong();
        String name = (patient.path("LName").asText("") + ", " + patient.path("FName").asText("")).replaceAll("^, |, $", "");
        selectedPatients.record(webhookClinics.clinic().getId(), workstation, patNum, name);
        log.info("PatientSelected webhook: patient {} on workstation {}", patNum, workstation);
        return ResponseEntity.ok("OK");
    }

    /**
     * Applies the webhook's rows to the resource's copy in od_resource_records (what the
     * API Catalog screens show): saved, or removed for a ...Deleted event.
     */
    private void mirror(String resource, String rawBody, boolean deleted) throws JsonProcessingException {
        if (rawBody == null || rawBody.isBlank()) {
            return;
        }
        JsonNode body = WEBHOOK_MAPPER.readTree(rawBody);
        List<JsonNode> rows = new java.util.ArrayList<>();
        if (body.isArray()) body.forEach(rows::add);
        else if (body.isObject()) rows.add(body);
        Clinic clinic = webhookClinics.clinic();
        int count = deleted ? resourceMirror.removeWebhookRows(clinic, resource, rows)
                : resourceMirror.applyWebhookRows(clinic, resource, rows);
        log.info("Webhook {}: {} {} record(s) {} (clinic {})", resource, count, resource, deleted ? "removed" : "saved",
                clinic.getClinicCode());
    }

}
