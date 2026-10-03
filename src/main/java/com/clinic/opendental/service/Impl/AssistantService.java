package com.clinic.opendental.service.Impl;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.clinic.opendental.exception.ApiException;
import com.clinic.opendental.security.Permission;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The dashboard's AI Assistant: answers staff questions about the practice using Claude
 * and a fixed set of read-only tools over our database ({@link AssistantTools}).
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
    private volatile AnthropicClient client;

    public AssistantService(AssistantTools tools,
                            @Value("${assistant.api-key:}") String apiKey,
                            @Value("${assistant.model:claude-opus-5-5}") String model) {
        this.tools = tools;
        this.apiKey = apiKey;
        this.model = model;
    }

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public Answer ask(List<Turn> history, Set<Permission> permissions) {
        if (!configured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The AI Assistant is not set up yet. Set ANTHROPIC_API_KEY on the backend and restart it.");
        }
        List<MessageParam> messages = toMessages(history);
        Set<String> toolsUsed = new LinkedHashSet<>();
        Map<Long, PatientRef> patients = new LinkedHashMap<>();
        long started = System.currentTimeMillis();

        try {
            for (int round = 0; round <= MAX_TOOL_ROUNDS; round++) {
                Message response = client().messages().create(params(messages, permissions));
                StopReason stop = response.stopReason().orElse(StopReason.END_TURN);

                if (StopReason.REFUSAL.equals(stop)) {
                    return finish(started, toolsUsed, patients,
                            "I can't help with that request. Try asking about the schedule, patients, claims or payments.");
                }
                if (!StopReason.TOOL_USE.equals(stop)) {
                    String text = text(response);
                    if (StopReason.MAX_TOKENS.equals(stop)) {
                        text += "\n\n(The answer was cut short. Ask a narrower question for the rest.)";
                    }
                    return finish(started, toolsUsed, patients, text.isBlank() ? "I couldn't find an answer to that." : text);
                }

                // Keep the model's turn exactly as returned, then answer every tool call in one message.
                messages.add(response.toParam());
                List<ContentBlockParam> results = new ArrayList<>();
                for (ContentBlock block : response.content()) {
                    block.toolUse().ifPresent(call -> results.add(runTool(call, permissions, toolsUsed, patients)));
                }
                messages.add(MessageParam.builder()
                        .role(MessageParam.Role.USER)
                        .contentOfBlockParams(results)
                        .build());
            }
            return finish(started, toolsUsed, patients,
                    "That needed more lookups than I'm allowed in one answer. Please ask a narrower question.");
        } catch (UnauthorizedException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The AI service rejected the API key. Check ANTHROPIC_API_KEY on the backend.");
        } catch (RateLimitException e) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "The AI service is busy. Please try again in a minute.");
        } catch (AnthropicServiceException e) {
            log.warn("AI Assistant request failed with status {}", e.statusCode());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI service returned an error (" + e.statusCode() + ").");
        } catch (AnthropicIoException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not reach the AI service. Check the backend's internet connection.");
        }
    }

    private MessageCreateParams params(List<MessageParam> messages, Set<Permission> permissions) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .system(SYSTEM_PROMPT + "\nToday is " + LocalDate.now().format(
                        DateTimeFormatter.ofPattern("EEEE, yyyy-MM-dd", Locale.ENGLISH)) + ".")
                // Quick, focused lookups: medium effort keeps answers fast without losing accuracy.
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).build())
                // If a safety classifier declines, the API retries on a fallback model instead of stopping.
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .messages(messages);
        tools.tools(permissions).forEach(builder::addTool);
        return builder.build();
    }

    private ContentBlockParam runTool(ToolUseBlock call, Set<Permission> permissions, Set<String> toolsUsed,
                                      Map<Long, PatientRef> patients) {
        toolsUsed.add(call.name());
        String content;
        boolean failed = false;
        try {
            JsonNode input = call._input().convert(JsonNode.class);
            content = tools.run(call.name(), input, permissions);
            collectPatients(content, patients);
        } catch (Exception e) {
            failed = true;
            content = "Error: " + e.getMessage();
            log.warn("AI Assistant tool {} failed: {}", call.name(), e.getClass().getSimpleName());
        }
        return ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                .toolUseId(call.id())
                .content(content)
                .isError(failed)
                .build());
    }

    /** Patients mentioned in tool results (pat_num + name), for links under the answer. */
    private static void collectPatients(String json, Map<Long, PatientRef> patients) {
        try {
            JsonNode node = JSON.readTree(json);
            collect(node, patients);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
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

    private static List<MessageParam> toMessages(List<Turn> history) {
        if (history == null || history.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Ask a question first.");
        }
        List<Turn> recent = history.size() > MAX_TURNS ? history.subList(history.size() - MAX_TURNS, history.size()) : history;
        List<MessageParam> messages = new ArrayList<>();
        for (Turn turn : recent) {
            String text = turn.text() == null ? "" : turn.text().trim();
            if (text.isEmpty()) continue;
            if (text.length() > MAX_TURN_CHARS) text = text.substring(0, MAX_TURN_CHARS);
            boolean user = !"assistant".equalsIgnoreCase(turn.role());
            // The conversation must start with the user.
            if (messages.isEmpty() && !user) continue;
            messages.add(MessageParam.builder()
                    .role(user ? MessageParam.Role.USER : MessageParam.Role.ASSISTANT)
                    .content(text)
                    .build());
        }
        if (messages.isEmpty() || !MessageParam.Role.USER.equals(messages.get(messages.size() - 1).role())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The last message must be a question from the user.");
        }
        return messages;
    }

    private static String text(Message response) {
        StringBuilder out = new StringBuilder();
        for (ContentBlock block : response.content()) {
            block.text().ifPresent(t -> out.append(t.text()));
        }
        return out.toString().trim();
    }

    private Answer finish(long started, Set<String> toolsUsed, Map<Long, PatientRef> patients, String text) {
        // No question, answer or patient data in the logs: only what ran and how long it took.
        log.info("AI Assistant answered in {} ms using tools {}", System.currentTimeMillis() - started, toolsUsed);
        return new Answer(text, List.copyOf(toolsUsed), List.copyOf(patients.values()));
    }

    private AnthropicClient client() {
        AnthropicClient current = client;
        if (current == null) {
            synchronized (this) {
                if (client == null) {
                    client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
                }
                current = client;
            }
        }
        return current;
    }
}
