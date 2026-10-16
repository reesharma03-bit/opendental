package com.clinic.opendental.controller;

import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.security.AuditService;
import com.clinic.opendental.security.CurrentUser;
import com.clinic.opendental.security.Role;
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
import java.util.List;
import java.util.Map;

/**
 * The "Open Dental" page (Admins and Dentists). An Admin sees every clinic, registers clinics for
 * any dentist and manages whichever clinic is chosen in the clinic switcher; a Dentist registers
 * and manages only the clinics they own. The key is never sent back, only its last four characters.
 */
@RestController
@RequestMapping("/api/sync/connection")
@RequiredArgsConstructor
public class ClinicConnectionController {

    private final ClinicConnectionService connections;
    private final AuditService audit;

    /**
     * GET: the current clinic (connected or not), how its webhooks stood at the last check, and
     * whether the user may register a clinic. From SmileOS's database only, so it opens instantly.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> get() {
        Map<String, Object> body = new LinkedHashMap<>();
        boolean admin = CurrentUser.role() == Role.ADMIN;
        body.put("developerKeySet", connections.hasDeveloperKey());
        body.put("remoteUrl", connections.remoteUrl());
        body.put("publicUrl", connections.publicUrl().isEmpty() ? null : connections.publicUrl());
        body.put("admin", admin);
        // Admins register clinics for any dentist; a dentist registers their own (any number).
        body.put("canRegister", admin || CurrentUser.role() == Role.DENTIST);
        Clinic clinic = connections.current();
        if (clinic == null) {
            body.put("clinic", null);
            body.put("webhooks", List.of());
            body.put("settingUp", false);
            body.put("checkedAt", null);
        } else {
            body.put("clinic", connections.describe(clinic));
            body.put("webhooks", Boolean.TRUE.equals(clinic.getIsActive()) ? connections.lastStatus(clinic) : List.of());
            body.put("settingUp", connections.isSettingUp(clinic));
            var checked = connections.lastChecked(clinic);
            body.put("checkedAt", checked == null ? null : checked.toInstant().toString());
        }
        return ResponseEntity.ok(body);
    }

    /** GET /clinics: every clinic for an Admin (with patients, users, last sync), the user's own otherwise. */
    @GetMapping("/clinics")
    public List<Map<String, Object>> clinics() {
        return connections.visibleClinics();
    }

    /**
     * GET /dentist?email=: (Admins) whether that email belongs to a dentist, before registering a
     * clinic for them; {"exists": false} when there is no such user yet (create their account first).
     */
    @GetMapping("/dentist")
    public Map<String, Object> dentist(@org.springframework.web.bind.annotation.RequestParam String email) {
        return connections.findDentist(email);
    }

    /** POST /test {customerKey, baseUrl}: tries the key without saving anything. */
    @PostMapping("/test")
    public ResponseEntity<ClinicConnectionService.Test> test(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(connections.test(text(body, "customerKey"), text(body, "baseUrl")));
    }

    /**
     * POST {clinicName, customerKey, baseUrl, workstation}: registers a new clinic; webhooks and its
     * first sync follow in the background. A Dentist's account is tied to it, so their session is
     * renewed to carry the clinic.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Map<String, Object> result = connections.register(text(body, "clinicName"), text(body, "customerKey"),
                text(body, "baseUrl"), text(body, "workstation"), uuid(body, "dentistUserId"));
        audit.record("CLINIC_REGISTERED", result.get("clinicName") + " (" + result.get("clinicCode") + ") for "
                + result.get("ownerEmail"), request);
        return ResponseEntity.ok(result);
    }

    /**
     * PUT {clinicName, customerKey?, baseUrl, workstation}: edits the current clinic (a disconnected
     * one is connected again; it then needs the key).
     */
    @PutMapping
    public ResponseEntity<Map<String, Object>> update(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Map<String, Object> result = connections.update(text(body, "clinicCode"), text(body, "clinicName"), text(body, "customerKey"),
                text(body, "baseUrl"), text(body, "workstation"), uuid(body, "dentistUserId"));
        audit.record("CLINIC_UPDATED", result.get("clinicName") + " (" + result.get("clinicCode") + ")"
                + (text(body, "customerKey").isEmpty() ? "" : ", new key"), request);
        return ResponseEntity.ok(result);
    }

    /** PUT /webhooks {workstation}: saves the office computer; the subscriptions are set up in the background. */
    @PutMapping("/webhooks")
    public ResponseEntity<Map<String, Object>> webhooks(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        connections.saveWorkstationAndSetUp(text(body, "clinicCode"), text(body, "workstation"));
        audit.record("OPENDENTAL_WEBHOOKS_SET_UP", label(connections.manageable(text(body, "clinicCode")))
                + ", workstation " + text(body, "workstation"), request);
        return ResponseEntity.accepted().body(Map.of("settingUp", true));
    }

    /** POST /webhooks: checks and repairs the current clinic's subscriptions, in the background. */
    @PostMapping("/webhooks")
    public ResponseEntity<Map<String, Object>> repairWebhooks(@RequestBody(required = false) Map<String, Object> body,
                                                              HttpServletRequest request) {
        connections.repairInBackground(text(body, "clinicCode"));
        audit.record("OPENDENTAL_WEBHOOKS_SET_UP", label(connections.manageable(text(body, "clinicCode"))), request);
        return ResponseEntity.accepted().body(Map.of("settingUp", true));
    }

    /** DELETE {clinicCode}: stops syncing the clinic and removes its webhooks. Its data is kept (hidden). */
    @DeleteMapping
    public ResponseEntity<Void> disconnect(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        Clinic clinic = connections.disconnect(text(body, "clinicCode"));
        audit.record("OPENDENTAL_DISCONNECTED", label(clinic), request);
        return ResponseEntity.noContent().build();
    }

    /**
     * DELETE /clinics/{code} {confirmName}: removes a disconnected clinic and its copied data for good
     * (Admins: any clinic; Dentists: their own). The user types the clinic's name to confirm.
     */
    @DeleteMapping("/clinics/{clinicCode}")
    public ResponseEntity<Map<String, Object>> remove(@org.springframework.web.bind.annotation.PathVariable String clinicCode,
                                                      @RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        String removed = connections.remove(clinicCode, text(body, "confirmName"));
        audit.record("CLINIC_REMOVED", removed, request);
        return ResponseEntity.ok(Map.of("removed", removed));
    }

    private static String label(Clinic clinic) {
        return clinic == null ? "" : clinic.getClinicName() + " (" + clinic.getClinicCode() + ")";
    }

    private static java.util.UUID uuid(Map<String, Object> body, String key) {
        String value = text(body, key);
        try {
            return value.isEmpty() ? null : java.util.UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new com.clinic.opendental.exception.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "Unknown dentist.");
        }
    }

    private static String text(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }
}
