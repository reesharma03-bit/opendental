package com.clinic.opendental.service.Impl;

import com.anthropic.models.messages.Tool;
import com.clinic.opendental.exception.ApiException;
import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.repository.ClinicRepository;
import com.clinic.opendental.security.Permission;
import com.clinic.opendental.security.PermissionResolver;
import com.clinic.opendental.security.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** The AI Assistant's guard rails: read-only tools, no sensitive identifiers, clear failures. */
class AssistantTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private AssistantTools tools;

    @BeforeEach
    void setUp() {
        ClinicRepository clinics = mock(ClinicRepository.class);
        when(clinics.findByIsActiveTrue()).thenReturn(List.of(Clinic.builder().id(UUID.randomUUID()).build()));
        tools = new AssistantTools(mock(JdbcTemplate.class), clinics, mock(DashboardService.class), mock(ResourceRecordStore.class),
                new PermissionResolver());
    }

    @Test
    void sensitiveIdentifiersNeverReachTheModel() throws Exception {
        JsonNode record = JSON.readTree("""
                {"PatNum": 48, "LName": "Doe", "SSN": "123-45-6789",
                 "subscriber": {"SubscriberSSN": "987", "CustomerKey": "abc"},
                 "rows": [{"ssn": "1"}, {"name": "ok"}]}
                """);

        String scrubbed = AssistantTools.scrub(record).toString();

        assertThat(scrubbed).doesNotContainIgnoringCase("ssn").doesNotContain("123-45-6789", "987", "CustomerKey");
        assertThat(scrubbed).contains("\"LName\":\"Doe\"", "\"name\":\"ok\"");
    }

    @Test
    void onlyKnownToolsAndRecordTypesCanBeUsed() throws Exception {
        assertThatThrownBy(() -> tools.run("drop_table", JSON.createObjectNode(), Role.ADMIN.permissions()))
                .hasMessageContaining("Unknown tool");
        assertThat(tools.run("search_records", JSON.readTree("{\"resource\": \"pg_shadow\"}"), Role.ADMIN.permissions()))
                .contains("Unknown record type");
    }

    @Test
    void everyToolHasADescriptionAndSchema() {
        List<Tool> defined = tools.tools(Role.ADMIN.permissions());

        assertThat(defined).extracting(Tool::name).contains(
                "get_practice_overview", "get_schedule", "find_patients", "get_patient_summary",
                "find_patients_with_allergy", "get_open_claims", "get_collections", "get_sync_status", "search_records");
        defined.forEach(tool -> assertThat(tool.description()).as(tool.name()).isPresent());
    }

    @Test
    void eachRoleOnlyGetsTheLookupsItMaySee() throws Exception {
        assertThat(tools.tools(Role.FRONT_DESK.permissions())).extracting(Tool::name)
                .contains("get_schedule", "find_patients", "find_patients_with_allergy")
                .doesNotContain("get_open_claims", "get_collections", "get_sync_status");
        assertThat(tools.tools(Role.BILLING.permissions())).extracting(Tool::name)
                .contains("get_open_claims", "get_collections")
                .doesNotContain("find_patients_with_allergy", "get_sync_status");
        assertThatThrownBy(() -> tools.run("get_open_claims", JSON.createObjectNode(), Role.FRONT_DESK.permissions()))
                .hasMessageContaining("doesn't allow");
        assertThat(tools.run("search_records", JSON.readTree("{\"resource\": \"claims\"}"), Role.FRONT_DESK.permissions()))
                .contains("doesn't allow reading claims");
    }

    @Test
    void withoutAnApiKeyTheAssistantSaysItIsNotSetUp() {
        AssistantService service = new AssistantService(tools, "", "claude-opus-5-5");

        assertThat(service.configured()).isFalse();
        assertThatThrownBy(() -> service.ask(List.of(new AssistantService.Turn("user", "Brief me on today")), Role.ADMIN.permissions()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void aConversationMustEndWithAQuestion() {
        AssistantService service = new AssistantService(tools, "test-key", "claude-opus-5-5");

        assertThatThrownBy(() -> service.ask(List.of(), Role.ADMIN.permissions()))
                .isInstanceOf(ApiException.class).hasMessageContaining("Ask a question");
        assertThatThrownBy(() -> service.ask(List.of(
                new AssistantService.Turn("user", "Hi"), new AssistantService.Turn("assistant", "Hello")), Role.ADMIN.permissions()))
                .isInstanceOf(ApiException.class).hasMessageContaining("last message");
    }
}
