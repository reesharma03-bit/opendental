package com.clinic.opendental.service;

import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.repository.ClinicRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** Webhooks are accepted only from a known practice, identified by the API key Open Dental sends. */
class WebhookClinicsTest {

    private static Clinic clinic(String code, String apiKey) {
        return Clinic.builder().clinicCode(code).apiKey(apiKey).build();
    }

    private static WebhookClinics with(String defaultKey, Clinic... active) {
        ClinicRepository clinics = mock(ClinicRepository.class);
        when(clinics.findByIsActiveTrue()).thenReturn(List.of(active));
        return new WebhookClinics(clinics, defaultKey);
    }

    @Test
    void theApiKeyPicksThePractice() {
        WebhookClinics clinics = with("", clinic("A", "ODFHIR dev123/custA"), clinic("B", "ODFHIR dev123/custB"));

        assertThat(clinics.resolve("custB").getClinicCode()).isEqualTo("B");
        assertThat(clinics.resolve("ODFHIR dev123/custA").getClinicCode()).isEqualTo("A");
    }

    @Test
    void aClinicWithoutItsOwnKeyUsesTheDefaultKey() {
        assertThat(with("ODFHIR dev/custDefault", clinic("A", "")).resolve("custDefault").getClinicCode()).isEqualTo("A");
    }

    @Test
    void aWrongOrMissingKeyIsRefusedEvenWithOneClinic() {
        WebhookClinics clinics = with("", clinic("A", "dev/custA"));

        assertThatThrownBy(() -> clinics.resolve("custZ")).isInstanceOf(WebhookClinics.UnknownPractice.class)
                .hasMessageContaining("unknown practice");
        assertThatThrownBy(() -> clinics.resolve(null)).hasMessageContaining("without an Open Dental API key");
        assertThatThrownBy(() -> with("").resolve("custA")).hasMessageContaining("No active clinic");
    }

    @Test
    void onlyASingleClinicWithNoKeyAtAllAcceptsUnsignedEvents() {
        assertThat(with("", clinic("LOCAL", "")).resolve(null).getClinicCode()).isEqualTo("LOCAL");
        assertThatThrownBy(() -> with("", clinic("A", ""), clinic("B", "")).resolve(null))
                .isInstanceOf(WebhookClinics.UnknownPractice.class);
    }

    @Test
    void customerKeysAreReadInAnyForm() {
        assertThat(WebhookClinics.customerKey("ODFHIR abc/xyz")).isEqualTo("xyz");
        assertThat(WebhookClinics.customerKey("abc/xyz")).isEqualTo("xyz");
        assertThat(WebhookClinics.customerKey(" xyz ")).isEqualTo("xyz");
        assertThat(WebhookClinics.customerKey(null)).isEmpty();
    }
}
