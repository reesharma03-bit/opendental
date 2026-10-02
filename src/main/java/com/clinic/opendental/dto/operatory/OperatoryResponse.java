package com.clinic.opendental.dto.operatory;

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
public class OperatoryResponse {

    @JsonAlias({"OperatoryNum", "operatory_num"}) private Long OperatoryNum;
    @JsonAlias({"Abbrev", "abbrev"}) private String Abbrev;
    @JsonAlias({"Description", "description"}) private String Description;
    @JsonAlias({"ClinicNum", "clinic_num"}) private Long ClinicNum;
    @JsonAlias({"IsHygiene", "is_hygiene"}) private String IsHygiene;
    @JsonAlias({"IsDisabled", "is_disabled"}) private String IsDisabled;
    @JsonAlias({"IsWebSched", "is_web_sched"}) private String IsWebSched;
    @JsonAlias({"OrderValue", "order_value"}) private Long OrderValue;
    @JsonAlias({"serverDateTime", "server_date_time"}) private String serverDateTime;
}