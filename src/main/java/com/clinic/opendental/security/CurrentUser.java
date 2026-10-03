package com.clinic.opendental.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/** The signed-in user of the current request. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id() {
        JwtAuthenticationToken token = token();
        return token == null ? null : UUID.fromString(token.getToken().getSubject());
    }

    public static String email() {
        JwtAuthenticationToken token = token();
        return token == null ? null : token.getToken().getClaimAsString("email");
    }

    public static Role role() {
        JwtAuthenticationToken token = token();
        return token == null ? null : Role.parse(token.getToken().getClaimAsString("role"));
    }

    /** Permissions of the current user; none when nobody is signed in. */
    public static Set<Permission> permissions() {
        Role role = role();
        return role == null ? EnumSet.noneOf(Permission.class) : role.permissions();
    }

    public static boolean can(Permission permission) {
        return permissions().contains(permission);
    }

    private static JwtAuthenticationToken token() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth instanceof JwtAuthenticationToken jwt ? jwt : null;
    }
}
