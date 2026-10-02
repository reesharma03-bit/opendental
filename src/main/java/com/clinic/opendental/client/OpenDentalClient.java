package com.clinic.opendental.client;

import com.clinic.opendental.dto.appointment.*;
import com.clinic.opendental.dto.clinic.ClinicResponse;
import com.clinic.opendental.dto.document.*;
import com.clinic.opendental.dto.patient.CreatePatientRequest;
import com.clinic.opendental.dto.patient.PatientResponse;
import com.clinic.opendental.dto.patient.PatientSimpleResponse;
import com.clinic.opendental.dto.patient.UpdatePatientRequest;
import com.clinic.opendental.dto.operatory.OperatoryResponse;
import com.clinic.opendental.dto.patfield.PatFieldResponse;
import com.clinic.opendental.dto.procedurelog.*;
import com.clinic.opendental.dto.provider.ProviderResponse;
import com.clinic.opendental.dto.schedule.ScheduleResponse;
import com.clinic.opendental.dto.toothinitial.ToothInitialResponse;
import com.clinic.opendental.dto.query.QueryRequest;
import com.clinic.opendental.dto.query.ShortQueryRequest;
import com.clinic.opendental.dto.subscription.SubscriptionRequest;
import com.clinic.opendental.dto.subscription.SubscriptionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OpenDentalClient {

    @Value("${opendental.base-url}")
    private String baseUrl;

    private final RestTemplate restTemplate;

    // Static, named generic type references (rather than inline anonymous ones).
    // Anonymous ParameterizedTypeReference inside overloaded methods generates
    // fragile InnerClass$N symbols that can cause NoClassDefFoundError; a single
    // static constant per list type avoids that entirely while still capturing
    // the concrete element type for deserialization.
    private static final ParameterizedTypeReference<List<PatientResponse>> PATIENT_LIST =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<AppointmentResponse>> APPOINTMENT_LIST =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<ProcedureLogResponse>> PROCEDURE_LOG_LIST =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<DocumentResponse>> DOCUMENT_LIST =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<ClinicResponse>> CLINIC_LIST =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<PatFieldResponse>> PAT_FIELD_LIST =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<ProviderResponse>> PROVIDER_LIST =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<OperatoryResponse>> OPERATORY_LIST =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<ScheduleResponse>> SCHEDULE_LIST =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<ToothInitialResponse>> TOOTH_INITIAL_LIST =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<Map<String, Object>>> ALLERGY_LIST =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<Map<String, Object>> ALLERGY_MAP =
            new ParameterizedTypeReference<>() {};


    /**
     * Resolve the effective base URL.
     * If a clinic-specific base URL is provided, use it; otherwise fall back to the configured default.
     */
    private String resolveBaseUrl(String clinicBaseUrl) {
        return (clinicBaseUrl != null && !clinicBaseUrl.isBlank())
                ? clinicBaseUrl
                : baseUrl;
    }


    /**
     * Build HTTP headers with the Open Dental API key in the Authorization header.
     * The API key identifies the customer for subscription endpoints.
     */
    private HttpHeaders buildAuthHeaders(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        if (apiKey != null && !apiKey.isBlank()) {
            headers.set("Authorization", apiKey);
        }
        return headers;
    }

    // ========================================================================
    // Patient Endpoints
    // ========================================================================

    public PatientResponse getPatient(Long patNum) {
        return getPatient(patNum, null);
    }

    public PatientResponse getPatient(Long patNum, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        return restTemplate.getForObject(
                url + "/patients/" + patNum,
                PatientResponse.class);
    }

    public List<PatientSimpleResponse> getSimplePatients(Map<String, String> params) {
        return getSimplePatients(params, null);
    }

    public List<PatientSimpleResponse> getSimplePatients(Map<String, String> params, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(url + "/patients/Simple");
        params.forEach(builder::queryParam);

        ResponseEntity<List<PatientSimpleResponse>> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<PatientSimpleResponse>>() {});
        return response.getBody();
    }

    public List<PatientResponse> getPatients(Map<String, String> params) {
        return getPatients(params, null);
    }

    public List<PatientResponse> getPatients(Map<String, String> params, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(url + "/patients");
        params.forEach(builder::queryParam);

        ResponseEntity<List<PatientResponse>> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                null,
                PATIENT_LIST);
        return response.getBody();
    }

    public List<PatientResponse> getPatients(Map<String, String> params, String clinicBaseUrl, String apiKey) {
        String url = resolveBaseUrl(clinicBaseUrl);
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(url + "/patients");
        params.forEach(builder::queryParam);

        HttpEntity<Void> entity = new HttpEntity<>(buildAuthHeaders(apiKey));
        ResponseEntity<List<PatientResponse>> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                entity,
                PATIENT_LIST);
        return response.getBody();
    }

    public PatientResponse createPatient(CreatePatientRequest request) {
        return createPatient(request, null);
    }

    public PatientResponse createPatient(CreatePatientRequest request, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        return restTemplate.postForObject(
                url + "/patients",
                request,
                PatientResponse.class);
    }

    public PatientResponse updatePatient(Long patNum, UpdatePatientRequest request) {
        return updatePatient(patNum, request, null);
    }

    public PatientResponse updatePatient(Long patNum, UpdatePatientRequest request, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        HttpEntity<UpdatePatientRequest> entity = new HttpEntity<>(request);
        ResponseEntity<PatientResponse> response = restTemplate.exchange(
                url + "/patients/" + patNum,
                HttpMethod.PUT,
                entity,
                PatientResponse.class);
        return response.getBody();
    }

    // ========================================================================
    // Clinic Endpoints
    // ========================================================================

    // --- GET /clinics ---
    public List<ClinicResponse> getClinics() {
        return getClinics(null);
    }

    public List<ClinicResponse> getClinics(String clinicBaseUrl) {
        return getClinics(clinicBaseUrl, null);
    }

    public List<ClinicResponse> getClinics(String clinicBaseUrl, String apiKey) {
        String url = resolveBaseUrl(clinicBaseUrl);
        HttpEntity<Void> entity = new HttpEntity<>(buildAuthHeaders(apiKey));
        ResponseEntity<List<ClinicResponse>> response = restTemplate.exchange(
                url + "/clinics",
                HttpMethod.GET,
                entity,
                CLINIC_LIST);
        return response.getBody();
    }


    // ========================================================================
    // Appointment Endpoints
    // ========================================================================

    // --- GET /appointments/{aptNum} ---
    public AppointmentResponse getAppointment(Long aptNum) {
        return getAppointment(aptNum, null);
    }

    public AppointmentResponse getAppointment(Long aptNum, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        return restTemplate.getForObject(
                url + "/appointments/" + aptNum,
                AppointmentResponse.class);
    }

    // --- GET /appointments ---
    public List<AppointmentResponse> getAppointments(Map<String, String> params) {
        return getAppointments(params, null);
    }

    public List<AppointmentResponse> getAppointments(Map<String, String> params, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(url + "/appointments");
        params.forEach(builder::queryParam);

        ResponseEntity<List<AppointmentResponse>> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                null,
                APPOINTMENT_LIST);
        return response.getBody();
    }

    public List<AppointmentResponse> getAppointments(Map<String, String> params, String clinicBaseUrl, String apiKey) {
        String url = resolveBaseUrl(clinicBaseUrl);
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(url + "/appointments");
        params.forEach(builder::queryParam);

        HttpEntity<Void> entity = new HttpEntity<>(buildAuthHeaders(apiKey));
        ResponseEntity<List<AppointmentResponse>> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                entity,
                APPOINTMENT_LIST);
        return response.getBody();
    }

    // --- GET /appointments/ASAP ---
    public List<AppointmentResponse> getASAPAppointments(Map<String, String> params) {
        return getASAPAppointments(params, null);
    }

    public List<AppointmentResponse> getASAPAppointments(Map<String, String> params, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(url + "/appointments/ASAP");
        params.forEach(builder::queryParam);

        ResponseEntity<List<AppointmentResponse>> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<AppointmentResponse>>() {});
        return response.getBody();
    }

    // --- GET /appointments/Slots ---
    public List<SlotResponse> getSlots(Map<String, String> params) {
        return getSlots(params, null);
    }

    public List<SlotResponse> getSlots(Map<String, String> params, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(url + "/appointments/Slots");
        params.forEach(builder::queryParam);

        ResponseEntity<List<SlotResponse>> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<SlotResponse>>() {});
        return response.getBody();
    }

    // --- GET /appointments/SlotsWebSched ---
    public List<SlotResponse> getSlotsWebSched(Map<String, String> params) {
        return getSlotsWebSched(params, null);
    }

    public List<SlotResponse> getSlotsWebSched(Map<String, String> params, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(url + "/appointments/SlotsWebSched");
        params.forEach(builder::queryParam);

        ResponseEntity<List<SlotResponse>> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<SlotResponse>>() {});
        return response.getBody();
    }

    // --- GET /appointments/WebSched ---
    public List<AppointmentResponse> getWebSchedAppointments(Map<String, String> params) {
        return getWebSchedAppointments(params, null);
    }

    public List<AppointmentResponse> getWebSchedAppointments(Map<String, String> params, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(url + "/appointments/WebSched");
        params.forEach(builder::queryParam);

        ResponseEntity<List<AppointmentResponse>> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<AppointmentResponse>>() {});
        return response.getBody();
    }

    // --- POST /appointments ---
    public AppointmentResponse createAppointment(CreateAppointmentRequest request) {
        return createAppointment(request, null);
    }

    public AppointmentResponse createAppointment(CreateAppointmentRequest request, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        return restTemplate.postForObject(
                url + "/appointments",
                request,
                AppointmentResponse.class);
    }

    // --- POST /appointments/Planned ---
    public AppointmentResponse createPlannedAppointment(PlannedAppointmentRequest request) {
        return createPlannedAppointment(request, null);
    }

    public AppointmentResponse createPlannedAppointment(PlannedAppointmentRequest request, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        return restTemplate.postForObject(
                url + "/appointments/Planned",
                request,
                AppointmentResponse.class);
    }

    // --- POST /appointments/SchedulePlanned ---
    public AppointmentResponse schedulePlannedAppointment(SchedulePlannedRequest request) {
        return schedulePlannedAppointment(request, null);
    }

    public AppointmentResponse schedulePlannedAppointment(SchedulePlannedRequest request, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        return restTemplate.postForObject(
                url + "/appointments/SchedulePlanned",
                request,
                AppointmentResponse.class);
    }

    // --- POST /appointments/WebSched ---
    public AppointmentResponse createWebSchedAppointment(WebSchedRequest request) {
        return createWebSchedAppointment(request, null);
    }

    public AppointmentResponse createWebSchedAppointment(WebSchedRequest request, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        return restTemplate.postForObject(
                url + "/appointments/WebSched",
                request,
                AppointmentResponse.class);
    }

    // --- PUT /appointments/{aptNum} ---
    public AppointmentResponse updateAppointment(Long aptNum, UpdateAppointmentRequest request) {
        return updateAppointment(aptNum, request, null);
    }

    public AppointmentResponse updateAppointment(Long aptNum, UpdateAppointmentRequest request, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        HttpEntity<UpdateAppointmentRequest> entity = new HttpEntity<>(request);
        ResponseEntity<AppointmentResponse> response = restTemplate.exchange(
                url + "/appointments/" + aptNum,
                HttpMethod.PUT,
                entity,
                AppointmentResponse.class);
        return response.getBody();
    }

    // --- PUT /appointments/{aptNum}/Break ---
    public void breakAppointment(Long aptNum, BreakAppointmentRequest request) {
        breakAppointment(aptNum, request, null);
    }

    public void breakAppointment(Long aptNum, BreakAppointmentRequest request, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        HttpEntity<BreakAppointmentRequest> entity = new HttpEntity<>(request);
        restTemplate.exchange(
                url + "/appointments/" + aptNum + "/Break",
                HttpMethod.PUT,
                entity,
                Void.class);
    }

    // --- PUT /appointments/{aptNum}/Note ---
    public void appendNote(Long aptNum, NoteRequest request) {
        appendNote(aptNum, request, null);
    }

    public void appendNote(Long aptNum, NoteRequest request, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        HttpEntity<NoteRequest> entity = new HttpEntity<>(request);
        restTemplate.exchange(
                url + "/appointments/" + aptNum + "/Note",
                HttpMethod.PUT,
                entity,
                Void.class);
    }

    // --- PUT /appointments/{aptNum}/Confirm ---
    public void confirmAppointment(Long aptNum, ConfirmAppointmentRequest request) {
        confirmAppointment(aptNum, request, null);
    }

    public void confirmAppointment(Long aptNum, ConfirmAppointmentRequest request, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        HttpEntity<ConfirmAppointmentRequest> entity = new HttpEntity<>(request);
        restTemplate.exchange(
                url + "/appointments/" + aptNum + "/Confirm",
                HttpMethod.PUT,
                entity,
                Void.class);
    }

    // ========================================================================
    // ProcedureLog Endpoints
    // ========================================================================

    // --- GET /procedurelogs/{procNum} ---
    public ProcedureLogResponse getProcedureLog(Long procNum) {
        return getProcedureLog(procNum, null);
    }

    public ProcedureLogResponse getProcedureLog(Long procNum, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        return restTemplate.getForObject(
                url + "/procedurelogs/" + procNum,
                ProcedureLogResponse.class);
    }

    // --- GET /procedurelogs ---
    public List<ProcedureLogResponse> getProcedureLogs(Map<String, String> params) {
        return getProcedureLogs(params, null);
    }

    public List<ProcedureLogResponse> getProcedureLogs(Map<String, String> params, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(url + "/procedurelogs");
        params.forEach(builder::queryParam);

        ResponseEntity<List<ProcedureLogResponse>> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                null,
                PROCEDURE_LOG_LIST);
        return response.getBody();
    }

    public List<ProcedureLogResponse> getProcedureLogs(Map<String, String> params, String clinicBaseUrl, String apiKey) {
        String url = resolveBaseUrl(clinicBaseUrl);
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(url + "/procedurelogs");
        params.forEach(builder::queryParam);

        HttpEntity<Void> entity = new HttpEntity<>(buildAuthHeaders(apiKey));
        ResponseEntity<List<ProcedureLogResponse>> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                entity,
                PROCEDURE_LOG_LIST);
        return response.getBody();
    }

    // --- GET /procedurelogs/InsuranceHistory ---
    public List<InsuranceHistoryResponse> getInsuranceHistory(Long patNum, Long insSubNum) {
        return getInsuranceHistory(patNum, insSubNum, null);
    }

    public List<InsuranceHistoryResponse> getInsuranceHistory(Long patNum, Long insSubNum, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(url + "/procedurelogs/InsuranceHistory")
                .queryParam("PatNum", patNum)
                .queryParam("InsSubNum", insSubNum);

        ResponseEntity<List<InsuranceHistoryResponse>> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<InsuranceHistoryResponse>>() {});
        return response.getBody();
    }

    // --- GET /procedurelogs/GroupNotes ---
    public List<GroupNoteResponse> getGroupNotes(Long patNum) {
        return getGroupNotes(patNum, null);
    }

    public List<GroupNoteResponse> getGroupNotes(Long patNum, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(url + "/procedurelogs/GroupNotes")
                .queryParam("PatNum", patNum);

        ResponseEntity<List<GroupNoteResponse>> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<GroupNoteResponse>>() {});
        return response.getBody();
    }

    // --- POST /procedurelogs ---
    public ProcedureLogResponse createProcedureLog(CreateProcedureLogRequest request) {
        return createProcedureLog(request, null);
    }

    public ProcedureLogResponse createProcedureLog(CreateProcedureLogRequest request, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        return restTemplate.postForObject(
                url + "/procedurelogs",
                request,
                ProcedureLogResponse.class);
    }

    // --- POST /procedurelogs/GroupNote ---
    public GroupNoteResponse createGroupNote(GroupNoteRequest request) {
        return createGroupNote(request, null);
    }

    public GroupNoteResponse createGroupNote(GroupNoteRequest request, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        return restTemplate.postForObject(
                url + "/procedurelogs/GroupNote",
                request,
                GroupNoteResponse.class);
    }

    // --- POST /procedurelogs/InsuranceHistory ---
    public ProcedureLogResponse createInsuranceHistory(InsuranceHistoryRequest request) {
        return createInsuranceHistory(request, null);
    }

    public ProcedureLogResponse createInsuranceHistory(InsuranceHistoryRequest request, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        return restTemplate.postForObject(
                url + "/procedurelogs/InsuranceHistory",
                request,
                ProcedureLogResponse.class);
    }

    // --- PUT /procedurelogs/{procNum} ---
    public ProcedureLogResponse updateProcedureLog(Long procNum, UpdateProcedureLogRequest request) {
        return updateProcedureLog(procNum, request, null);
    }

    public ProcedureLogResponse updateProcedureLog(Long procNum, UpdateProcedureLogRequest request, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        HttpEntity<UpdateProcedureLogRequest> entity = new HttpEntity<>(request);
        ResponseEntity<ProcedureLogResponse> response = restTemplate.exchange(
                url + "/procedurelogs/" + procNum,
                HttpMethod.PUT,
                entity,
                ProcedureLogResponse.class);
        return response.getBody();
    }

    // --- PUT /procedurelogs/{procNum}/GroupNote ---
    public GroupNoteResponse updateGroupNote(Long procNum, UpdateGroupNoteRequest request) {
        return updateGroupNote(procNum, request, null);
    }

    public GroupNoteResponse updateGroupNote(Long procNum, UpdateGroupNoteRequest request, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        HttpEntity<UpdateGroupNoteRequest> entity = new HttpEntity<>(request);
        ResponseEntity<GroupNoteResponse> response = restTemplate.exchange(
                url + "/procedurelogs/" + procNum + "/GroupNote",
                HttpMethod.PUT,
                entity,
                GroupNoteResponse.class);
        return response.getBody();
    }

    // --- DELETE /procedurelogs/{procNum} ---
    public void deleteProcedureLog(Long procNum) {
        deleteProcedureLog(procNum, null);
    }

    public void deleteProcedureLog(Long procNum, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        restTemplate.delete(url + "/procedurelogs/" + procNum);
    }

    // --- DELETE /procedurelogs/{procNum}/GroupNote ---
    public void deleteGroupNote(Long procNum) {
        deleteGroupNote(procNum, null);
    }

    public void deleteGroupNote(Long procNum, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        restTemplate.delete(url + "/procedurelogs/" + procNum + "/GroupNote");
    }

    // ========================================================================
    // Query Endpoints
    // ========================================================================

    // --- POST /queries ---
    public void runQuery(QueryRequest request) {
        runQuery(request, null);
    }

    public void runQuery(QueryRequest request, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        restTemplate.postForObject(
                url + "/queries",
                request,
                Void.class);
    }

    // --- PUT /queries/ShortQuery ---
    public List<Map<String, Object>> runShortQuery(ShortQueryRequest request, Integer offset) {
        return runShortQuery(request, offset, null);
    }

    public List<Map<String, Object>> runShortQuery(ShortQueryRequest request, Integer offset, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(url + "/queries/ShortQuery");

        if (offset != null) {
            builder.queryParam("Offset", offset);
        }

        HttpEntity<ShortQueryRequest> entity = new HttpEntity<>(request);

        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.PUT,
                entity,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        return response.getBody();
    }

    // ========================================================================
    // Subscription Endpoints
    // ========================================================================

    // --- GET /subscriptions ---
    public List<SubscriptionResponse> getSubscriptions() {
        return getSubscriptions(null);
    }

    public List<SubscriptionResponse> getSubscriptions(String apiKey) {
        HttpEntity<Void> entity = new HttpEntity<>(buildAuthHeaders(apiKey));
        ResponseEntity<List<SubscriptionResponse>> response = restTemplate.exchange(
                baseUrl + "/subscriptions",
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<List<SubscriptionResponse>>() {});
        return response.getBody();
    }

    // --- POST /subscriptions ---
    public SubscriptionResponse createSubscription(SubscriptionRequest request) {
        return createSubscription(request, null);
    }

    public SubscriptionResponse createSubscription(SubscriptionRequest request, String apiKey) {
        HttpEntity<SubscriptionRequest> entity = new HttpEntity<>(request, buildAuthHeaders(apiKey));
        ResponseEntity<SubscriptionResponse> response = restTemplate.exchange(
                baseUrl + "/subscriptions",
                HttpMethod.POST,
                entity,
                SubscriptionResponse.class);
        return response.getBody();
    }

    // --- PUT /subscriptions/{SubscriptionNum} ---
    public SubscriptionResponse updateSubscription(Long subscriptionNum, SubscriptionRequest request) {
        return updateSubscription(subscriptionNum, request, null);
    }

    public SubscriptionResponse updateSubscription(Long subscriptionNum, SubscriptionRequest request, String apiKey) {
        HttpEntity<SubscriptionRequest> entity = new HttpEntity<>(request, buildAuthHeaders(apiKey));
        ResponseEntity<SubscriptionResponse> response = restTemplate.exchange(
                baseUrl + "/subscriptions/" + subscriptionNum,
                HttpMethod.PUT,
                entity,
                SubscriptionResponse.class);
        return response.getBody();
    }

    // --- DELETE /subscriptions/{subscriptionId} ---
    public void deleteSubscription(Long subscriptionId) {
        deleteSubscription(subscriptionId, null);
    }

    public void deleteSubscription(Long subscriptionId, String apiKey) {
        HttpEntity<Void> entity = new HttpEntity<>(buildAuthHeaders(apiKey));
        restTemplate.exchange(
                baseUrl + "/subscriptions/" + subscriptionId,
                HttpMethod.DELETE,
                entity,
                Void.class);
    }

    // ========================================================================
    // Document Endpoints
    // ========================================================================

    // --- GET /documents/{docNum} ---
    public DocumentResponse getDocument(Long docNum) {
        return getDocument(docNum, null);
    }

    public DocumentResponse getDocument(Long docNum, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        return restTemplate.getForObject(
                url + "/documents/" + docNum,
                DocumentResponse.class);
    }

    // --- GET /documents ---
    public List<DocumentResponse> getDocuments(Map<String, String> params) {
        return getDocuments(params, null);
    }

    public List<DocumentResponse> getDocuments(Map<String, String> params, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(url + "/documents");
        params.forEach(builder::queryParam);

        ResponseEntity<List<DocumentResponse>> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                null,
                DOCUMENT_LIST);
        return response.getBody();
    }

    public List<DocumentResponse> getDocuments(Map<String, String> params, String clinicBaseUrl, String apiKey) {
        String url = resolveBaseUrl(clinicBaseUrl);
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(url + "/documents");
        params.forEach(builder::queryParam);

        HttpEntity<Void> entity = new HttpEntity<>(buildAuthHeaders(apiKey));
        ResponseEntity<List<DocumentResponse>> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                entity,
                DOCUMENT_LIST);
        return response.getBody();
    }

    // --- POST /documents/Upload ---
    public DocumentResponse uploadDocument(UploadDocumentRequest request) {
        return uploadDocument(request, null);
    }

    public DocumentResponse uploadDocument(UploadDocumentRequest request, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        return restTemplate.postForObject(
                url + "/documents/Upload",
                request,
                DocumentResponse.class);
    }

    // --- POST /documents/SetByUrl ---
    public DocumentResponse setByUrl(SetByUrlRequest request) {
        return setByUrl(request, null);
    }

    public DocumentResponse setByUrl(SetByUrlRequest request, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        return restTemplate.postForObject(
                url + "/documents/SetByUrl",
                request,
                DocumentResponse.class);
    }

    // --- POST /documents/UploadSftp ---
    public DocumentResponse uploadSftp(UploadSftpRequest request) {
        return uploadSftp(request, null);
    }

    public DocumentResponse uploadSftp(UploadSftpRequest request, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        return restTemplate.postForObject(
                url + "/documents/UploadSftp",
                request,
                DocumentResponse.class);
    }

    // --- POST /documents/DownloadSftp ---
    public String downloadSftp(DownloadSftpRequest request) {
        return downloadSftp(request, null);
    }

    public String downloadSftp(DownloadSftpRequest request, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        HttpEntity<DownloadSftpRequest> entity = new HttpEntity<>(request);
        ResponseEntity<String> response = restTemplate.exchange(
                url + "/documents/DownloadSftp",
                HttpMethod.POST,
                entity,
                String.class);
        return response.getBody();
    }

    // --- POST /documents/Thumbnails ---
    public List<ThumbnailResult> getThumbnails(ThumbnailsRequest request) {
        return getThumbnails(request, null);
    }

    public List<ThumbnailResult> getThumbnails(ThumbnailsRequest request, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        HttpEntity<ThumbnailsRequest> entity = new HttpEntity<>(request);
        ResponseEntity<List<ThumbnailResult>> response = restTemplate.exchange(
                url + "/documents/Thumbnails",
                HttpMethod.POST,
                entity,
                new ParameterizedTypeReference<List<ThumbnailResult>>() {});
        return response.getBody();
    }

    // --- POST /documents/DownloadMount ---
    public List<ThumbnailResult> downloadMount(DownloadMountRequest request) {
        return downloadMount(request, null);
    }

    public List<ThumbnailResult> downloadMount(DownloadMountRequest request, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        HttpEntity<DownloadMountRequest> entity = new HttpEntity<>(request);
        ResponseEntity<List<ThumbnailResult>> response = restTemplate.exchange(
                url + "/documents/DownloadMount",
                HttpMethod.POST,
                entity,
                new ParameterizedTypeReference<List<ThumbnailResult>>() {});
        return response.getBody();
    }

    // --- PUT /documents/{docNum} ---
    public DocumentResponse updateDocument(Long docNum, UpdateDocumentRequest request) {
        return updateDocument(docNum, request, null);
    }

    public DocumentResponse updateDocument(Long docNum, UpdateDocumentRequest request, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        HttpEntity<UpdateDocumentRequest> entity = new HttpEntity<>(request);
        ResponseEntity<DocumentResponse> response = restTemplate.exchange(
                url + "/documents/" + docNum,
                HttpMethod.PUT,
                entity,
                DocumentResponse.class);
        return response.getBody();
    }

    // --- DELETE /documents/{docNum} ---
    public void deleteDocument(Long docNum) {
        deleteDocument(docNum, null);
    }

    public void deleteDocument(Long docNum, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        restTemplate.delete(url + "/documents/" + docNum);
    }
    // --- GET /patfields ---
    public List<PatFieldResponse> getPatFields(Map<String, String> params) {
        return getPatFields(params, null);
    }

    public List<PatFieldResponse> getPatFields(Map<String, String> params, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(url + "/patfields");
        params.forEach(builder::queryParam);

        ResponseEntity<List<PatFieldResponse>> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                null,
                PAT_FIELD_LIST);
        return response.getBody();
    }

    public List<PatFieldResponse> getPatFields(Map<String, String> params, String clinicBaseUrl, String apiKey) {
        String url = resolveBaseUrl(clinicBaseUrl);
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(url + "/patfields");
        params.forEach(builder::queryParam);

        HttpHeaders headers = buildAuthHeaders(apiKey);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(params, headers);

        ResponseEntity<List<PatFieldResponse>> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                entity,
                PAT_FIELD_LIST);
        return response.getBody();
    }

    // --- GET /patfields/{patFieldNum} ---
    public PatFieldResponse getPatField(Long patFieldNum) {
        return getPatField(patFieldNum, null);
    }

    public PatFieldResponse getPatField(Long patFieldNum, String clinicBaseUrl) {
        String url = resolveBaseUrl(clinicBaseUrl);
        return restTemplate.getForObject(url + "/patfields/" + patFieldNum, PatFieldResponse.class);
    }

    public PatFieldResponse getPatField(Long patFieldNum, String clinicBaseUrl, String apiKey) {
        String url = resolveBaseUrl(clinicBaseUrl);
        HttpHeaders headers = buildAuthHeaders(apiKey);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(url + "/patfields/" + patFieldNum, HttpMethod.GET, entity, PatFieldResponse.class).getBody();
    }

    // ========================================================================
    // Reference data endpoints (providers / operatories / schedules / toothinitial)
    // ========================================================================

    // --- GET /providers ---
    public List<ProviderResponse> getProviders(Map<String, String> params, String clinicBaseUrl, String apiKey) {
        String url = resolveBaseUrl(clinicBaseUrl);
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url + "/providers");
        params.forEach(builder::queryParam);

        HttpHeaders headers = buildAuthHeaders(apiKey);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(params, headers);

        ResponseEntity<List<ProviderResponse>> response = restTemplate.exchange(
                builder.toUriString(), HttpMethod.GET, entity, PROVIDER_LIST);
        return response.getBody();
    }

    // --- GET /operatories ---
    public List<OperatoryResponse> getOperatories(Map<String, String> params, String clinicBaseUrl, String apiKey) {
        String url = resolveBaseUrl(clinicBaseUrl);
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url + "/operatories");
        params.forEach(builder::queryParam);

        HttpHeaders headers = buildAuthHeaders(apiKey);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(params, headers);

        ResponseEntity<List<OperatoryResponse>> response = restTemplate.exchange(
                builder.toUriString(), HttpMethod.GET, entity, OPERATORY_LIST);
        return response.getBody();
    }

    // --- GET /schedules ---
    public List<ScheduleResponse> getSchedules(Map<String, String> params, String clinicBaseUrl, String apiKey) {
        String url = resolveBaseUrl(clinicBaseUrl);
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url + "/schedules");
        params.forEach(builder::queryParam);

        HttpHeaders headers = buildAuthHeaders(apiKey);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(params, headers);

        ResponseEntity<List<ScheduleResponse>> response = restTemplate.exchange(
                builder.toUriString(), HttpMethod.GET, entity, SCHEDULE_LIST);
        return response.getBody();
    }

    // --- GET /toothinitial ---
    public List<ToothInitialResponse> getToothInitials(Map<String, String> params, String clinicBaseUrl, String apiKey) {
        String url = resolveBaseUrl(clinicBaseUrl);
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url + "/toothinitial");
        params.forEach(builder::queryParam);

        HttpHeaders headers = buildAuthHeaders(apiKey);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(params, headers);

        ResponseEntity<List<ToothInitialResponse>> response = restTemplate.exchange(
                builder.toUriString(), HttpMethod.GET, entity, TOOTH_INITIAL_LIST);
        return response.getBody();
    }

    // ========================================================================
    // Allergy endpoints
    // ========================================================================

    public List<Map<String, Object>> getAllergies(Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(
                resolveBaseUrl(null) + "/allergies");
        params.forEach(builder::queryParam);
        return restTemplate.exchange(
                builder.toUriString(), HttpMethod.GET, null, ALLERGY_LIST).getBody();
    }

    public Map<String, Object> getAllergy(Long allergyNum) {
        return restTemplate.exchange(
                resolveBaseUrl(null) + "/allergies/" + allergyNum,
                HttpMethod.GET,
                null,
                ALLERGY_MAP).getBody();
    }

    public Map<String, Object> createAllergy(Map<String, Object> request) {
        return restTemplate.exchange(
                resolveBaseUrl(null) + "/allergies",
                HttpMethod.POST,
                new HttpEntity<>(request),
                ALLERGY_MAP).getBody();
    }

    public Map<String, Object> updateAllergy(Long allergyNum, Map<String, Object> request) {
        return restTemplate.exchange(
                resolveBaseUrl(null) + "/allergies/" + allergyNum,
                HttpMethod.PUT,
                new HttpEntity<>(request),
                ALLERGY_MAP).getBody();
    }

    public void deleteAllergy(Long allergyNum) {
        restTemplate.exchange(
                resolveBaseUrl(null) + "/allergies/" + allergyNum,
                HttpMethod.DELETE,
                null,
                Void.class);
    }
}
