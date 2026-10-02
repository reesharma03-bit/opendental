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
public class QueryRequest {

    @NotBlank(message = "SqlCommand is required")
    private String SqlCommand;

    @NotBlank(message = "SftpAddress is required")
    private String SftpAddress;

    @NotBlank(message = "SftpUsername is required")
    private String SftpUsername;

    @NotBlank(message = "SftpPassword is required")
    private String SftpPassword;

    private Integer SftpPort;
    private String IsAsync;
}