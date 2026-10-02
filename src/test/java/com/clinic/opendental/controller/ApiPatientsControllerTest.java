package com.clinic.opendental.controller;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.clinic.opendental.dto.patient.CreatePatientRequest;
import com.clinic.opendental.dto.patient.PatientResponse;
import com.clinic.opendental.dto.patient.PatientSimpleResponse;
import com.clinic.opendental.dto.patient.UpdatePatientRequest;
import com.clinic.opendental.service.PatientService;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-slice tests for {@link ApiPatientsController}.
 *
 * <p>The patient endpoints proxy the Open Dental REST API through
 * {@code PatientService}, so the service is mocked here: these tests verify
 * request mapping, bean validation, status codes and the JSON contract without
 * needing a live Open Dental instance.</p>
 *
 * <p>The app's REST API uses the global Jackson {@code SNAKE_CASE} strategy, so
 * request bodies are posted with snake_case keys and responses are asserted on
 * snake_case paths ({@code pat_num}, {@code l_name}, ...).</p>
 */
@WebMvcTest(ApiPatientsController.class)
class ApiPatientsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PatientService patientService;

    // ================================================================
    // Test: GET /{patNum} returns the patient
    // ================================================================
    @Test
    void getSinglePatientReturnsSamplePatient() throws Exception {
        when(patientService.getPatient(48L)).thenReturn(PatientResponse.builder()
                .PatNum(48L).LName("Smith").FName("John")
                .Language("spa").BalTotal(388.0).build());

        mockMvc.perform(get("/api/patients/48"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pat_num", is(48)))
                .andExpect(jsonPath("$.lname", is("Smith")))
                .andExpect(jsonPath("$.language", is("spa")))
                .andExpect(jsonPath("$.bal_total", is(388.0)));
    }

    @Test
    void getSimplePatientsIncludesServerDateTimeAndBalanceFields() throws Exception {
        when(patientService.getSimplePatients(any())).thenReturn(List.of(
                PatientSimpleResponse.builder()
                        .serverDateTime("2026-08-22 12:00:00")
                        .PatNum(48L).LName("Smith")
                        .EstBalance(0.0).Language("spa")
                        .build()));

        mockMvc.perform(get("/api/patients/Simple").param("LName", "smi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].server_date_time").exists())
                .andExpect(jsonPath("$[0].est_balance", is(0.0)))
                .andExpect(jsonPath("$[0].language", is("spa")));
    }

    @Test
    void getMultiplePatientsFiltersBySearchCriteria() throws Exception {
        when(patientService.getPatients(any())).thenReturn(List.of(
                PatientResponse.builder()
                        .PatNum(12L).LName("Smith").FName("Jane")
                        .Birthdate("1976-05-24").build()));

        mockMvc.perform(get("/api/patients")
                        .param("LName", "smi").param("Birthdate", "1976-05-24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lname", is("Smith")))
                .andExpect(jsonPath("$[0].birthdate", is("1976-05-24")));

        verify(patientService).getPatients(eq(Map.of("LName", "smi", "Birthdate", "1976-05-24")));
    }

    @Test
    void createPatientReturnsCreatedResponseAndLocationHeader() throws Exception {
        when(patientService.createPatient(any(CreatePatientRequest.class)))
                .thenReturn(PatientResponse.builder()
                        .PatNum(1001L).LName("Doe").FName("John").Language("eng").build());

        Map<String, Object> body = Map.of(
                "lname", "Doe",
                "fname", "John",
                "language", "eng"
        );

        mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/patients/1001"))
                .andExpect(jsonPath("$.pat_num", is(1001)))
                .andExpect(jsonPath("$.lname", is("Doe")))
                .andExpect(jsonPath("$.language", is("eng")));
    }

    @Test
    void updatePatientReturnsUpdatedFields() throws Exception {
        when(patientService.updatePatient(eq(47L), any(UpdatePatientRequest.class)))
                .thenReturn(PatientResponse.builder()
                        .PatNum(47L).Preferred("Janie")
                        .PreferContactMethod("WirelessPh").build());

        Map<String, Object> body = Map.of(
                "preferred", "Janie",
                "prefer_contact_method", "WirelessPh"
        );

        mockMvc.perform(put("/api/patients/47")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pat_num", is(47)))
                .andExpect(jsonPath("$.preferred", is("Janie")))
                .andExpect(jsonPath("$.prefer_contact_method", is("WirelessPh")));
    }

    @Test
    void deletePatientIsRejectedByApi() throws Exception {
        mockMvc.perform(delete("/api/patients/47"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error", is("Patients cannot be deleted via the Open Dental API. Delete is only supported in Open Dental.")));
    }
}