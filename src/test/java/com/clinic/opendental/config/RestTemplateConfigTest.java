package com.clinic.opendental.config;

import com.clinic.opendental.client.OpenDentalClient;
import com.clinic.opendental.dto.patient.UpdatePatientRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Open Dental's API service rejects any request without an Authorization header
 * with {@code 400 "Malformed API request."}, so patient writes must always carry
 * the configured key.
 */
class RestTemplateConfigTest {

    private static final String UPDATED_PATIENT = "{\"PatNum\":47,\"HmPhone\":\"555-0199\"}";

    private OpenDentalClient client(RestTemplate template) {
        OpenDentalClient client = new OpenDentalClient(template);
        ReflectionTestUtils.setField(client, "baseUrl", "http://opendental.test/api/v1");
        return client;
    }

    @Test
    void appliesConfiguredApiKeyToPatientUpdate() {
        RestTemplate template = new RestTemplateConfig().restTemplate("ODFHIR devKey/custKey");
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();

        // RestTemplateConfig builds the RestTemplate with Jackson's default
        // (identity) naming, so Lombok's getHmPhone() serializes as "hmPhone"
        // (@JsonAlias on the DTO only affects deserialization).
        server.expect(requestTo("http://opendental.test/api/v1/patients/47"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("Authorization", "ODFHIR devKey/custKey"))
                .andExpect(content().json("{\"hmPhone\":\"555-0199\"}"))
                .andRespond(withSuccess(UPDATED_PATIENT, MediaType.APPLICATION_JSON));

        assertEquals("555-0199", client(template)
                .updatePatient(47L, UpdatePatientRequest.builder().HmPhone("555-0199").build())
                .getHmPhone());
        server.verify();
    }


    @Test
    void omitsAuthorizationHeaderWhenNoKeyIsConfigured() {
        RestTemplate template = new RestTemplateConfig().restTemplate("");
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();

        server.expect(requestTo("http://opendental.test/api/v1/patients/47"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess(UPDATED_PATIENT, MediaType.APPLICATION_JSON));

        client(template).updatePatient(
                47L, UpdatePatientRequest.builder().HmPhone("555-0199").build());
        server.verify();
    }
}