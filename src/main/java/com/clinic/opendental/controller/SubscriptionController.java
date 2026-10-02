package com.clinic.opendental.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.opendental.dto.subscription.SubscriptionRequest;
import com.clinic.opendental.dto.subscription.SubscriptionResponse;
import com.clinic.opendental.service.SubscriptionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Subscriptions", description = "Open Dental webhook subscription management")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping
    @Operation(summary = "List all webhook subscriptions")
    public ResponseEntity<?> getSubscriptions(
            @RequestHeader(value = "Authorization", required = false) String apiKey,
            HttpServletRequest request) {
        log.info("GET /api/subscriptions apiKeyPresent={}, apiKey=[{}]", apiKey != null && !apiKey.isBlank(), apiKey);
        log.info(">>> ALL INCOMING REQUEST HEADERS:");
        java.util.Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            log.info(">>>   {} = {}", name, request.getHeader(name));
        }
        List<SubscriptionResponse> subscriptions = subscriptionService.getSubscriptions(apiKey);
        return ResponseEntity.ok(Map.of(
                "count", subscriptions.size(),
                "subscriptions", subscriptions
        ));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a webhook subscription")
    public ResponseEntity<?> createSubscription(
            @RequestHeader(value = "Authorization", required = false) String apiKey,
            @RequestBody SubscriptionRequest request) {
        log.info("POST /api/subscriptions apiKeyPresent={} request={}",
                apiKey != null && !apiKey.isBlank(), request);
        SubscriptionResponse response = subscriptionService.createSubscription(apiKey, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping(value = "/{subscriptionNum}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update a webhook subscription")
    public ResponseEntity<?> updateSubscription(
            @RequestHeader(value = "Authorization", required = false) String apiKey,
            @PathVariable Long subscriptionNum,
            @RequestBody SubscriptionRequest request) {
        log.info("PUT /api/subscriptions/{} apiKeyPresent={} request={}",
                subscriptionNum, apiKey != null && !apiKey.isBlank(), request);
        SubscriptionResponse response = subscriptionService.updateSubscription(apiKey, subscriptionNum, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{subscriptionNum}")
    @Operation(summary = "Delete a webhook subscription")
    public ResponseEntity<?> deleteSubscription(
            @RequestHeader(value = "Authorization", required = false) String apiKey,
            @PathVariable Long subscriptionNum) {
        log.info("DELETE /api/subscriptions/{} apiKeyPresent={}",
                subscriptionNum, apiKey != null && !apiKey.isBlank());
        subscriptionService.deleteSubscription(apiKey, subscriptionNum);
        return ResponseEntity.ok(Map.of(
                "subscriptionNum", subscriptionNum,
                "deleted", true
        ));
    }
}