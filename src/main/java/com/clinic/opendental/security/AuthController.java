package com.clinic.opendental.security;

import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * POST /api/auth/login            {"email", "password"} -> signed-in user (sets the session cookie)
 * POST /api/auth/logout           ends the session
 * GET  /api/auth/me               the signed-in user, role and permissions
 * POST /api/auth/change-password  {"currentPassword", "newPassword"}
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final TokenService tokens;
    private final AuditService audit;

    public record LoginRequest(String email, String password) {
    }

    public record ChangePasswordRequest(@JsonAlias("currentPassword") String currentPassword,
                                        @JsonAlias("newPassword") String newPassword) {
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest body, HttpServletRequest request) {
        AppUser user = userService.signIn(body.email(), body.password(), request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, tokens.cookie(tokens.issue(user)).toString())
                .body(me(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        Jwt jwt = tokens.decodeOrNull(sessionCookie(request));
        if (jwt != null) {
            audit.record("LOGOUT", null, UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("email"), request);
        }
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, tokens.clearCookie().toString()).build();
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> current() {
        return ResponseEntity.ok(me(userService.find(CurrentUser.id())));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(@RequestBody ChangePasswordRequest body, HttpServletRequest request) {
        AppUser user = userService.changePassword(CurrentUser.id(), body.currentPassword(), body.newPassword(), request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, tokens.cookie(tokens.issue(user)).toString())
                .body(me(user));
    }

    private Map<String, Object> me(AppUser user) {
        Map<String, Object> out = new LinkedHashMap<>(UserService.describe(user));
        out.put("permissions", user.getRole().permissions().stream().map(Enum::name).sorted().toList());
        out.put("sessionMinutes", tokens.sessionLength().toMinutes());
        return out;
    }

    private static String sessionCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (TokenService.COOKIE.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
