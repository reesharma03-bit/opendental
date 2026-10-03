package com.clinic.opendental.controller;

import com.clinic.opendental.security.CurrentUser;
import com.clinic.opendental.service.Impl.AssistantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * AI Assistant for the dashboard.
 *
 * GET  /api/assistant/status   whether the assistant is set up (an API key is configured)
 * POST /api/assistant          {"messages": [{"role": "user"|"assistant", "text": "..."}]}
 *                              -> {"answer": "...", "toolsUsed": [...], "patients": [{"patNum", "name"}]}
 */
@RestController
@RequestMapping("/api/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantService assistantService;

    public record AskRequest(List<AssistantService.Turn> messages) {
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of("configured", assistantService.configured()));
    }

    @PostMapping
    public ResponseEntity<AssistantService.Answer> ask(@RequestBody AskRequest request) {
        return ResponseEntity.ok(assistantService.ask(request.messages(), CurrentUser.permissions()));
    }
}
