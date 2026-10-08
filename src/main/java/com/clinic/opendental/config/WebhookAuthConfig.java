package com.clinic.opendental.config;

import com.clinic.opendental.service.WebhookClinics;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Webhooks can't sign in (Open Dental calls them), so they are public routes; instead every
 * call must carry a known practice's Open Dental API key, which Open Dental sends in the
 * Authorization header. Anything else is refused before a byte of it is saved.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class WebhookAuthConfig implements WebMvcConfigurer {

    /** Looked up per call, so web slices that don't include it (controller tests) still start. */
    private final ObjectProvider<WebhookClinics> webhookClinics;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                try {
                    webhookClinics.getObject().clinic();
                    return true;
                } catch (WebhookClinics.UnknownPractice e) {
                    log.warn("Refused webhook {} from {}: {}", request.getRequestURI(), request.getRemoteAddr(), e.getMessage());
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write("{\"error\":\"Unknown Open Dental practice\",\"code\":\"UNAUTHENTICATED\"}");
                    return false;
                }
            }
        }).addPathPatterns("/api/webhooks/**");
    }
}
