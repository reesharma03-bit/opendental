package com.clinic.opendental.dto.clinic;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Open Dental clinic record mapper (GET /api/v1/clinics).
 *
 * <p>Follows the same convention as the other DTOs: the app uses a global
 * {@code SNAKE_CASE} Jackson naming strategy for its REST API, while Open Dental
 * returns <b>PascalCase</b> keys ({@code ClinicNum}, {@code Abbr}, ...). Each
 * field declares {@link JsonAlias} with the PascalCase name so both map correctly
 * during deserialization.
 *
 * <p>VERIFY AGAINST CURRENT OPEN DENTAL API DOCUMENTATION: the {@code /clinics}
 * endpoint may expose additional fields (e.g. IsHidden, BillingPrefix, ...).
 * Only fields confirmed against the live specification should be added below.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClinicResponse {

    @JsonAlias("ClinicNum")
    private Long ClinicNum;

    @JsonAlias("Abbr")
    private String Abbr;

    @JsonAlias("Description")
    private String Description;
}
