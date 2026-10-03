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

@WebMvcTest(SchedulingResourcesController.class)
// Controller behaviour only; who may call what is covered by SecurityRulesTest.
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "opendental.base-url=https://od.example/api/v1")
class SchedulingResourcesControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private RestTemplate restTemplate;

    @Test
    void documentedCollectionAndSingleReadsForwardToUpstreamPaths() throws Exception {
        when(restTemplate.exchange(any(URI.class), any(HttpMethod.class), any(HttpEntity.class), eq(Object.class)))
                .thenReturn(ResponseEntity.ok(Map.of("ResponseKey", "retained")));
        List<String> urls = List.of(
                "/api/appointmenttypes", "/api/appointmenttypes/1",
                "/api/apptfields?AptNum=101", "/api/apptfields/11",
                "/api/apptfielddefs", "/api/apptfielddefs/7",
                "/api/asapcomms?ClinicNum=1", "/api/asapcomms/192",
                "/api/clockevents?EmployeeNum=13", "/api/clockevents/19123",
                "/api/histappointments?PatNum=1",
                "/api/operatories", "/api/operatories/1",
                "/api/scheduleops?ScheduleNum=1093",
                "/api/schedules?date=2022-03-07", "/api/schedules/24555");

        for (String url : urls) {
            mockMvc.perform(get(url))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ResponseKey", is("retained")));
        }
        verify(restTemplate, times(urls.size())).exchange(any(URI.class), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(Object.class));
        verify(restTemplate).exchange(eq(URI.create("https://od.example/api/v1/appointmenttypes/1")),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(Object.class));
        verify(restTemplate).exchange(eq(URI.create("https://od.example/api/v1/scheduleops?ScheduleNum=1093")),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(Object.class));
    }

    @Test
    void writesForwardUsingOpenDentalFieldNamesAndPreserveUpstreamStatusAndBody() throws Exception {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(Object.class)))
                .thenReturn(ResponseEntity.status(201).body(Map.of(
                        "AppointmentTypeNum", 20, "AppointmentTypeName", "WebSched")));

        mockMvc.perform(post("/api/appointmenttypes")
                        .contentType("application/json")
                        .content("{\"AppointmentTypeName\":\"WebSched\",\"IsHidden\":\"false\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.AppointmentTypeNum", is(20)))
                .andExpect(jsonPath("$.AppointmentTypeName", is("WebSched")));

        verify(restTemplate).exchange(eq(URI.create("https://od.example/api/v1/appointmenttypes")),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(Object.class));

        reset(restTemplate);
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class), eq(Object.class)))
                .thenReturn(ResponseEntity.ok(Map.of("ApptFieldNum", 11, "FieldValue", "Yes")));

        mockMvc.perform(put("/api/apptfields/11")
                        .contentType("application/json")
                        .content("{\"FieldValue\":\"Yes\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ApptFieldNum", is(11)));

        verify(restTemplate).exchange(eq(URI.create("https://od.example/api/v1/apptfields/11")),
                eq(HttpMethod.PUT), any(HttpEntity.class), eq(Object.class));

        reset(restTemplate);
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(Object.class)))
                .thenReturn(ResponseEntity.status(201).body(Map.of("AsapCommNum", 2374)));

        mockMvc.perform(post("/api/asapcomms")
                        .contentType("application/json")
                        .content("{\"op\":2,\"aptNum\":11939,\"dateTimeStart\":\"2023-10-18 14:00:00\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.AsapCommNum", is(2374)));

        reset(restTemplate);
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Object.class)))
                .thenReturn(ResponseEntity.ok().build());

        mockMvc.perform(delete("/api/apptfields/11")).andExpect(status().isOk());
        verify(restTemplate).exchange(eq(URI.create("https://od.example/api/v1/apptfields/11")),
                eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Object.class));
    }

    @Test
    void unsupportedMethodsAndInvalidInputsDoNotReachUpstream() throws Exception {
        mockMvc.perform(delete("/api/clockevents/1")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put("/api/operatories/5").contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/api/schedules").contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(get("/api/histappointments/5")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(get("/api/scheduleops/5")).andExpect(status().isMethodNotAllowed());

        mockMvc.perform(get("/api/clockevents").param("Bogus", "1")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/schedules").param("date", "03-07-2022")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/histappointments").param("AptStatus", "Bogus")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/appointmenttypes").param("Offset", "1")).andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/appointmenttypes").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/apptfielddefs").contentType("application/json")
                        .content("{\"FieldName\":\"Temperature\",\"FieldType\":\"PickList\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/asapcomms").contentType("application/json")
                        .content("{\"op\":2,\"dateTimeStart\":\"2023-10-18 14:00:00\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/asapcomms").contentType("application/json")
                        .content("{\"op\":2,\"aptNum\":1,\"dateTimeStart\":\"10/18/2023\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(restTemplate);
    }

    @Test
    void returnsUpstreamErrorsAndClearBadGatewayOnConnectivityFailure() throws Exception {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(Object.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.FORBIDDEN, "Denied", null,
                        "{\"message\":\"upstream denied\"}".getBytes(), null));
        mockMvc.perform(get("/api/operatories"))
                .andExpect(status().isForbidden())
                .andExpect(content().string("{\"message\":\"upstream denied\"}"));

        reset(restTemplate);
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(Object.class)))
                .thenThrow(new ResourceAccessException("connection refused"));
        mockMvc.perform(get("/api/operatories"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error", is("Open Dental connectivity failure.")));
    }
}