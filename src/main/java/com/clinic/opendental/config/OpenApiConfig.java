package com.clinic.opendental.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;

/**
 * Configures the OpenAPI documentation for Swagger UI.
 *
 * <p>Declares a global "apiKeyAuth" security scheme, which makes Swagger UI show
 * an "Authorize" button at the top-right. Entering the API key there applies the
 * Authorization header to every request, so you don't have to type it per endpoint.
 */
@Configuration
@SecurityScheme(
        name = "apiKeyAuth",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,
        paramName = "Authorization",
        description = "Open Dental API key (used by webhook subscription endpoints)"
)
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("OpenDental API")
                        .description("Webhook subscription management API")
                        .version("1.0.0"))
                // Apply the API key to all endpoints by default, so the Authorize
                // header (top-right) is attached to every request.
                .addSecurityItem(new SecurityRequirement().addList("apiKeyAuth"));
    }
}
