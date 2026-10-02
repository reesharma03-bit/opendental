package com.clinic.opendental.dto.patfielddeleted;

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
public class PatFieldDeletedResponse {

    @JsonAlias({"PatFieldNum", "pat_field_num"}) private Long PatFieldNum;
    @JsonAlias({"PatNum", "pat_num"}) private Long PatNum;
    @JsonAlias({"DateTimeDeleted", "date_time_deleted"}) private String DateTimeDeleted;
    @JsonAlias({"DeletedBy", "deleted_by"}) private Long DeletedBy;
    @JsonAlias({"ClinicNum", "clinic_num"}) private Long ClinicNum;
    @JsonAlias({"serverDateTime", "server_date_time"}) private String serverDateTime;
}