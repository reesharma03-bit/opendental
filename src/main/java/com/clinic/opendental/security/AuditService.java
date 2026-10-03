package com.clinic.opendental.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Writes the audit trail. Recording never breaks the request it describes. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditEventRepository events;

    public void record(String action, String detail, UUID userId, String email, HttpServletRequest request) {
        try {
            events.save(AuditEvent.builder()
                    .action(action)
                    .detail(detail == null || detail.length() <= 2000 ? detail : detail.substring(0, 2000))
                    .userId(userId)
                    .userEmail(email)
                    .ipAddress(request == null ? null : clientIp(request))
                    .build());
        } catch (Exception e) {
            log.error("Could not write audit event {}: {}", action, e.getMessage());
        }
    }

    /** As the current signed-in user. */
    public void record(String action, String detail, HttpServletRequest request) {
        record(action, detail, CurrentUser.id(), CurrentUser.email(), request);
    }

    static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
