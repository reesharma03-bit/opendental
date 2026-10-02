package com.clinic.opendental.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.opendental.dto.appointment.AppointmentResponse;
import com.clinic.opendental.dto.appointmentdeleted.AppointmentDeletedResponse;
import com.clinic.opendental.dto.document.DocumentResponse;
import com.clinic.opendental.dto.operatory.OperatoryResponse;
import com.clinic.opendental.dto.patfield.PatFieldResponse;
import com.clinic.opendental.dto.patfielddeleted.PatFieldDeletedResponse;
import com.clinic.opendental.dto.patient.PatientResponse;
import com.clinic.opendental.dto.procedurelog.ProcedureLogResponse;
import com.clinic.opendental.dto.provider.ProviderResponse;
import com.clinic.opendental.dto.query.QueryRequest;
import com.clinic.opendental.dto.schedule.ScheduleResponse;
import com.clinic.opendental.dto.scheduledeleted.ScheduleDeletedResponse;
import com.clinic.opendental.dto.toothinitial.ToothInitialResponse;
import com.clinic.opendental.dto.toothinitialdeleted.ToothInitialDeletedResponse;
import com.clinic.opendental.service.WebhookService;
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

        return ResponseEntity.ok("OK");
    }

    /**
     * Document webhook endpoint.
     *
     * Open Dental sends subscription events here when a document is created,
     * updated, or deleted. The payload is a JSON array of document records,
     * parsed and saved directly to Supabase — no API fetch needed.
     */
    @PostMapping("/document")
    public ResponseEntity<String> documentWebhook(
            @RequestBody(required = false) String rawBody) throws JsonProcessingException {

        List<DocumentResponse> records = parseArray(rawBody, DocumentResponse.class);

        log.info("Webhook received: {} document record(s)",
                records != null ? records.size() : 0);

        webhookService.processDocumentWebhook(records);

        return ResponseEntity.ok("OK");
    }

    /**
     * ProcedureLog webhook endpoint.
     *
     * Open Dental sends subscription events here when a procedure log is
     * created, updated, or deleted. The payload is a JSON array of procedure log
     * records, parsed and saved directly to Supabase — no API fetch needed.
     */
    @PostMapping("/procedurelog")
    public ResponseEntity<String> procedureLogWebhook(
            @RequestBody(required = false) String rawBody) throws JsonProcessingException {

        List<ProcedureLogResponse> records = parseArray(rawBody, ProcedureLogResponse.class);

        log.info("Webhook received: {} procedureLog record(s)",
                records != null ? records.size() : 0);

        webhookService.processProcedureLogWebhook(records);

        return ResponseEntity.ok("OK");
    }

    /**
     * Query webhook endpoint.
     *
     * Open Dental sends a single query request (not an array) which is forwarded
     * to the query service to execute and save the results to SFTP.
     */
    @PostMapping("/query")
    public ResponseEntity<String> queryWebhook(
            @RequestBody(required = false) String rawBody) throws JsonProcessingException {

        QueryRequest request = null;
        if (rawBody != null && !rawBody.isBlank()) {
            request = WEBHOOK_MAPPER.readValue(rawBody, QueryRequest.class);
        }

        log.info("Query webhook received: command={}",
                request != null ? request.getSqlCommand() : "empty");

        webhookService.processQueryWebhook(request);

        return ResponseEntity.ok("OK");
    }
}
