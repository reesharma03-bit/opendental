package com.clinic.opendental.dto.scheduledeleted;

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
public class ScheduleDeletedResponse {

    @JsonAlias({"ScheduleNum", "schedule_num"}) private Long ScheduleNum;
    @JsonAlias({"SchedDate", "sched_date"}) private String SchedDate;
    @JsonAlias({"SchedTypeNum", "sched_type_num"}) private Long SchedTypeNum;
    @JsonAlias({"ProvNum", "prov_num"}) private Long ProvNum;
    @JsonAlias({"ClinicNum", "clinic_num"}) private Long ClinicNum;
    @JsonAlias({"DateTimeDeleted", "date_time_deleted"}) private String DateTimeDeleted;
    @JsonAlias({"DeletedBy", "deleted_by"}) private Long DeletedBy;
    @JsonAlias({"serverDateTime", "server_date_time"}) private String serverDateTime;
}