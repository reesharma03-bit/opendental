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
public class UploadDocumentRequest {

    @NotNull(message = "PatNum is required")
    private Long PatNum;

    @NotBlank(message = "rawBase64 is required")
    private String rawBase64;

    @NotBlank(message = "extension is required")
    private String extension;

    private String Description;
    private String DateCreated;
    private Long DocCategory;
    private String ImgType;
    private String ToothNumbers;
    private Long ProvNum;
    private String PrintHeading;
}