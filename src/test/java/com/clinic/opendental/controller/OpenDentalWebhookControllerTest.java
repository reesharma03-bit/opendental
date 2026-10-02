package com.clinic.opendental.controller;

import java.util.List;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clinic.opendental.dto.query.QueryRequest;
import com.clinic.opendental.service.WebhookService;

@WebMvcTest(OpenDentalWebhookController.class)
class OpenDentalWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WebhookService webhookService;

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
    // Document webhook
    // ========================================================================

    @Test
    void documentWebhookReturnsOk() throws Exception {
        String payload = """
                [
                  {
                    "DocNum": 456,
                    "Description": "X-Ray",
                    "ImgType": "jpg",
                    "FileName": "xray.jpg"
                  }
                ]
                """;

        mockMvc.perform(post("/api/webhooks/opendental/document")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));

        verify(webhookService).processDocumentWebhook(any(List.class));
    }

    @Test
    void documentWebhookAcceptsEmptyBody() throws Exception {
        mockMvc.perform(post("/api/webhooks/opendental/document")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));

        verify(webhookService).processDocumentWebhook(isNull());
    }

    // ========================================================================
    // ProcedureLog webhook
    // ========================================================================

    @Test
    void procedureLogWebhookReturnsOk() throws Exception {
        String payload = """
                [
                  {
                    "ProcNum": 789,
                    "ProcStatus": "Complete",
                    "ProvNum": 1,
                    "ProvAbbr": "DOC1",
                    "Descript": "Cleaning"
                  }
                ]
                """;

        mockMvc.perform(post("/api/webhooks/opendental/procedurelog")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));

        verify(webhookService).processProcedureLogWebhook(any(List.class));
    }

    @Test
    void procedureLogWebhookAcceptsEmptyBody() throws Exception {
        mockMvc.perform(post("/api/webhooks/opendental/procedurelog")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));

        verify(webhookService).processProcedureLogWebhook(isNull());
    }

    // ========================================================================
    // Query webhook
    // ========================================================================

    @Test
    void queryWebhookReturnsOk() throws Exception {
        String payload = """
                {
                  "SqlCommand": "SELECT * FROM patient",
                  "SftpAddress": "host",
                  "SftpUsername": "user",
                  "SftpPassword": "pass"
                }
                """;

        mockMvc.perform(post("/api/webhooks/opendental/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));

        verify(webhookService).processQueryWebhook(any(QueryRequest.class));
    }

    @Test
    void queryWebhookAcceptsEmptyBody() throws Exception {
        mockMvc.perform(post("/api/webhooks/opendental/query")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));

        verify(webhookService).processQueryWebhook(isNull());
    }
}
