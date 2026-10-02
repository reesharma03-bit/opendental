package com.clinic.opendental.controller;

import com.clinic.opendental.service.DiseaseDefinitionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DiseaseDefsController.class)
class DiseaseDefsControllerTest {
    @Autowired private MockMvc mvc;
    @MockBean private DiseaseDefinitionService service;

    @Test
    void listsDefinitionsWithPaginationAndCodes() throws Exception {
        when(service.getDiseaseDefinitions(any())).thenReturn(List.of(
                Map.of("DiseaseDefNum", 59, "DiseaseName", "Unspecified Essential Hypertension",
                        "IsHidden", "false", "ICD9Code", "401.9", "ICD10Code", "", "SnomedCode", "")));
        mvc.perform(get("/api/diseasedefs").param("Limit", "100").param("Offset", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].DiseaseDefNum").value(59))
                .andExpect(jsonPath("$[0].ICD9Code").value("401.9"));
        verify(service).getDiseaseDefinitions(Map.of("Limit", "100", "Offset", "200"));
    }

    @Test
    void readsDefinitionById() throws Exception {
        when(service.getDiseaseDefinition(58L)).thenReturn(
                Map.of("DiseaseDefNum", 58, "DiseaseName", "Severe Back Pain", "IsHidden", "true"));
        mvc.perform(get("/api/diseasedefs/58"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.DiseaseDefNum").value(58))
                .andExpect(jsonPath("$.IsHidden").value("true"));
    }

    @Test
    void createsWithCanonicalNameAndNoResponseBody() throws Exception {
        mvc.perform(post("/api/diseasedefs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disease_name\":\" Shingles \",\"IsHidden\":\"true\"}"))
                .andExpect(status().isCreated())
                .andExpect(content().string(""));
        verify(service).createDiseaseDefinition(Map.of("DiseaseName", "Shingles"));
    }

    @Test
    void rejectsMissingBlankAndNonStringNames() throws Exception {
        for (String body : List.of("{}", "{\"DiseaseName\":\"  \"}", "{\"DiseaseName\":123}")) {
            mvc.perform(post("/api/diseasedefs").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verify(service, never()).createDiseaseDefinition(any());
    }

    @Test
    void preservesDuplicateAndNotFoundErrors() throws Exception {
        doThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", null,
                "A DiseaseDef with that name already exists".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8)).when(service).createDiseaseDefinition(any());
        mvc.perform(post("/api/diseasedefs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"DiseaseName\":\"Shingles\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("A DiseaseDef with that name already exists"));
        when(service.getDiseaseDefinition(999L)).thenThrow(
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null,
                        "Disease definition not found".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
        mvc.perform(get("/api/diseasedefs/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Disease definition not found"));
    }

    @Test
    void rejectsInvalidIdWithoutCallingUpstream() throws Exception {
        mvc.perform(get("/api/diseasedefs/0")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}