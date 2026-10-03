package com.clinic.opendental.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClinicalCareResourcesController.class)
// Controller behaviour only; who may call what is covered by SecurityRulesTest.
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "opendental.base-url=https://od.example/api/v1")
class ClinicalCareResourcesControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private RestTemplate restTemplate;

    @Test
    void documentedReadsForwardToUpstreamPaths() throws Exception {
        when(restTemplate.exchange(any(URI.class), any(HttpMethod.class), any(HttpEntity.class), eq(Object.class)))
                .thenReturn(ResponseEntity.ok(Map.of("ResponseKey", "retained")));
        List<String> urls = List.of(
                "/api/autonotecontrols",
                "/api/autonotes?Category=340",
                "/api/chartmodules/13/ProgNotes",
                "/api/chartmodules/15/PatientInfo",
                "/api/chartmodules/31/PlannedAppts?Offset=100",
                "/api/codegroups?IsHidden=false&ShowInHistory=true",
                "/api/codegroups/9",
                "/api/perioexams?PatNum=236",
                "/api/perioexams/171",
                "/api/periomeasures?PerioExamNum=3",
                "/api/procedurecodes",
                "/api/procedurecodes/35",
                "/api/procnotes?PatNum=426&ProcNum=1234",
                "/api/proctps?TreatPlanNum=963",
                "/api/toothinitials?PatNum=13",
                "/api/treatplanattaches?TreatPlanNum=1845",
                "/api/treatplans?PatNum=1897&TPStatus=Saved");

        for (String url : urls) {
            mockMvc.perform(get(url))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ResponseKey", is("retained")));
        }
        verify(restTemplate).exchange(eq(URI.create("https://od.example/api/v1/codegroups/9")),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(Object.class));
        verify(restTemplate).exchange(eq(URI.create(
                        "https://od.example/api/v1/chartmodules/31/PlannedAppts?Offset=100")),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(Object.class));
    }

    @Test
    void writesForwardUsingOpenDentalFieldNamesAndPreserveUpstreamStatusAndBody() throws Exception {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(Object.class)))
                .thenReturn(ResponseEntity.status(201).body(Map.of(
                        "AutoNoteNum", 10, "AutoNoteName", "Allergens")));

        mockMvc.perform(post("/api/autonotes").contentType("application/json")
                        .content("{\"AutoNoteName\":\"Allergens\",\"MainText\":\"Allergies: [Prompt:\\\"Allergies\\\"]\",\"Category\":399}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.AutoNoteNum", is(10)));
        verify(restTemplate).exchange(eq(URI.create("https://od.example/api/v1/autonotes")),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(Object.class));

        reset(restTemplate);
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class), eq(Object.class)))
                .thenReturn(ResponseEntity.ok(Map.of("CodeGroupNum", 23, "IsHidden", "true")));

        mockMvc.perform(put("/api/codegroups/23").contentType("application/json")
                        .content("{\"IsHidden\":\"true\",\"ShowInAgeLimit\":\"false\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.CodeGroupNum", is(23)));
        verify(restTemplate).exchange(eq(URI.create("https://od.example/api/v1/codegroups/23")),
                eq(HttpMethod.PUT), any(HttpEntity.class), eq(Object.class));

        reset(restTemplate);
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Object.class)))
                .thenReturn(ResponseEntity.ok().build());

        mockMvc.perform(delete("/api/treatplans/115")).andExpect(status().isOk());
        verify(restTemplate).exchange(eq(URI.create("https://od.example/api/v1/treatplans/115")),
                eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Object.class));
    }

    @Test
    void documentedSpecialRoutesForward() throws Exception {
        when(restTemplate.exchange(any(URI.class), any(HttpMethod.class), any(HttpEntity.class), eq(Object.class)))
                .thenReturn(ResponseEntity.ok(Map.of("ResponseKey", "retained")));

        mockMvc.perform(put("/api/toothinitials/ClearMovements").contentType("application/json")
                        .content("{\"PatNum\":72,\"toothNums\":[\"1\",\"2\",\"3\",\"A\"]}"))
                .andExpect(status().isOk());
        verify(restTemplate).exchange(eq(URI.create(
                        "https://od.example/api/v1/toothinitials/ClearMovements")),
                eq(HttpMethod.PUT), any(HttpEntity.class), eq(Object.class));

        mockMvc.perform(get("/api/chartmodules/13/ProgNotes")).andExpect(status().isOk());
    }

    @Test
    void unsupportedMethodsAndInvalidInputsDoNotReachUpstream() throws Exception {
        mockMvc.perform(delete("/api/autonotes/5")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(get("/api/autonotes/5")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(get("/api/proctps/976")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/api/proctps").contentType("application/json").content("{\"PatNum\":1}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(get("/api/treatplanattaches/70")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/api/chartmodules").contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(get("/api/chartmodules/13/NotAModule")).andExpect(status().isNotFound());

        mockMvc.perform(get("/api/treatplans").param("Bogus", "1")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/perioexams").param("ExamDate", "04-01-2023")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/treatplans").param("TPStatus", "Bogus")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/codegroups").param("ShowInHistory", "maybe")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/proctps")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/treatplanattaches")).andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/autonotes").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/autonotecontrols").contentType("application/json")
                        .content("{\"Descript\":\"Meds\",\"ControlType\":\"MultiResponse\",\"ControlLabel\":\"Medications\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/autonotecontrols").contentType("application/json")
                        .content("{\"Descript\":\"Meds\",\"ControlType\":\"Bogus\",\"ControlLabel\":\"M\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/periomeasures").contentType("application/json")
                        .content("{\"PerioExamNum\":5,\"SequenceType\":\"Probing\",\"IntTooth\":44}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/periomeasures").contentType("application/json")
                        .content("{\"PerioExamNum\":5,\"SequenceType\":\"Bogus\",\"IntTooth\":8}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/toothinitials").contentType("application/json")
                        .content("{\"PatNum\":13,\"ToothNum\":32,\"InitialType\":\"Rotate\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/procedurecodes").contentType("application/json")
                        .content("{\"ProcCode\":\"D0120\",\"Descript\":\"x\",\"AbbrDesc\":\"PerEx\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/codegroups").contentType("application/json")
                        .content("{\"GroupName\":\"SRP\",\"ShowInHistory\":\"true\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/proctps/1").contentType("application/json")
                        .content("{\"Discount\":17.00,\"ProcStatus\":\"ZZ\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(restTemplate);
    }

    @Test
    void returnsUpstreamErrorsAndClearBadGatewayOnConnectivityFailure() throws Exception {
        when(restTemplate.exchange(any(URI.class), any(HttpMethod.class), any(HttpEntity.class), eq(Object.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.FORBIDDEN, "Denied", null,
                        "{\"message\":\"upstream denied\"}".getBytes(), null));
        mockMvc.perform(get("/api/procedurecodes"))
                .andExpect(status().isForbidden())
                .andExpect(content().string("{\"message\":\"upstream denied\"}"));

        reset(restTemplate);
        when(restTemplate.exchange(any(URI.class), any(HttpMethod.class), any(HttpEntity.class), eq(Object.class)))
                .thenThrow(new ResourceAccessException("connection refused"));
        mockMvc.perform(get("/api/procedurecodes"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error", is("Open Dental connectivity failure.")));
    }
}