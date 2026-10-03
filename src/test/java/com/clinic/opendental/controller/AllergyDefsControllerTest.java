package com.clinic.opendental.controller;

import com.clinic.opendental.service.AllergyDefinitionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AllergyDefsController.class)
// Controller behaviour only; who may call what is covered by SecurityRulesTest.
@AutoConfigureMockMvc(addFilters = false)
class AllergyDefsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AllergyDefinitionService allergyDefinitionService;

    @Test
    void listsDefinitionsAndForwardsOffset() throws Exception {
        when(allergyDefinitionService.getAllergyDefinitions(any())).thenReturn(List.of(
                Map.of("AllergyDefNum", 44, "Description ", "Allergy - Latex", "IsHidden", "false")));

        mockMvc.perform(get("/api/allergydefs")
                        .param("Limit", "100")
                        .param("Offset", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].AllergyDefNum", is(44)))
                .andExpect(jsonPath("$[0].IsHidden", is("false")));

        verify(allergyDefinitionService).getAllergyDefinitions(Map.of(
                "Limit", "100",
                "Offset", "200"));
    }

    @Test
    void readsOneDefinitionById() throws Exception {
        when(allergyDefinitionService.getAllergyDefinition(14L)).thenReturn(
                Map.of("AllergyDefNum", 14, "Description ", "Latex", "IsHidden", "false"));

        mockMvc.perform(get("/api/allergydefs/14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.AllergyDefNum", is(14)))
                .andExpect(jsonPath("$['Description ']", is("Latex")));

        verify(allergyDefinitionService).getAllergyDefinition(14L);
    }

    @Test
    void createsDefinitionAndReturnsLocation() throws Exception {
        when(allergyDefinitionService.createAllergyDefinition(any())).thenReturn(
                Map.of("AllergyDefNum", 84, "Description ", "Tylenol"));

        mockMvc.perform(post("/api/allergydefs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("Description", " Tylenol "))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/allergydefs/84"))
                .andExpect(jsonPath("$.AllergyDefNum", is(84)));

        verify(allergyDefinitionService).createAllergyDefinition(Map.of("Description", "Tylenol"));
    }

    @Test
    void rejectsDefinitionWithoutDescription() throws Exception {
        mockMvc.perform(post("/api/allergydefs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("Description", "  "))))
                .andExpect(status().isBadRequest());

        verify(allergyDefinitionService, never()).createAllergyDefinition(any());
    }

    @Test
    void updatesDescriptionAndHiddenStatus() throws Exception {
        when(allergyDefinitionService.updateAllergyDefinition(eq(84L), any())).thenReturn(
                Map.of("AllergyDefNum", 84, "Description ", "Tylenol", "IsHidden", "true"));

        mockMvc.perform(put("/api/allergydefs/84")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "Description", "Tylenol",
                                "IsHidden", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.IsHidden", is("true")));

        verify(allergyDefinitionService).updateAllergyDefinition(84L, Map.of(
                "Description", "Tylenol",
                "IsHidden", "true"));
    }

    @Test
    void rejectsUnsupportedAndInvalidUpdates() throws Exception {
        mockMvc.perform(put("/api/allergydefs/84")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("MedicationNum", 3))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/allergydefs/84")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("IsHidden", "sometimes"))))
                .andExpect(status().isBadRequest());

        verify(allergyDefinitionService, never()).updateAllergyDefinition(eq(84L), any());
    }
}