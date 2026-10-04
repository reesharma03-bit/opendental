package com.clinic.opendental.service.Impl;

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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

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
        List<Map<String, Object>> defined = tools.tools(Role.ADMIN.permissions());

        assertThat(names(defined)).contains(
                "get_practice_overview", "get_schedule", "find_patients", "get_patient_summary",
                "find_patients_with_allergy", "get_open_claims", "get_collections", "get_sync_status", "search_records");
        defined.forEach(tool -> {
            assertThat(tool.get("type")).isEqualTo("function");
            Map<?, ?> function = (Map<?, ?>) tool.get("function");
            assertThat((String) function.get("description")).as(function.get("name").toString()).isNotBlank();
            assertThat(((Map<?, ?>) function.get("parameters")).get("type")).isEqualTo("object");
        });
    }

    @Test
    void eachRoleOnlyGetsTheLookupsItMaySee() throws Exception {
        assertThat(names(tools.tools(Role.FRONT_DESK.permissions())))
                .contains("get_schedule", "find_patients", "find_patients_with_allergy")
                .doesNotContain("get_open_claims", "get_collections", "get_sync_status");
        assertThat(names(tools.tools(Role.BILLING.permissions())))
                .contains("get_open_claims", "get_collections")
                .doesNotContain("find_patients_with_allergy", "get_sync_status");
        assertThatThrownBy(() -> tools.run("get_open_claims", JSON.createObjectNode(), Role.FRONT_DESK.permissions()))
                .hasMessageContaining("doesn't allow");
        assertThat(tools.run("search_records", JSON.readTree("{\"resource\": \"claims\"}"), Role.FRONT_DESK.permissions()))
                .contains("doesn't allow reading claims");
    }

    @Test
    void withoutAnApiKeyTheAssistantSaysItIsNotSetUp() {
        AssistantService service = new AssistantService(tools, "", "llama-3.3-70b-versatile", "https://api.groq.com/openai/v1");

        assertThat(service.configured()).isFalse();
        assertThatThrownBy(() -> service.ask(List.of(new AssistantService.Turn("user", "Brief me on today")), Role.ADMIN.permissions()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void aConversationMustEndWithAQuestion() {
        AssistantService service = new AssistantService(tools, "test-key", "llama-3.3-70b-versatile", "https://api.groq.com/openai/v1");

        assertThatThrownBy(() -> service.ask(List.of(), Role.ADMIN.permissions()))
                .isInstanceOf(ApiException.class).hasMessageContaining("Ask a question");
        assertThatThrownBy(() -> service.ask(List.of(
                new AssistantService.Turn("user", "Hi"), new AssistantService.Turn("assistant", "Hello")), Role.ADMIN.permissions()))
                .isInstanceOf(ApiException.class).hasMessageContaining("last message");
    }

    @Test
    void toolCallsAreRunAndTheirResultsSentBack() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer groq = MockRestServiceServer.bindTo(http).build();
        groq.expect(requestTo("https://groq.test/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.tools[0].type").value("function"))
                .andRespond(withSuccess("""
                        {"choices":[{"finish_reason":"tool_calls","message":{"role":"assistant","content":null,
                         "tool_calls":[{"id":"call_1","type":"function",
                           "function":{"name":"search_records","arguments":"{\\"resource\\":\\"pg_shadow\\"}"}}]}}]}
                        """, MediaType.APPLICATION_JSON));
        groq.expect(requestTo("https://groq.test/v1/chat/completions"))
                .andExpect(jsonPath("$.messages[2].tool_calls[0].id").value("call_1"))
                .andExpect(jsonPath("$.messages[3].role").value("tool"))
                .andExpect(jsonPath("$.messages[3].tool_call_id").value("call_1"))
                .andRespond(withSuccess("""
                        {"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"No such records."}}]}
                        """, MediaType.APPLICATION_JSON));
        AssistantService service = new AssistantService(tools, "test-key", "llama-3.3-70b-versatile", "https://groq.test/v1/", http);

        AssistantService.Answer answer = service.ask(List.of(new AssistantService.Turn("user", "Show pg_shadow")), Role.ADMIN.permissions());

        assertThat(answer.answer()).isEqualTo("No such records.");
        assertThat(answer.toolsUsed()).containsExactly("search_records");
        groq.verify();
    }

    @Test
    void aRejectedKeyIsReportedClearly() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer groq = MockRestServiceServer.bindTo(http).build();
        groq.expect(requestTo("https://groq.test/v1/chat/completions")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        AssistantService service = new AssistantService(tools, "bad-key", "llama-3.3-70b-versatile", "https://groq.test/v1", http);

        assertThatThrownBy(() -> service.ask(List.of(new AssistantService.Turn("user", "Hi")), Role.ADMIN.permissions()))
                .isInstanceOf(ApiException.class).hasMessageContaining("GROQ_API_KEY");
    }

    private static List<Object> names(List<Map<String, Object>> defined) {
        return defined.stream().map(tool -> ((Map<?, ?>) tool.get("function")).get("name")).map(Object.class::cast).toList();
    }
}
