package com.clinic.opendental.dto.patient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that a snake_case patient payload (as returned by Open Dental's REST
 * query endpoint {@code GET /patients}) now deserializes correctly after adding
 * snake_case {@link com.fasterxml.jackson.annotation.JsonAlias} entries to
 * {@link PatientResponse}.
 *
 * <p>The app's {@code RestTemplate} uses an identity-naming Jackson mapper.
 * Without a snake_case alias, {@code "pat_num": 101} would never map to the
 * PascalCase field {@code PatNum}, and every field would remain null (the root
 * cause of the reported {@code null value in column "pat_num"} error).
 */
class PatientResponseDeserializationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final TypeReference<List<PatientResponse>> LIST_TYPE =
            new TypeReference<>() {};

    @Test
    void deserializesSnakeCasePatientList() throws Exception {
        String json;
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("patient/opendental-patients-snake-sample.json")) {
            assertThat(is).as("test sample resource").isNotNull();
            json = new String(is.readAllBytes());
        }

        List<PatientResponse> result = MAPPER.readValue(json, LIST_TYPE);

        assertThat(result).as("parsed patient list").hasSize(1);

        PatientResponse patient = result.get(0);

        // The core fix: patNum must now be populated from snake_case "pat_num"
        assertThat(patient.getPatNum()).as("patNum").isEqualTo(101L);

        // Key fields that were all-null before the fix:
        assertThat(patient.getLName()).as("lName").isEqualTo("Smith");
        assertThat(patient.getFName()).as("fName").isEqualTo("John");
        assertThat(patient.getAddress()).as("address").isEqualTo("123 Main St");
        assertThat(patient.getCity()).as("city").isEqualTo("Springfield");
        assertThat(patient.getState()).as("state").isEqualTo("IL");
        assertThat(patient.getZip()).as("zip").isEqualTo("62701");
        assertThat(patient.getHmPhone()).as("hmPhone").isEqualTo("555-0101");
        assertThat(patient.getHasIns()).as("hasIns").isEqualTo("Y");
        assertThat(patient.getEstBalance()).as("estBalance").isEqualTo(150.00);
        assertThat(patient.getBalTotal()).as("balTotal").isEqualTo(150.00);

        // Extra coverage: additional fields mapped via snake_case
        assertThat(patient.getSSN()).as("ssn").isEqualTo("123-45-6789");
        assertThat(patient.getGuarantor()).as("guarantor").isEqualTo(101L);
        assertThat(patient.getFeeSched()).as("feeSched").isEqualTo(1L);
        assertThat(patient.getBillingType()).as("billingType").isEqualTo("Standard");
        assertThat(patient.getBal_0_30()).as("bal_0_30").isEqualTo(100.00);
        assertThat(patient.getBal_31_60()).as("bal_31_60").isEqualTo(50.00);
        assertThat(patient.getInsEst()).as("insEst").isEqualTo(75.00);
    }
}