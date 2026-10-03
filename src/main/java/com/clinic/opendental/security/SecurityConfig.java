package com.clinic.opendental.security;

import com.clinic.opendental.security.PermissionResolver.Requirement;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Who may call what. Every request is checked against {@link PermissionResolver};
 * sessions are stateless signed tokens in an HttpOnly, SameSite=Strict cookie (which is
 * also why there is no CSRF token: other sites cannot send that cookie).
 */
@Configuration
public class SecurityConfig {

    /** How often an active session is re-checked against the user table and renewed. */
    static final Duration RENEW_AFTER = Duration.ofMinutes(5);

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, TokenService tokens, PermissionResolver permissions,
                                                   AppUserRepository users, AuditService audit) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())
                .logout(l -> l.disable())
                .oauth2ResourceServer(o -> o
                        .bearerTokenResolver(tokenResolver(permissions))
                        .jwt(j -> j.decoder(tokens.decoder()).jwtAuthenticationConverter(authenticationConverter()))
                        .authenticationEntryPoint((req, res, ex) -> {
                            res.addHeader(HttpHeaders.SET_COOKIE, tokens.clearCookie().toString());
                            json(res, 401, "Your session has ended. Please sign in again.", "UNAUTHENTICATED");
                        }))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> json(res, 401, "Please sign in.", "UNAUTHENTICATED"))
                        .accessDeniedHandler((req, res, ex) -> {
                            audit.record("ACCESS_DENIED", req.getMethod() + " " + req.getRequestURI(), req);
                            Role role = CurrentUser.role();
                            json(res, 403, "Your role" + (role == null ? "" : " (" + role.label() + ")")
                                    + " doesn't allow this.", "FORBIDDEN");
                        }))
                .authorizeHttpRequests(a -> a.anyRequest().access(authorization(permissions)))
                .addFilterAfter(new SessionFilter(tokens, users), BearerTokenAuthenticationFilter.class)
                .addFilterAfter(new WriteAuditFilter(audit), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    static AuthorizationManager<RequestAuthorizationContext> authorization(PermissionResolver permissions) {
        return (Supplier<Authentication> authentication, RequestAuthorizationContext context) -> {
            HttpServletRequest request = context.getRequest();
            Requirement required = permissions.required(request.getMethod(), request.getRequestURI());
            Authentication auth = authentication.get();
            boolean signedIn = auth instanceof JwtAuthenticationToken && auth.isAuthenticated();
            return new AuthorizationDecision(switch (required.kind()) {
                case PUBLIC -> true;
                case AUTHENTICATED -> signedIn;
                case PERMISSION -> signedIn && auth.getAuthorities().stream()
                        .anyMatch(g -> g.getAuthority().equals(required.permission().authority()));
            });
        };
    }

    /** Token from the session cookie (or an Authorization header); ignored on public routes. */
    static BearerTokenResolver tokenResolver(PermissionResolver permissions) {
        DefaultBearerTokenResolver header = new DefaultBearerTokenResolver();
        return request -> {
            if (permissions.required(request.getMethod(), request.getRequestURI()).kind() == Requirement.Kind.PUBLIC) {
                return null; // an old cookie must not block signing in
            }
            String fromHeader = header.resolve(request);
            if (fromHeader != null) return fromHeader;
            if (request.getCookies() == null) return null;
            for (Cookie cookie : request.getCookies()) {
                if (TokenService.COOKIE.equals(cookie.getName()) && !cookie.getValue().isBlank()) return cookie.getValue();
            }
            return null;
        };
    }

    static JwtAuthenticationConverter authenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roleToPermissions());
        converter.setPrincipalClaimName("email");
        return converter;
    }

    static Converter<Jwt, Collection<GrantedAuthority>> roleToPermissions() {
        return jwt -> {
            List<GrantedAuthority> authorities = new ArrayList<>();
            Role role;
            try {
                role = Role.parse(jwt.getClaimAsString("role"));
            } catch (IllegalArgumentException e) {
                return authorities;
            }
            if (role == null) return authorities;
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
            role.permissions().forEach(p -> authorities.add(new SimpleGrantedAuthority(p.authority())));
            return authorities;
        };
    }

    static void json(HttpServletResponse response, int status, String message, String code) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + message.replace("\"", "'") + "\",\"code\":\"" + code + "\"}");
    }

    /**
     * Renews an active session every few minutes, re-checking the user so that a
     * deactivated user or a changed role takes effect without waiting for log-out; and
     * holds back everything but password change while one is required.
     */
    static class SessionFilter extends OncePerRequestFilter {
        private final TokenService tokens;
        private final AppUserRepository users;

        SessionFilter(TokenService tokens, AppUserRepository users) {
            this.tokens = tokens;
            this.users = users;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken auth)) {
                chain.doFilter(request, response);
                return;
            }
            Jwt jwt = auth.getToken();
            boolean authRoute = request.getRequestURI().startsWith("/api/auth/");
            if (Boolean.TRUE.equals(jwt.getClaimAsBoolean("mcp")) && !authRoute) {
                json(response, 403, "Change your password before continuing.", "PASSWORD_CHANGE_REQUIRED");
                return;
            }
            Instant issued = jwt.getIssuedAt();
            if (issued == null || issued.plus(RENEW_AFTER).isBefore(Instant.now())) {
                // Lock-outs only stop new sign-ins, so failed guesses can't end someone else's session.
                AppUser user = users.findById(UUID.fromString(jwt.getSubject())).orElse(null);
                if (user == null || !user.isActive()) {
                    SecurityContextHolder.clearContext();
                    response.addHeader(HttpHeaders.SET_COOKIE, tokens.clearCookie().toString());
                    json(response, 401, "Your account is no longer active.", "UNAUTHENTICATED");
                    return;
                }
                response.addHeader(HttpHeaders.SET_COOKIE, tokens.cookie(tokens.issue(user)).toString());
            }
            chain.doFilter(request, response);
        }
    }

    /** Records every change made through the API: who, what, and the result. */
    static class WriteAuditFilter extends OncePerRequestFilter {
        private final AuditService audit;

        WriteAuditFilter(AuditService audit) {
            this.audit = audit;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            chain.doFilter(request, response);
            String path = request.getRequestURI();
            String method = request.getMethod();
            boolean change = !List.of("GET", "HEAD", "OPTIONS").contains(method);
            if (change && path.startsWith("/api/") && !path.startsWith("/api/auth/") && !path.startsWith("/api/webhooks/")
                    && !path.startsWith("/api/assistant") && CurrentUser.id() != null) {
                audit.record("API_WRITE", method + " " + path + " -> " + response.getStatus(), request);
            }
        }
    }
}
