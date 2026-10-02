package com.clinic.opendental.dto.document;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentResponse {

    @JsonAlias("DocNum") private Long DocNum;
    @JsonAlias("MountNum") private Long MountNum;
    @JsonAlias("filePath") private String filePath;
    @JsonAlias("Description") private String Description;
    @JsonAlias("PatNum") private String PatNum;
    @JsonAlias("Note") private String Note;
    @JsonAlias("DateCreated") private String DateCreated;
    @JsonAlias("docCategory") private String docCategory;
    @JsonAlias("DocCategory") private Long DocCategory;
    @JsonAlias("FileName") private String FileName;
    @JsonAlias("ImgType") private String ImgType;
    @JsonAlias("ToothNumbers") private String ToothNumbers;
    @JsonAlias("DateTStamp") private String DateTStamp;
    @JsonAlias("ProvNum") private Long ProvNum;
    @JsonAlias("PrintHeading") private String PrintHeading;
    @JsonAlias("serverDateTime") private String serverDateTime;
}
