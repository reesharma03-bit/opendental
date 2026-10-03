package com.clinic.opendental.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * User administration (Admin only, see {@link PermissionResolver}).
 *
 * GET  /api/users                       all users
 * GET  /api/users/roles                 roles and what each may do
 * POST /api/users                       {"email", "fullName", "role", "password"} (temporary password)
 * PUT  /api/users/{id}                  {"fullName"?, "role"?, "active"?}
 * POST /api/users/{id}/reset-password   {"password"} (temporary password)
 * GET  /api/audit?limit=200             recent audit trail
 */
@RestController
@RequiredArgsConstructor
public class UserAdminController {

    private final UserService userService;
    private final AuditEventRepository events;

    public record CreateUserRequest(String email, String fullName, String role, String password) {
    }

    public record UpdateUserRequest(String fullName, String role, Boolean active) {
    }

    public record ResetPasswordRequest(String password) {
    }

    @GetMapping("/api/users")
    public List<Map<String, Object>> list() {
        return userService.list();
    }

    @GetMapping("/api/users/roles")
    public List<Map<String, Object>> roles() {
        return Arrays.stream(Role.values()).map(role -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("role", role.name());
            out.put("label", role.label());
            out.put("permissions", role.permissions().stream().map(Enum::name).sorted().toList());
            return out;
        }).toList();
    }

    @PostMapping("/api/users")
    public ResponseEntity<Map<String, Object>> create(@RequestBody CreateUserRequest body, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userService.create(body.email(), body.fullName(), body.role(), body.password(), request));
    }

    @PutMapping("/api/users/{id}")
    public Map<String, Object> update(@PathVariable UUID id, @RequestBody UpdateUserRequest body, HttpServletRequest request) {
        return userService.update(id, body.fullName(), body.role(), body.active(), request);
    }

    @PostMapping("/api/users/{id}/reset-password")
    public Map<String, Object> resetPassword(@PathVariable UUID id, @RequestBody ResetPasswordRequest body,
                                             HttpServletRequest request) {
        return userService.resetPassword(id, body.password(), request);
    }

    @GetMapping("/api/audit")
    public List<Map<String, Object>> audit(@RequestParam(defaultValue = "200") int limit) {
        return events.findAllByOrderByIdDesc(PageRequest.of(0, Math.max(1, Math.min(limit, 1000)))).stream()
                .map(e -> {
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("id", e.getId());
                    out.put("at", e.getCreatedAt() == null ? null : e.getCreatedAt().toString());
                    out.put("user", e.getUserEmail());
                    out.put("action", e.getAction());
                    out.put("detail", e.getDetail());
                    out.put("ip", e.getIpAddress());
                    return out;
                }).toList();
    }
}
