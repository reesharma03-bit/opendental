package com.clinic.opendental.security;

import com.clinic.opendental.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Sign-in, password rules and user administration. */
@Service
@Slf4j
public class UserService {

    static final int MIN_PASSWORD_LENGTH = 12;

    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final AuditService audit;
    private final int maxFailedAttempts;
    private final Duration lockout;
    /** Compared against when the email is unknown, so a wrong email takes as long as a wrong password. */
    private final String dummyHash;

    public UserService(AppUserRepository users, PasswordEncoder passwords, AuditService audit,
                       @Value("${auth.max-failed-attempts:5}") int maxFailedAttempts,
                       @Value("${auth.lockout-minutes:15}") long lockoutMinutes) {
        this.users = users;
        this.passwords = passwords;
        this.audit = audit;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockout = Duration.ofMinutes(lockoutMinutes);
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    // ------------------------------------------------------------------ signing in

    /**
     * Not one transaction on purpose: a failed attempt ends in an exception, and the
     * attempt counter, lock-out and audit entry must still be saved.
     */
    public AppUser signIn(String email, String password, HttpServletRequest request) {
        String cleanEmail = email == null ? "" : email.trim();
        AppUser user = users.findByEmail(cleanEmail).orElse(null);
        if (user == null) {
            passwords.matches(password == null ? "" : password, dummyHash);
            audit.record("LOGIN_FAILED", "unknown email", null, cleanEmail, request);
            throw wrongCredentials();
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            audit.record("LOGIN_FAILED", "account locked", user.getId(), user.getEmail(), request);
            long minutes = Math.max(1, Duration.between(LocalDateTime.now(), user.getLockedUntil()).toMinutes() + 1);
            throw new ApiException(HttpStatus.UNAUTHORIZED,
                    "Too many failed attempts. Try again in " + minutes + " minute" + (minutes == 1 ? "" : "s") + ".");
        }
        if (!passwords.matches(password == null ? "" : password, user.getPasswordHash()) || !user.isActive()) {
            if (user.isActive()) {
                user.setFailedAttempts(user.getFailedAttempts() + 1);
                if (user.getFailedAttempts() >= maxFailedAttempts) {
                    user.setLockedUntil(LocalDateTime.now().plus(lockout));
                    user.setFailedAttempts(0);
                    audit.record("ACCOUNT_LOCKED", "after " + maxFailedAttempts + " failed attempts", user.getId(), user.getEmail(), request);
                }
                users.save(user);
            }
            audit.record("LOGIN_FAILED", user.isActive() ? "wrong password" : "inactive account", user.getId(), user.getEmail(), request);
            throw wrongCredentials();
        }
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());
        users.save(user);
        audit.record("LOGIN", null, user.getId(), user.getEmail(), request);
        return user;
    }

    private static ApiException wrongCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect.");
    }

    @Transactional
    public AppUser changePassword(UUID userId, String current, String next, HttpServletRequest request) {
        AppUser user = find(userId);
        if (!passwords.matches(current == null ? "" : current, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Your current password is incorrect.");
        }
        if (passwords.matches(next == null ? "" : next, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a password different from your current one.");
        }
        checkPassword(next, user.getEmail());
        user.setPasswordHash(passwords.encode(next));
        user.setMustChangePassword(false);
        users.save(user);
        audit.record("PASSWORD_CHANGED", null, user.getId(), user.getEmail(), request);
        return user;
    }

    /** At least 12 characters with letters and numbers, and not built from the email address. */
    static void checkPassword(String password, String email) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Use at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (!password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Use both letters and numbers.");
        }
        String local = email == null ? "" : email.split("@")[0].toLowerCase();
        if (local.length() >= 4 && password.toLowerCase().contains(local)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Don't use your email address in the password.");
        }
    }

    // ------------------------------------------------------------------ administration

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        users.findAllByOrderByFullNameAsc().forEach(u -> out.add(describe(u)));
        return out;
    }

    @Transactional
    public Map<String, Object> create(String email, String fullName, String role, String password, HttpServletRequest request) {
        String cleanEmail = email == null ? "" : email.trim();
        if (!cleanEmail.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter a valid email address.");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter the person's name.");
        }
        if (users.findByEmail(cleanEmail).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "A user with this email already exists.");
        }
        Role parsed = parseRole(role);
        checkPassword(password, cleanEmail);
        AppUser user = users.save(AppUser.builder()
                .email(cleanEmail)
                .fullName(fullName.trim())
                .role(parsed)
                .passwordHash(passwords.encode(password))
                .active(true)
                .mustChangePassword(true)
                .build());
        audit.record("USER_CREATED", cleanEmail + " as " + parsed.label(), request);
        return describe(user);
    }

    @Transactional
    public Map<String, Object> update(UUID id, String fullName, String role, Boolean active, HttpServletRequest request) {
        AppUser user = find(id);
        List<String> changes = new ArrayList<>();
        Role newRole = role == null ? user.getRole() : parseRole(role);
        boolean newActive = active == null ? user.isActive() : active;
        boolean removesAdmin = user.getRole() == Role.ADMIN && user.isActive() && (newRole != Role.ADMIN || !newActive);
        if (removesAdmin && users.countByRoleAndActiveTrue(Role.ADMIN) <= 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "There must always be at least one active Admin.");
        }
        if (id.equals(CurrentUser.id()) && !newActive) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You can't deactivate your own account.");
        }
        if (fullName != null && !fullName.isBlank() && !fullName.trim().equals(user.getFullName())) {
            changes.add("name");
            user.setFullName(fullName.trim());
        }
        if (newRole != user.getRole()) {
            changes.add("role " + user.getRole().label() + " -> " + newRole.label());
            user.setRole(newRole);
        }
        if (newActive != user.isActive()) {
            changes.add(newActive ? "activated" : "deactivated");
            user.setActive(newActive);
        }
        users.save(user);
        if (!changes.isEmpty()) {
            audit.record("USER_UPDATED", user.getEmail() + ": " + String.join(", ", changes), request);
        }
        return describe(user);
    }

    @Transactional
    public Map<String, Object> resetPassword(UUID id, String password, HttpServletRequest request) {
        AppUser user = find(id);
        checkPassword(password, user.getEmail());
        user.setPasswordHash(passwords.encode(password));
        user.setMustChangePassword(true);
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        users.save(user);
        audit.record("PASSWORD_RESET", user.getEmail(), request);
        return describe(user);
    }

    public AppUser find(UUID id) {
        return users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));
    }

    private static Role parseRole(String role) {
        try {
            Role parsed = Role.parse(role);
            if (parsed != null) return parsed;
        } catch (IllegalArgumentException ignored) {
            // fall through
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a role.");
    }

    public static Map<String, Object> describe(AppUser user) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", user.getId());
        out.put("email", user.getEmail());
        out.put("fullName", user.getFullName());
        out.put("role", user.getRole().name());
        out.put("roleLabel", user.getRole().label());
        out.put("active", user.isActive());
        out.put("mustChangePassword", user.isMustChangePassword());
        out.put("locked", user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now()));
        out.put("lastLoginAt", user.getLastLoginAt() == null ? null : user.getLastLoginAt().toString());
        return out;
    }
}
