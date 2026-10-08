package com.clinic.opendental.controller;

import com.clinic.opendental.client.OpenDentalClient;
import com.clinic.opendental.exception.ApiException;
import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.repository.ClinicRepository;
import com.clinic.opendental.security.Permission;
import com.clinic.opendental.security.PermissionResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Account Module views are read live from Open Dental for the active clinic, and need billing access. */
class AccountModulesControllerTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Clinic CLINIC = Clinic.builder().id(UUID.randomUUID()).baseUrl("http://od").apiKey("k").build();

    private OpenDentalClient client;
    private AccountModulesController controller;

    @BeforeEach
    void setUp() {
        client = mock(OpenDentalClient.class);
        ClinicRepository clinics = mock(ClinicRepository.class);
        when(clinics.findByIsActiveTrue()).thenReturn(List.of(CLINIC));
        controller = new AccountModulesController(client, clinics);
    }

    @Test
    void eachViewCallsItsOpenDentalEndpoint() throws Exception {
        when(client.getRaw(anyString(), anyMap(), any(), any())).thenReturn(JSON.readTree("{\"Total\": 667.31}"));

        assertThat(controller.aging(1337).path("Total").asDouble()).isEqualTo(667.31);
        controller.patientBalances(16);
        controller.serviceDateView(65, true);

        verify(client).getRaw("/accountmodules/1337/Aging", Map.of(), "http://od", "k");
        verify(client).getRaw("/accountmodules/16/PatientBalances", Map.of(), "http://od", "k");
        verify(client).getRaw("/accountmodules/65/ServiceDateView", Map.of("isFamily", "true"), "http://od", "k");
    }

    @Test
    void anUnknownPatientIsReportedClearly() {
        when(client.getRaw(anyString(), anyMap(), any(), any()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null));

        assertThatThrownBy(() -> controller.aging(999))
                .isInstanceOf(ApiException.class).hasMessageContaining("not found in Open Dental");
        assertThatThrownBy(() -> controller.aging(-3))
                .isInstanceOf(ApiException.class).hasMessageContaining("saved in Open Dental");
    }

    @Test
    void readingAnAccountNeedsBillingAccess() {
        assertThat(new PermissionResolver().required("GET", "/api/accountmodules/16/Aging").permission())
                .isEqualTo(Permission.BILLING_READ);
    }
}
