package com.clinic.opendental.dto.labcasedeleted;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A lab case deleted in Open Dental (LabCaseDeleted webhook). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class LabCaseDeletedResponse {
    @JsonAlias({"LabCaseNum", "lab_case_num"}) private Long LabCaseNum;
    @JsonAlias({"PatNum", "pat_num"}) private Long PatNum;
    @JsonAlias({"DateTimeDeleted", "date_time_deleted"}) private String DateTimeDeleted;
    @JsonAlias({"DeletedBy", "deleted_by"}) private Long DeletedBy;
    @JsonAlias({"ClinicNum", "clinic_num"}) private Long ClinicNum;
    @JsonAlias({"serverDateTime", "server_date_time"}) private String serverDateTime;
}
