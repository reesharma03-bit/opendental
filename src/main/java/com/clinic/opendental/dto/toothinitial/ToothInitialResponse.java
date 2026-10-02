package com.clinic.opendental.dto.toothinitial;

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
public class ToothInitialResponse {

    @JsonAlias({"ToothInitialNum", "tooth_initial_num"}) private Long ToothInitialNum;
    @JsonAlias({"PatNum", "pat_num"}) private Long PatNum;
    @JsonAlias({"ToothNum", "tooth_num"}) private String ToothNum;
    @JsonAlias({"ToothType", "tooth_type"}) private String ToothType;
    @JsonAlias({"ToothGroup", "tooth_group"}) private String ToothGroup;
    @JsonAlias({"Mobility", "mobility"}) private String Mobility;
    @JsonAlias({"DateTStamp", "date_t_stamp"}) private String DateTStamp;
    @JsonAlias({"serverDateTime", "server_date_time"}) private String serverDateTime;
}