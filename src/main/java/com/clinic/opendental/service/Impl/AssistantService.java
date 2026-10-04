package com.clinic.opendental.service.Impl;

import com.clinic.opendental.exception.ApiException;
import com.clinic.opendental.security.Permission;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The dashboard's AI Assistant: answers staff questions about the practice using a
 * model served by Groq (OpenAI-compatible chat completions with tool calling) and a
 * fixed set of read-only tools over our database ({@link AssistantTools}).
 *
 * <p>The assistant cannot change data. Conversations are not stored; the browser sends
 * the visible history with each question. Logs record which tools ran, never the
 * question, the answer or any patient data.</p>
 */
@Service
@Slf4j
public class AssistantService {

    /** One visible chat turn, as the browser holds it. */
    public record Turn(String role, String text) {
    }

    /** A patient the answer drew on, so the UI can link to them. */
    public record PatientRef(long patNum, String name) {
    }

    public record Answer(String answer, List<String> toolsUsed, List<PatientRef> patients) {
    }

    private static final int MAX_TURNS = 20;
    private static final int MAX_TURN_CHARS = 4_000;
    private static final int MAX_TOOL_ROUNDS = 8;
    private static final int MAX_ANSWER_TOKENS = 2_048;
    /** Plain mapper: the request/response format is Groq's, not our API's snake_case. */
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            You are the AI assistant inside SmileOS, a dashboard for a dental practice that uses Open Dental. \
            You help front-desk staff, dentists and the practice owner understand their practice: schedules, \
            patients, allergies, insurance claims, payments and how well the link with Open Dental is working.

            How to work:
            - Use the tools to look things up. Never invent patients, appointments, numbers or dates; if the \
            tools don't return something, say it isn't in the database (it may not have been synced from \
            Open Dental yet).
            - When you only have a patient's name, find them first, then use their PatNum with other tools. If \
            several patients match, list them and ask which one.
            - You can only read data. If someone asks you to book, change or delete something, explain which \
            screen to use (Patients, Appointments, or the API Catalog) instead of claiming you did it.
            - If a tool says the user's role doesn't allow something, tell them it's outside their access.
            - Answer briefly and concretely for busy clinic staff: lead with the answer, then the details. Use \
            short lists or small tables when listing people or appointments. Mention patients as \
            "Name (#PatNum)".
            - Amounts are in the practice's currency; show them as plain numbers with two decimals.
            - This is patient health information: only include what the question needs. Never reveal social \
            security numbers or other identifiers that weren't asked for.
            - Medical questions: you can summarise what is recorded (allergies, problems, medications), but \
            leave clinical judgement to the dentist.
            """;

    private final AssistantTools tools;
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final RestTemplate http;

    @Autowired
    public AssistantService(AssistantTools tools,
                            @Value("${assistant.api-key:}") String apiKey,
                            @Value("${assistant.model:openai/gpt-oss-120b}") String model,
                            @Value("${assistant.base-url:https://api.groq.com/openai/v1}") String baseUrl) {
        this(tools, apiKey, model, baseUrl, defaultClient());
    }

    /** For tests: talk to a stubbed endpoint. */
    AssistantService(AssistantTools tools, String apiKey, String model, String baseUrl, RestTemplate http) {
        this.tools = tools;
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.http = http;
    }

    private static RestTemplate defaultClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(90_000);
        return new RestTemplate(factory);
    }

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public Answer ask(List<Turn> history, Set<Permission> permissions) {
        if (!configured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The AI Assistant is not set up yet. Set GROQ_API_KEY on the backend and restart it.");
        }
        ArrayNode messages = toMessages(history);
        List<Map<String, Object>> toolDefinitions = tools.tools(permissions);
        Set<String> toolsUsed = new LinkedHashSet<>();
        Map<Long, PatientRef> patients = new LinkedHashMap<>();
        long started = System.currentTimeMillis();

        for (int round = 0; round <= MAX_TOOL_ROUNDS; round++) {
            JsonNode choice = complete(messages, toolDefinitions).path("choices").path(0);
            JsonNode message = choice.path("message");
            String finish = choice.path("finish_reason").asText("stop");
            JsonNode calls = message.path("tool_calls");

            if (!calls.isArray() || calls.isEmpty()) {
                String text = message.path("content").asText("").trim();
                if ("length".equals(finish)) {
                    text += "\n\n(The answer was cut short. Ask a narrower question for the rest.)";
                }
                return finish(started, toolsUsed, patients, text.isBlank() ? "I couldn't find an answer to that." : text);
            }

            // Keep the model's turn as returned, then answer every tool call.
            ObjectNode assistantTurn = messages.addObject();
            assistantTurn.put("role", "assistant");
            if (message.hasNonNull("content")) assistantTurn.put("content", message.get("content").asText());
            assistantTurn.set("tool_calls", calls);
            for (JsonNode call : calls) {
                String name = call.path("function").path("name").asText();
                ObjectNode result = messages.addObject();
                result.put("role", "tool");
                result.put("tool_call_id", call.path("id").asText());
                result.put("name", name);
                result.put("content", runTool(name, call.path("function").path("arguments").asText("{}"),
                        permissions, toolsUsed, patients));
            }
        }
        return finish(started, toolsUsed, patients,
                "That needed more lookups than I'm allowed in one answer. Please ask a narrower question.");
    }

    /** One chat-completions call to Groq. */
    private JsonNode complete(ArrayNode messages, List<Map<String, Object>> toolDefinitions) {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", model);
        body.set("messages", messages);
        if (!toolDefinitions.isEmpty()) {
            body.set("tools", JSON.valueToTree(toolDefinitions));
            body.put("tool_choice", "auto");
        }
        body.put("temperature", 0.2);
        body.put("max_tokens", MAX_ANSWER_TOKENS);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        try {
            String response = http.postForObject(baseUrl + "/chat/completions",
                    new HttpEntity<>(body.toString(), headers), String.class);
            return JSON.readTree(response == null ? "{}" : response);
        } catch (HttpStatusCodeException e) {
            int status = e.getStatusCode().value();
            log.warn("AI Assistant request to Groq failed with status {}", status);
            if (status == 401 || status == 403) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                        "The AI service rejected the API key. Check GROQ_API_KEY on the backend.");
            }
            if (status == 429) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "The AI service is busy. Please try again in a minute.");
            }
            if (status == 400 && e.getResponseBodyAsString().contains("tool_use_failed")) {
                throw new ApiException(HttpStatus.BAD_GATEWAY,
                        "The AI model couldn't work out how to look that up. Try rephrasing the question.");
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI service returned an error (" + status + ").");
        } catch (ResourceAccessException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not reach the AI service. Check the backend's internet connection.");
        } catch (JsonProcessingException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI service sent a response that couldn't be read.");
        }
    }

    private String runTool(String name, String arguments, Set<Permission> permissions,
                           Set<String> toolsUsed, Map<Long, PatientRef> patients) {
        toolsUsed.add(name);
        try {
            JsonNode input = arguments == null || arguments.isBlank() ? JSON.createObjectNode() : JSON.readTree(arguments);
            String content = tools.run(name, input, permissions);
            collectPatients(content, patients);
            return content;
        } catch (Exception e) {
            log.warn("AI Assistant tool {} failed: {}", name, e.getClass().getSimpleName());
            return "Error: " + e.getMessage();
        }
    }

    /** Patients mentioned in tool results (pat_num + name), for links under the answer. */
    private static void collectPatients(String json, Map<Long, PatientRef> patients) {
        try {
            collect(JSON.readTree(json), patients);
        } catch (JsonProcessingException ignored) {
            // truncated or non-JSON result: no links
        }
    }

    private static void collect(JsonNode node, Map<Long, PatientRef> patients) {
        if (patients.size() >= 10 || node == null) return;
        if (node.isArray()) {
            node.forEach(child -> collect(child, patients));
        } else if (node.isObject()) {
            JsonNode patNum = node.has("pat_num") ? node.get("pat_num") : node.get("PatNum");
            JsonNode name = node.has("patient") ? node.get("patient") : node.get("name");
            if (patNum != null && patNum.canConvertToLong() && patNum.asLong() != 0
                    && name != null && name.isTextual() && !name.asText().isBlank()) {
                patients.putIfAbsent(patNum.asLong(), new PatientRef(patNum.asLong(), name.asText()));
            }
            node.fields().forEachRemaining(entry -> {
                if (entry.getValue().isContainerNode()) collect(entry.getValue(), patients);
            });
        }
    }

    private static ArrayNode toMessages(List<Turn> history) {
        if (history == null || history.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Ask a question first.");
        }
        List<Turn> recent = history.size() > MAX_TURNS ? history.subList(history.size() - MAX_TURNS, history.size()) : history;
        ArrayNode messages = JSON.createArrayNode();
        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content", SYSTEM_PROMPT + "\nToday is "
                + LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, yyyy-MM-dd", Locale.ENGLISH)) + ".");
        String lastRole = null;
        for (Turn turn : recent) {
            String text = turn.text() == null ? "" : turn.text().trim();
            if (text.isEmpty()) continue;
            if (text.length() > MAX_TURN_CHARS) text = text.substring(0, MAX_TURN_CHARS);
            String role = "assistant".equalsIgnoreCase(turn.role()) ? "assistant" : "user";
            // The conversation must start with the user.
            if (lastRole == null && role.equals("assistant")) continue;
            ObjectNode message = messages.addObject();
            message.put("role", role);
            message.put("content", text);
            lastRole = role;
        }
        if (!"user".equals(lastRole)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, lastRole == null
                    ? "Ask a question first." : "The last message must be a question from the user.");
        }
        return messages;
    }

    private Answer finish(long started, Set<String> toolsUsed, Map<Long, PatientRef> patients, String text) {
        // No question, answer or patient data in the logs: only what ran and how long it took.
        log.info("AI Assistant answered in {} ms using tools {}", System.currentTimeMillis() - started, toolsUsed);
        return new Answer(text, List.copyOf(toolsUsed), List.copyOf(patients.values()));
    }
}
