package com.clinic.opendental.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OpenDentalClientDiseaseDefsTest {
    private OpenDentalClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestTemplate template = new RestTemplate();
        server = MockRestServiceServer.bindTo(template).build();
        client = new OpenDentalClient(template);
        ReflectionTestUtils.setField(client, "baseUrl", "http://opendental.test/api/v1");
    }

    @Test
    void forwardsPaginationAndReadsOfficialListShape() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("Limit", "100");
        params.put("Offset", "200");
        server.expect(requestTo("http://opendental.test/api/v1/diseasedefs?Limit=100&Offset=200"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"DiseaseDefNum\":59,\"DiseaseName\":\"Hypertension\","
                        + "\"IsHidden\":\"false\",\"ICD9Code\":\"401.9\",\"ICD10Code\":\"\","
                        + "\"SnomedCode\":\"\",\"DateTStamp\":\"2021-09-07 14:00:10\"}]", MediaType.APPLICATION_JSON));
        assertEquals("401.9", client.getDiseaseDefs(params).get(0).get("ICD9Code"));
        server.verify();
    }

    @Test
    void readsSingleDefinition() {
        server.expect(requestTo("http://opendental.test/api/v1/diseasedefs/58"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"DiseaseDefNum\":58,\"DiseaseName\":\"Severe Back Pain\"}",
                        MediaType.APPLICATION_JSON));
        assertEquals(58, client.getDiseaseDef(58L).get("DiseaseDefNum"));
        server.verify();
    }

    @Test
    void acceptsBodylessCreateResponse() {
        server.expect(requestTo("http://opendental.test/api/v1/diseasedefs"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"DiseaseName\":\"Shingles\"}"))
                .andRespond(withStatus(HttpStatus.CREATED));
        assertDoesNotThrow(() -> client.createDiseaseDef(Map.of("DiseaseName", "Shingles")));
        server.verify();
    }
}