package com.clinic.opendental.controller;

import com.clinic.opendental.service.AllergyService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AllergiesController.class)
// Controller behaviour only; who may call what is covered by SecurityRulesTest.
@AutoConfigureMockMvc(addFilters = false)
class AllergiesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AllergyService allergyService;

    @Test
    void listsAllergiesAndForwardsThePatientFilter() throws Exception {
        when(allergyService.getAllergies(any())).thenReturn(List.of(
                Map.of("AllergyNum", 4, "PatNum", 85, "defDescription", "Latex")));

        mockMvc.perform(get("/api/allergies").param("PatNum", "85"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].AllergyNum", is(4)))
                .andExpect(jsonPath("$[0].PatNum", is(85)))
                .andExpect(jsonPath("$[0].defDescription", is("Latex")));

        verify(allergyService).getAllergies(Map.of("PatNum", "85"));
    }

    @Test
    void getsAnAllergyById() throws Exception {
        when(allergyService.getAllergy(4L)).thenReturn(
                Map.of("AllergyNum", 4, "PatNum", 85, "defDescription", "Latex"));

        mockMvc.perform(get("/api/allergies/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.AllergyNum", is(4)))
                .andExpect(jsonPath("$.defDescription", is("Latex")));

        verify(allergyService).getAllergy(4L);
    }

    @Test
    void createsAllergyAndReturnsLocation() throws Exception {
        when(allergyService.createAllergy(any())).thenReturn(
                Map.of("AllergyNum", 14, "PatNum", 85, "defDescription", "Latex"));

        mockMvc.perform(post("/api/allergies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "PatNum", 85,
                                "defDescription", "Latex",
                                "Reaction", "Hives"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/allergies/14"))
                .andExpect(jsonPath("$.AllergyNum", is(14)));

        verify(allergyService).createAllergy(Map.of(
                "PatNum", 85,
                "defDescription", "Latex",
                "Reaction", "Hives"));
    }

    @Test
    void rejectsCreateWithoutPatientOrAllergyDefinition() throws Exception {
        mockMvc.perform(post("/api/allergies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("Reaction", "Hives"))))
                .andExpect(status().isBadRequest());

        verify(allergyService, never()).createAllergy(any());
    }

    @Test
    void updatesSupportedFieldsUsingOpenDentalNames() throws Exception {
        Map<String, Object> updated = Map.of("AllergyNum", 14, "StatusIsActive", "false");
        when(allergyService.updateAllergy(eq(14L), any())).thenReturn(updated);

        mockMvc.perform(put("/api/allergies/14")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "Reaction", "Hives",
                                "DateAdverseReaction", "2024-10-02",
                                "StatusIsActive", "false"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.StatusIsActive", is("false")));

        verify(allergyService).updateAllergy(14L, Map.of(
                "Reaction", "Hives",
                "DateAdverseReaction", "2024-10-02",
                "StatusIsActive", "false"));
    }

    @Test
    void rejectsUnsupportedOrInvalidUpdates() throws Exception {
        mockMvc.perform(put("/api/allergies/14")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("PatNum", 85))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/allergies/14")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("StatusIsActive", "sometimes"))))
                .andExpect(status().isBadRequest());

        verify(allergyService, never()).updateAllergy(eq(14L), any());
    }

    @Test
    void deletesAllergy() throws Exception {
        mockMvc.perform(delete("/api/allergies/14"))
                .andExpect(status().isOk());

        verify(allergyService).deleteAllergy(14L);
    }
}