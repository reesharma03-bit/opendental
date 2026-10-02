package com.clinic.opendental.dto.procedurelog;

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
public class InsuranceHistoryRequest {

    @NotNull(message = "PatNum is required")
    private Long PatNum;

    @NotNull(message = "InsSubNum is required")
    private Long InsSubNum;

    @NotBlank(message = "insHistPrefName is required")
    private String insHistPrefName;

    @NotBlank(message = "ProcDate is required")
    private String ProcDate;
}