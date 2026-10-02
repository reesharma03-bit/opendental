package com.clinic.opendental.dto.appointment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the "Error while extracting response for type
 * List&lt;AppointmentResponse&gt;" failure.
 *
 * <p>Open Dental returns a top-level JSON array whose objects carry
 * duplicate, case-variant keys — e.g. both {@code "Confirmed": 19} (number)
 * and {@code "confirmed": "Unconfirmed"} (string). Under the app's global
 * {@code spring.jackson.property-naming-strategy: SNAKE_CASE} the Long field
 * {@code Confirmed} is derived to property name {@code confirmed}, so the
 * lowercase string variant collided and was coerced into the Long → fail.
 * Pinning {@code @JsonProperty("Confirmed")} fixes it.
 */
class AppointmentResponseDeserializationTest {

    /**
     * Builds an ObjectMapper that faithfully replicates the application's global
     * Jackson configuration declared in {@code application.yaml}:
     * {@code spring.jackson.property-naming-strategy: SNAKE_CASE}.
     * (FAIL_ON_UNKNOWN is Jackson's default false; the DTO also carries
     * {@code @JsonIgnoreProperties(ignoreUnknown = true)}.)
     *
     * Plain unit test (no Spring context / no DB) so the deserialization path is
     * validated deterministically and fast.
     */
    private static final ObjectMapper APP_MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    private static final TypeReference<List<AppointmentResponse>> LIST_TYPE =
            new TypeReference<>() {};

    @Test
    void deserializesSampleWithDuplicateConfirmedKeysUnderSnakeCaseStrategy() throws Exception {
        String json;
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("appointment/opendental-appointments-sample.json")) {
            assertThat(is).as("test sample resource").isNotNull();
            json = new String(is.readAllBytes());
        }

        // Before the @JsonProperty("Confirmed") fix this threw:
        //   JsonMappingException: Cannot deserialize value of type `java.lang.Long`
        //   from String "Unconfirmed", ... property "confirmed"
        List<AppointmentResponse> result = APP_MAPPER.readValue(json, LIST_TYPE);

        assertThat(result).as("parsed appointment list").hasSize(1);

        AppointmentResponse appt = result.get(0);
        assertThat(appt.getAptNum()).isEqualTo(43L);
        assertThat(appt.getPatNum()).isEqualTo(9L);
        // The PascalCase numeric value is what the Long field must hold.
        assertThat(appt.getConfirmed()).isEqualTo(19L);
        assertThat(appt.getServerDateTime()).isEqualTo("2026-08-08 10:58:44");
    }
}
