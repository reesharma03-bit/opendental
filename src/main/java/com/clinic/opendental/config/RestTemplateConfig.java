package com.clinic.opendental.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    /**
     * Open Dental's API service rejects any request that is missing the
     * Authorization header with {@code 400 "Malformed API request."}, so the
     * configured API key is attached to every outgoing call here. This covers
     * patient writes, which previously sent no credentials at all.
     *
     * <p>Requests that already carry an Authorization header (a per-clinic key
     * supplied by the caller) are left untouched, so multi-clinic keys keep
     * taking precedence over the configured default.</p>
     *
     * @param apiKey full Authorization header value, e.g.
     *               {@code ODFHIR <DeveloperKey>/<CustomerKey>}; blank disables
     *               the header entirely
     */
    @Bean
    public RestTemplate restTemplate(@Value("${opendental.api-key:}") String apiKey) {
        RestTemplate restTemplate = new RestTemplate();
        if (apiKey != null && !apiKey.isBlank()) {
            ClientHttpRequestInterceptor apiKeyInterceptor =
                    (HttpRequest request, byte[] body, ClientHttpRequestExecution execution) -> {
                        if (!request.getHeaders().containsKey("Authorization")) {
                            request.getHeaders().set("Authorization", apiKey);
                        }
                        return execution.execute(request, body);
                    };
            restTemplate.getInterceptors().add(apiKeyInterceptor);
        }
        return restTemplate;
    }

}