package com.clinic.opendental.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.util.function.Supplier;

@Configuration
public class RestTemplateConfig {

    /**
     * Open Dental's API service rejects any request that is missing the
     * Authorization header with {@code 400 "Malformed API request."}, so the
     * current clinic's api_key (clinics table) is attached to every outgoing call
     * that doesn't carry one already.
     *
     * <p>Requests that already carry an Authorization header (a per-clinic key
     * supplied by the caller) are left untouched.</p>
     */
    @Bean
    public RestTemplate restTemplate(ObjectProvider<OpenDentalConnection> connection,
                                     @Value("${opendental.min-request-interval-ms:1000}") long minRequestIntervalMs) {
        return restTemplate(() -> {
            OpenDentalConnection active = connection.getIfAvailable();
            try {
                return active == null ? null : active.apiKey();
            } catch (RuntimeException e) {
                return null; // no clinic for this call (background work names its own key)
            }
        }, minRequestIntervalMs);
    }

    /** @param apiKey the full Authorization value, e.g. {@code ODFHIR <DeveloperKey>/<CustomerKey>}; null or blank sends none */
    RestTemplate restTemplate(Supplier<String> apiKey, long minRequestIntervalMs) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add((request, body, execution) -> {
            if (!request.getHeaders().containsKey("Authorization")) {
                String key = apiKey.get();
                if (key != null && !key.isBlank()) {
                    request.getHeaders().set("Authorization", key);
                }
            }
            return execution.execute(request, body);
        });
        // Last, so a retry after 429 re-sends the request exactly as prepared above.
        restTemplate.getInterceptors().add(new OpenDentalRateLimiter(minRequestIntervalMs));
        return restTemplate;
    }

}
