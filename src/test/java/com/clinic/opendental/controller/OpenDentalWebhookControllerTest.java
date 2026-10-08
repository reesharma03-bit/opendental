package com.clinic.opendental.controller;

import java.util.List;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.service.SelectedPatients;
import com.clinic.opendental.service.WebhookClinics;
import com.clinic.opendental.service.WebhookService;
import com.clinic.opendental.service.Impl.ResourceMirrorService;

@WebMvcTest(OpenDentalWebhookController.class)
// Controller behaviour only; who may call what is covered by SecurityRulesTest.
@AutoConfigureMockMvc(addFilters = false)
class OpenDentalWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WebhookService webhookService;

    @MockBean
    private WebhookClinics webhookClinics;

    @MockBean
    private ResourceMirrorService resourceMirror;

    @MockBean
    private SelectedPatients selectedPatients;

    // ========================================================================
    // Patient webhook
    // ========================================================================

    @Test
    void patientWebhookReturnsOk() throws Exception {
        String payload = """
                [
                  {
                    "PatNum": 48,
                    "LName": "Test12",
                    "FName": "Tesat12",
                    "PatStatus": "Patient",
                    "Gender": "Unknown",
                    "Position": "Single",
                    "Birthdate": "0001-01-01",
                    "PriProv": 1,
                    "priProvAbbr": "DOC1",
                    "BillingType": "Standard",
                    "ImageFolder": "TestTesat26",
                    "DateTStamp": "2026-08-02 09:45:11",
                    "SecUserNumEntry": 1,
                    "SecDateEntry": "2026-08-02"
                  }
                ]
                """;

        mockMvc.perform(post("/api/webhooks/opendental/patient")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));

        verify(webhookService).processPatientWebhook(any(List.class));
    }

    @Test
    void patientWebhookAcceptsEmptyBody() throws Exception {
        mockMvc.perform(post("/api/webhooks/opendental/patient")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));

        verify(webhookService).processPatientWebhook(isNull());
    }

    // ========================================================================
    // Appointment webhook
    // ========================================================================

    @Test
    void appointmentWebhookReturnsOk() throws Exception {
        // Mirrors the real Open Dental payload, including the conflicting
        // numeric "Confirmed" and text "confirmed" keys, to guard against
        // regression of the case-collision deserialization bug.
        String payload = """
                [
                  {
                    "AptNum": 59,
                    "PatNum": 11,
                    "AptStatus": "UnschedList",
                    "Pattern": "//////",
                    "Confirmed": 21,
                    "confirmed": "Confirmed",
                    "TimeLocked": "true",
                    "Op": 0,
                    "Note": "",
                    "ProvNum": 2,
                    "provAbbr": "HYG1",
                    "ProvHyg": 0,
                    "AptDateTime": "0001-01-01 00:00:00",
                    "NextAptNum": 0,
                    "UnschedStatus": 92,
                    "unschedStatus": "Appointment Scheduled",
                    "IsNewPatient": "false",
                    "ProcDescript": "PA, Pano, 4-BWX",
                    "Assistant": 0,
                    "ClinicNum": 0,
                    "IsHygiene": "false",
                    "DateTStamp": "2026-08-04 09:47:36",
                    "DateTimeArrived": "0001-01-01 00:00:00",
                    "DateTimeSeated": "0001-01-01 00:00:00",
                    "DateTimeDismissed": "0001-01-01 00:00:00",
                    "InsPlan1": 12,
                    "InsPlan2": 0,
                    "DateTimeAskedToArrive": "0001-01-01 00:00:00",
                    "colorOverride": "",
                    "AppointmentTypeNum": 0,
                    "SecUserNumEntry": 1,
                    "SecDateTEntry": "2026-08-04 09:47:36",
                    "Priority": "Normal",
                    "PatternSecondary": "//////",
                    "ItemOrderPlanned": 0,
                    "IsMirrored": "false"
                  }
                ]
                """;

        mockMvc.perform(post("/api/webhooks/opendental/appointment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));

        verify(webhookService).processAppointmentWebhook(any(List.class));
    }

    @Test
    void appointmentWebhookAcceptsEmptyBody() throws Exception {
        mockMvc.perform(post("/api/webhooks/opendental/appointment")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));

        verify(webhookService).processAppointmentWebhook(isNull());
    }

    // ========================================================================
    // Routes Open Dental never calls are gone (one of them ran SQL for anyone)
    // ========================================================================

    @Test
    void routesOpenDentalDoesNotSendAreGone() throws Exception {
        for (String route : List.of("document", "procedurelog", "query")) {
            mockMvc.perform(post("/api/webhooks/opendental/" + route)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"SqlCommand\":\"SELECT * FROM patient\"}"))
                    .andExpect(status().is4xxClientError());
        }
        org.mockito.Mockito.verifyNoInteractions(webhookService);
    }

    // ========================================================================
    // Only a known practice's API key gets in
    // ========================================================================

    @Test
    void aWebhookWithoutAKnownPracticeKeyIsRefused() throws Exception {
        when(webhookClinics.clinic()).thenThrow(new WebhookClinics.UnknownPractice("Webhook without an Open Dental API key"));

        mockMvc.perform(post("/api/webhooks/opendental/patient")
                        .contentType(MediaType.APPLICATION_JSON).content("[{\"PatNum\":1,\"LName\":\"Evil\"}]"))
                .andExpect(status().isUnauthorized());

        org.mockito.Mockito.verifyNoInteractions(webhookService, resourceMirror);
    }

    // ========================================================================
    // LabCase / MedicationPat (kept in od_resource_records)
    // ========================================================================

    @Test
    void labCasesAndPatientMedicationsAreSavedAndRemoved() throws Exception {
        Clinic clinic = Clinic.builder().clinicCode("A").build();
        when(webhookClinics.clinic()).thenReturn(clinic);

        mockMvc.perform(post("/api/webhooks/opendental/labcase").contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"LabCaseNum\":5,\"PatNum\":48,\"LaboratoryNum\":2}]"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/webhooks/opendental/medicationpatdeleted").contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"MedicationPatNum\":9}]"))
                .andExpect(status().isOk());

        verify(webhookService).processLabCaseWebhook(argThat(list -> list.size() == 1 && list.get(0).getLabCaseNum() == 5L));
        verify(webhookService).processMedicationPatDeletedWebhook(argThat(list -> list.get(0).getMedicationPatNum() == 9L));
        verify(resourceMirror).applyWebhookRows(eq(clinic), eq("labcases"), anyList());
        verify(resourceMirror).removeWebhookRows(eq(clinic), eq("medicationpats"), anyList());
    }

    @Test
    void scheduleChangesAlsoUpdateTheCatalogCopy() throws Exception {
        Clinic clinic = Clinic.builder().clinicCode("A").build();
        when(webhookClinics.clinic()).thenReturn(clinic);

        mockMvc.perform(post("/api/webhooks/opendental/scheduledeleted").contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"ScheduleNum\":77}]"))
                .andExpect(status().isOk());

        verify(webhookService).processScheduleDeletedWebhook(any());
        verify(resourceMirror).removeWebhookRows(eq(clinic), eq("schedules"), anyList());
    }

    @Test
    void aPatientOpenedInOpenDentalIsRememberedPerWorkstation() throws Exception {
        java.util.UUID clinicId = java.util.UUID.randomUUID();
        when(webhookClinics.clinic()).thenReturn(Clinic.builder().id(clinicId).clinicCode("A").build());

        mockMvc.perform(post("/api/webhooks/opendental/patientselected").header("Workstation", "FRONTDESK1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"PatNum\":48,\"LName\":\"Smith\",\"FName\":\"John\"}"))
                .andExpect(status().isOk());

        verify(selectedPatients).record(clinicId, "FRONTDESK1", 48L, "Smith, John");
    }
}
