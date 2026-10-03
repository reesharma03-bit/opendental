package com.clinic.opendental.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

/**
 * Signed session tokens (HS256) carried in an HttpOnly cookie. A session ends after
 * {@code auth.session-minutes} without activity (HIPAA automatic log-off); activity
 * renews it.
 */
@Component
@Slf4j
public class TokenService {

    public static final String COOKIE = "smileos_session";

    private final SecretKey key;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final Duration sessionLength;
    private final boolean secureCookie;

    public TokenService(@Value("${auth.jwt-secret:}") String secret,
                        @Value("${auth.session-minutes:30}") long sessionMinutes,
                        @Value("${auth.cookie-secure:false}") boolean secureCookie) {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            log.warn("AUTH_JWT_SECRET is not set (or shorter than 32 characters): using a random key. "
                    + "Everyone is signed out whenever the backend restarts. Set AUTH_JWT_SECRET to keep sessions.");
            bytes = new byte[48];
            new SecureRandom().nextBytes(bytes);
        }
        this.key = new SecretKeySpec(bytes, "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        this.sessionLength = Duration.ofMinutes(sessionMinutes);
        this.secureCookie = secureCookie;
    }

    public JwtDecoder decoder() {
        return decoder;
    }

    public Duration sessionLength() {
        return sessionLength;
    }

    public String issue(AppUser user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(sessionLength))
                .claim("email", user.getEmail())
                .claim("name", user.getFullName())
                .claim("role", user.getRole().name())
                .claim("mcp", user.isMustChangePassword())
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    /** Decodes a token, or null when it is missing, forged or expired. */
    public Jwt decodeOrNull(String token) {
        if (token == null || token.isBlank()) return null;
        try {
            return decoder.decode(token);
        } catch (Exception e) {
            return null;
        }
    }

    public ResponseCookie cookie(String token) {
        return ResponseCookie.from(COOKIE, token)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/")
                .maxAge(sessionLength)
                .build();
    }

    public ResponseCookie clearCookie() {
        return ResponseCookie.from(COOKIE, "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();
    }
}
