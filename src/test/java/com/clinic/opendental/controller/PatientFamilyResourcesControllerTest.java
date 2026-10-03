package com.clinic.opendental.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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

@WebMvcTest(PatientFamilyResourcesController.class)
// Controller behaviour only; who may call what is covered by SecurityRulesTest.
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "opendental.base-url=https://od.example/api/v1")
class PatientFamilyResourcesControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private RestTemplate restTemplate;

    @Test
    void allTwentyResourceCollectionAndSingleReadsForwardToDocumentedUpstreamPaths() throws Exception {
        when(restTemplate.exchange(any(URI.class), any(HttpMethod.class), any(HttpEntity.class), eq(Object.class)))
                .thenReturn(ResponseEntity.ok(Map.of("ResponseKey", "retained")));
        List<String> urls = List.of(
                "/api/allergydefs", "/api/diseasedefs", "/api/diseases", "/api/ehrpatients/1",
                "/api/familymodules/1/Insurance", "/api/guardians", "/api/medicationpats",
                "/api/medications", "/api/patientnotes", "/api/patientraces?PatNum=2",
                "/api/patfielddefs", "/api/patfields", "/api/patplans", "/api/patrestrictions",
                "/api/pharmacies", "/api/popups?PatNum=2", "/api/recalls", "/api/recalltypes",
                "/api/rxpats", "/api/vitalsigns");

        for (String url : urls) {
            mockMvc.perform(get(url))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ResponseKey", is("retained")));
        }
        verify(restTemplate, times(20)).exchange(any(URI.class), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(Object.class));
        verify(restTemplate).exchange(eq(URI.create("https://od.example/api/v1/familymodules/1/Insurance")),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(Object.class));
    }

    @Test
    void forwardsWritesUsingOpenDentalFieldNamesAndPreservesUpstreamStatusAndBody() throws Exception {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(Object.class)))
                .thenReturn(ResponseEntity.status(201).body(Map.of("AllergyDefNum", 17, "Description", "Latex")));

        mockMvc.perform(post("/api/allergydefs")
                        .contentType("application/json")
                        .content("{\"Description\":\"Latex\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.AllergyDefNum", is(17)))
                .andExpect(jsonPath("$.Description", is("Latex")));

        verify(restTemplate).exchange(eq(URI.create("https://od.example/api/v1/allergydefs")),
                eq(HttpMethod.POST), argThat((HttpEntity<?> e) -> {
                    Map<?, ?> body = (Map<?, ?>) e.getBody();
                    return "Latex".equals(body.get("Description"));
                }), eq(Object.class));
    }

    @Test
    void documentedCreateAndUpdateEndpointsForResourcesAreAvailableWithoutLiveDataMutation() throws Exception {
        when(restTemplate.exchange(any(URI.class), any(HttpMethod.class), any(HttpEntity.class), eq(Object.class)))
                .thenReturn(ResponseEntity.ok(Map.of("ok", true)));
        perform("POST", "/api/allergydefs", "{\"Description\":\"Latex\"}");
        perform("POST", "/api/diseasedefs", "{\"DiseaseName\":\"Asthma\"}");
        perform("POST", "/api/diseases", "{\"PatNum\":8,\"diseaseDefName\":\"Asthma\"}");
        perform("PUT", "/api/ehrpatients/8", "{\"DischargeDate\":\"0001-01-01\"}");
        perform("POST", "/api/guardians", "{\"PatNumChild\":8,\"PatNumGuardian\":9,\"Relationship\":\"Parent\"}");
        perform("POST", "/api/medicationpats", "{\"PatNum\":8}");
        perform("POST", "/api/medications", "{\"MedName\":\"Acetaminophen\"}");
        perform("PUT", "/api/patientnotes/8", "{\"Medical\":\"Note\"}");
        perform("POST", "/api/patfielddefs", "{\"FieldName\":\"Test\",\"FieldType\":\"Text\"}");
        perform("PUT", "/api/patfields", "{\"PatNum\":8,\"FieldName\":\"Test\",\"FieldValue\":\"x\"}");
        perform("POST", "/api/patplans", "{\"PatNum\":8,\"InsSubNum\":9}");
        perform("POST", "/api/patrestrictions", "{\"PatNum\":8,\"PatRestrictType\":\"ApptSchedule\"}");
        perform("POST", "/api/popups", "{\"PatNum\":8,\"Description\":\"Review\"}");
        perform("POST", "/api/recalls", "{\"PatNum\":8,\"RecallTypeNum\":9}");
        perform("POST", "/api/vitalsigns", "{\"PatNum\":8}");

        verify(restTemplate, times(15)).exchange(any(URI.class), any(HttpMethod.class),
                any(HttpEntity.class), eq(Object.class));
    }

    @Test
    void documentedDeleteEndpointsAreForwardedButUnsupportedDeleteOperationsAreNot() throws Exception {
        when(restTemplate.exchange(any(URI.class), any(HttpMethod.class), any(HttpEntity.class), eq(Object.class)))
                .thenReturn(ResponseEntity.ok().build());
        for (String path : List.of("/api/diseases/1", "/api/guardians/1", "/api/medicationpats/1",
                "/api/medications/1", "/api/patfielddefs/1", "/api/patfields/1",
                "/api/patplans/1", "/api/patrestrictions/1", "/api/vitalsigns/1")) {
            perform("DELETE", path, null);
        }
        verify(restTemplate, times(9)).exchange(any(URI.class), eq(HttpMethod.DELETE),
                any(HttpEntity.class), eq(Object.class));
    }

    @Test
    void specialRecallEndpointsForwardExactPathsAndBodies() throws Exception {
        when(restTemplate.exchange(any(URI.class), any(HttpMethod.class), any(HttpEntity.class), eq(Object.class)))
                .thenReturn(ResponseEntity.ok().build());
        mockMvc.perform(get("/api/recalls/List").param("IncludeReminded", "true"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/recalls/Status").contentType("application/json")
                        .content("{\"PatNum\":8,\"recallType\":\"Prophy\",\"RecallStatus\":0}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/recalls/SwitchType").contentType("application/json")
                        .content("{\"PatNum\":8}"))
                .andExpect(status().isOk());
        verify(restTemplate).exchange(eq(URI.create("https://od.example/api/v1/recalls/List?IncludeReminded=true")),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(Object.class));
        verify(restTemplate).exchange(eq(URI.create("https://od.example/api/v1/recalls/Status")),
                eq(HttpMethod.PUT), argThat((HttpEntity<?> e) -> {
                    Map<?, ?> body = (Map<?, ?>) e.getBody();
                    return Integer.valueOf(0).equals(body.get("RecallStatus"));
                }), eq(Object.class));
        verify(restTemplate).exchange(eq(URI.create("https://od.example/api/v1/recalls/SwitchType")),
                eq(HttpMethod.PUT), any(HttpEntity.class), eq(Object.class));
    }

    @Test
    void patientFieldUpdateUsesCollectionRouteAndSentinelDatesAndStringBooleansAreUnchanged() throws Exception {
        when(restTemplate.exchange(any(URI.class), any(HttpMethod.class), any(HttpEntity.class), eq(Object.class)))
                .thenReturn(ResponseEntity.ok(Map.of("FieldValue", "false")));

        mockMvc.perform(put("/api/patfields").contentType("application/json")
                        .content("{\"PatNum\":8,\"FieldName\":\"Ins Verified\",\"FieldValue\":\"false\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.FieldValue", is("false")));
        mockMvc.perform(post("/api/medicationpats").contentType("application/json")
                        .content("{\"PatNum\":8,\"DateStart\":\"0001-01-01\",\"DateStop\":\"0001-01-01\",\"ProvNum\":0}"))
                .andExpect(status().isOk());

        verify(restTemplate).exchange(eq(URI.create("https://od.example/api/v1/patfields")),
                eq(HttpMethod.PUT), argThat((HttpEntity<?> e) -> {
                    Map<?, ?> body = (Map<?, ?>) e.getBody();
                    return "false".equals(body.get("FieldValue")) && body.get("PatNum").equals(8);
                }), eq(Object.class));
        verify(restTemplate).exchange(eq(URI.create("https://od.example/api/v1/medicationpats")),
                eq(HttpMethod.POST), argThat((HttpEntity<?> e) -> {
                    Map<?, ?> body = (Map<?, ?>) e.getBody();
                    return Integer.valueOf(0).equals(body.get("ProvNum"))
                            && "0001-01-01".equals(body.get("DateStart"));
                }), eq(Object.class));
    }

    @Test
    void patientFieldCanBeClearedWithoutDroppingItsRequiredValueKey() throws Exception {
        when(restTemplate.exchange(any(URI.class), any(HttpMethod.class), any(HttpEntity.class), eq(Object.class)))
                .thenReturn(ResponseEntity.ok(Map.of("FieldValue", "")));
        mockMvc.perform(put("/api/patfields").contentType("application/json")
                        .content("{\"PatNum\":8,\"FieldName\":\"Verified\",\"FieldValue\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.FieldValue", is("")));
    }

    @Test
    void disallowedDeletesUnsupportedMethodsAndInvalidRequiredInputsDoNotReachUpstream() throws Exception {
        mockMvc.perform(delete("/api/allergydefs/3")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put("/api/diseasedefs/3").contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/rxpats/3")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/recalls/3")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(get("/api/recalls/3")).andExpect(status().isMethodNotAllowed());
        for (String path : List.of("/api/ehrpatients/3", "/api/familymodules/3/Insurance",
                "/api/patientnotes/3", "/api/patientraces", "/api/pharmacies/3",
                "/api/popups/3", "/api/recalltypes/3", "/api/rxpats/3")) {
            mockMvc.perform(delete(path)).andExpect(status().isMethodNotAllowed());
        }
        mockMvc.perform(get("/api/patientraces")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/patientraces").param("PatNum", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/guardians").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/patfielddefs").contentType("application/json")
                        .content("{\"FieldName\":\"Preferred color\",\"FieldType\":\"PickList\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/allergydefs").param("unrelated", "yes")).andExpect(status().isBadRequest());
        verifyNoInteractions(restTemplate);
    }

    @Test
    void returnsUpstreamErrorsAndClearBadGatewayOnConnectivityFailure() throws Exception {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(Object.class)))
                .thenThrow(org.springframework.web.client.HttpClientErrorException.create(
                        org.springframework.http.HttpStatus.FORBIDDEN, "Denied", null,
                        "{\"message\":\"upstream denied\"}".getBytes(), null));
        mockMvc.perform(get("/api/pharmacies"))
                .andExpect(status().isForbidden())
                .andExpect(content().string("{\"message\":\"upstream denied\"}"));

        reset(restTemplate);
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(Object.class)))
                .thenThrow(new ResourceAccessException("connection refused"));
        mockMvc.perform(get("/api/pharmacies"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error", is("Open Dental connectivity failure.")));
    }

    private void perform(String method, String path, String json) throws Exception {
        MockHttpServletRequestBuilder request = request(HttpMethod.valueOf(method), path);
        if (json != null) request.contentType("application/json").content(json);
        mockMvc.perform(request).andExpect(status().is2xxSuccessful());
    }
}