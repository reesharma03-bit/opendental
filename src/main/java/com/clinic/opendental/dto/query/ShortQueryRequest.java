package com.clinic.opendental.dto.query;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShortQueryRequest {

    @NotBlank(message = "SqlCommand is required")
    private String SqlCommand;
}