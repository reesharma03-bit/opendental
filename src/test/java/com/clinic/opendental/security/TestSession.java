package com.clinic.opendental.security;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/** A signed-in user and an HTTP request for code that reads {@link CurrentUser} / {@link CurrentClinic}. */
public final class TestSession {

    private TestSession() {
    }

    /** Signs in a user of {@code role} belonging to {@code clinicId} (null: none); returns their id. */
    public static UUID signIn(Role role, UUID clinicId) {
        return signIn(role, clinicId, null);
    }

    /** As {@link #signIn(Role, UUID)}, with the clinic switcher's header set to {@code chosenClinicCode}. */
    public static UUID signIn(Role role, UUID clinicId, String chosenClinicCode) {
        UUID userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "none")
                .subject(userId.toString())
                .claim("email", role.name().toLowerCase() + "@example.com")
                .claim("role", role.name())
                .claim("clinic", clinicId == null ? "" : clinicId.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                role.permissions().stream().map(p -> new SimpleGrantedAuthority(p.authority())).toList()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (chosenClinicCode != null) request.addHeader(CurrentClinic.HEADER, chosenClinicCode);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return userId;
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }
}
