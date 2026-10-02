package com.clinic.opendental.dto.schedule;

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
public class ScheduleResponse {

    @JsonAlias({"ScheduleNum", "schedule_num"}) private Long ScheduleNum;
    @JsonAlias({"SchedDate", "sched_date"}) private String SchedDate;
    @JsonAlias({"SchedTypeNum", "sched_type_num"}) private Long SchedTypeNum;
    @JsonAlias({"ProvNum", "prov_num"}) private Long ProvNum;
    @JsonAlias({"ClinicNum", "clinic_num"}) private Long ClinicNum;
    @JsonAlias({"StartTime", "start_time"}) private String StartTime;
    @JsonAlias({"StopTime", "stop_time"}) private String StopTime;
    @JsonAlias({"Blockout", "blockout"}) private String Blockout;
    @JsonAlias({"serverDateTime", "server_date_time"}) private String serverDateTime;
}