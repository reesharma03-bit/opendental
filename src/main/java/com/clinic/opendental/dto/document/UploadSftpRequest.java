package com.clinic.opendental.dto.document;

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
public class UploadSftpRequest {

    @NotNull(message = "PatNum is required")
    private Long PatNum;

    @NotBlank(message = "SftpAddress is required")
    private String SftpAddress;

    @NotBlank(message = "SftpUsername is required")
    private String SftpUsername;

    @NotBlank(message = "SftpPassword is required")
    private String SftpPassword;

    private Integer SftpPort;
    private String Description;
    private String DateCreated;
    private Long DocCategory;
    private String ImgType;
    private String ToothNumbers;
    private Long ProvNum;
    private String PrintHeading;
}