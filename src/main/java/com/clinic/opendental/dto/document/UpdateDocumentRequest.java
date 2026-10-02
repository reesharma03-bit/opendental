package com.clinic.opendental.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateDocumentRequest {

    private String Description;
    private String DateCreated;
    private Long DocCategory;
    private String ImgType;
    private String ToothNumbers;
    private Long ProvNum;
    private String PrintHeading;
}