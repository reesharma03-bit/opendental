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
public class WebSchedRequest {

    @NotNull(message = "PatNum is required")
    private Long PatNum;

    @NotBlank(message = "DateTimeStart is required")
    private String DateTimeStart;

    @NotBlank(message = "DateTimeEnd is required")
    private String DateTimeEnd;

    @NotNull(message = "ProvNum is required")
    private Long ProvNum;

    @NotNull(message = "OpNum is required")
    private Long OpNum;

    @NotNull(message = "defNumApptType is required")
    private Long defNumApptType;
}