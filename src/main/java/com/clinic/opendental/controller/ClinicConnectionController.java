package com.clinic.opendental.controller;

import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.security.AuditService;
import com.clinic.opendental.service.Impl.ClinicConnectionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The "Open Dental" page: connect the practice with its Customer Key, change the key,
 * set up webhook subscriptions, disconnect. Needs the Sync permission (/api/sync/**).
 * The key is never sent back, only its last four characters.
 */
@RestController
@RequestMapping("/api/sync/connection")
@RequiredArgsConstructor
public class ClinicConnectionController {

    private final ClinicConnectionService connections;
    private final AuditService audit;

    /** GET: what is connected, how its webhooks stand, and what the server is set up with. */
    @GetMapping
    public ResponseEntity<Map<String, Object>> get() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("developerKeySet", connections.hasDeveloperKey());
        body.put("remoteUrl", connections.remoteUrl());
        body.put("publicUrl", connections.publicUrl().isEmpty() ? null : connections.publicUrl());
        Clinic clinic = connections.current();
        if (clinic == null) {
            body.put("clinic", null);
            body.put("webhooks", java.util.List.of());
        } else {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("clinicCode", clinic.getClinicCode());
            c.put("clinicName", clinic.getClinicName());
            c.put("baseUrl", clinic.getBaseUrl());
            c.put("key", ClinicConnectionService.maskedKey(clinic.getApiKey()));
            c.put("workstation", connections.workstation(clinic));
            body.put("clinic", c);
            body.put("webhooks", connections.webhookStatus(clinic));
        }
        return ResponseEntity.ok(body);
    }

    /** POST /test {customerKey, baseUrl?}: tries the key without saving anything. */
    @PostMapping("/test")
    public ResponseEntity<ClinicConnectionService.Test> test(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(connections.test(text(body, "customerKey"), text(body, "baseUrl")));
    }

    /** POST {clinicName, customerKey, baseUrl?, workstation?}: connects the practice. */
    @PostMapping
    public ResponseEntity<Map<String, Object>> connect(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Map<String, Object> result = connections.connect(text(body, "clinicName"), text(body, "customerKey"),
                text(body, "baseUrl"), text(body, "workstation"));
        audit.record("OPENDENTAL_CONNECTED", "Practice " + text(body, "clinicName"), request);
        return ResponseEntity.ok(result);
    }

    /** PUT /key {customerKey, baseUrl?}: a new key for the connected practice. */
    @PutMapping("/key")
    public ResponseEntity<Map<String, Object>> changeKey(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Map<String, Object> result = connections.changeKey(text(body, "customerKey"), text(body, "baseUrl"));
        audit.record("OPENDENTAL_KEY_CHANGED", null, request);
        return ResponseEntity.ok(result);
    }

    /** PUT /webhooks {workstation}: saves the office computer and sets up the subscriptions. */
    @PutMapping("/webhooks")
    public ResponseEntity<Map<String, Object>> webhooks(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        var result = connections.saveWorkstationAndSetUp(text(body, "workstation"));
        audit.record("OPENDENTAL_WEBHOOKS_SET_UP", "Workstation " + text(body, "workstation"), request);
        return ResponseEntity.ok(Map.of("webhooks", result));
    }

    /** POST /webhooks: checks and repairs the subscriptions now. */
    @PostMapping("/webhooks")
    public ResponseEntity<Map<String, Object>> repairWebhooks(HttpServletRequest request) {
        Clinic clinic = connections.current();
        if (clinic == null) return ResponseEntity.status(404).body(Map.of("error", "Open Dental isn't connected yet."));
        var result = connections.setUpWebhooks(clinic);
        audit.record("OPENDENTAL_WEBHOOKS_SET_UP", null, request);
        return ResponseEntity.ok(Map.of("webhooks", result));
    }

    /** DELETE: removes SmileOS's webhooks in Open Dental and stops syncing. Data already copied stays. */
    @DeleteMapping
    public ResponseEntity<Void> disconnect(HttpServletRequest request) {
        connections.disconnect();
        audit.record("OPENDENTAL_DISCONNECTED", null, request);
        return ResponseEntity.noContent().build();
    }

    private static String text(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }
}
