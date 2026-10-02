package com.clinic.opendental.dto.appointment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchedulePlannedRequest {

    @NotNull(message = "AptNum is required")
    private Long AptNum;

    @NotBlank(message = "AptDateTime is required")
    private String AptDateTime;

    @NotNull(message = "ProvNum is required")
    private Long ProvNum;

    @NotNull(message = "Op is required")
    private Long Op;

    private Long Confirmed;
    private String Note;
}