package com.clinic.opendental.dto.patfield;

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
public class PatFieldResponse {

    @JsonAlias({"PatFieldNum", "pat_field_num"}) private Long PatFieldNum;
    @JsonAlias({"PatNum", "pat_num"}) private Long PatNum;
    @JsonAlias({"FieldName", "field_name"}) private String FieldName;
    @JsonAlias({"FieldValue", "field_value"}) private String FieldValue;
    @JsonAlias({"FieldDesc", "field_desc"}) private String FieldDesc;
    @JsonAlias({"FieldType", "field_type"}) private String FieldType;
    @JsonAlias({"ClinicNum", "clinic_num"}) private Long ClinicNum;
    @JsonAlias({"serverDateTime", "server_date_time"}) private String serverDateTime;
}