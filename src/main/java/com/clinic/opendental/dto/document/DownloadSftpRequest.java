package com.clinic.opendental.dto.document;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DownloadSftpRequest {

    private Long DocNum;
    private Long MountNum;

    @NotBlank(message = "SftpAddress is required")
    private String SftpAddress;

    @NotBlank(message = "SftpUsername is required")
    private String SftpUsername;

    @NotBlank(message = "SftpPassword is required")
    private String SftpPassword;

    private Integer SftpPort;
}