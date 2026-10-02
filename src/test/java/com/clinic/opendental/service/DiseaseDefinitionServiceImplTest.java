package com.clinic.opendental.service;

import com.clinic.opendental.client.OpenDentalClient;
import com.clinic.opendental.exception.ApiException;
import com.clinic.opendental.service.Impl.DiseaseDefinitionServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DiseaseDefinitionServiceImplTest {
    private final OpenDentalClient client = mock(OpenDentalClient.class);
    private final DiseaseDefinitionServiceImpl service = new DiseaseDefinitionServiceImpl(client);

    @Test
    void unreachableUpstreamReturnsClearBadGatewayInsteadOfSampleData() {
        when(client.getDiseaseDefs(Map.of())).thenThrow(new ResourceAccessException("Connection refused"));
        ApiException error = assertThrows(ApiException.class,
                () -> service.getDiseaseDefinitions(Map.of()));
        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatus());
        assertTrue(error.getErrorMessage().contains("Unable to reach the Open Dental API"));
    }

    @Test
    void upstreamServerFailureReturnsClearBadGateway() {
        when(client.getDiseaseDef(58L)).thenThrow(
                new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));
        ApiException error = assertThrows(ApiException.class,
                () -> service.getDiseaseDefinition(58L));
        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatus());
        assertTrue(error.getErrorMessage().contains("503"));
    }

    @Test
    void duplicateNameErrorIsNotReplacedWithSuccess() {
        HttpClientErrorException duplicate = new HttpClientErrorException(HttpStatus.BAD_REQUEST);
        doThrow(duplicate).when(client).createDiseaseDef(Map.of("DiseaseName", "Shingles"));
        assertSame(duplicate, assertThrows(HttpClientErrorException.class,
                () -> service.createDiseaseDefinition(Map.of("DiseaseName", "Shingles"))));
    }
}