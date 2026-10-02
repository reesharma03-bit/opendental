package com.clinic.opendental.dto.toothinitialdeleted;

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
public class ToothInitialDeletedResponse {

    @JsonAlias({"ToothInitialNum", "tooth_initial_num"}) private Long ToothInitialNum;
    @JsonAlias({"PatNum", "pat_num"}) private Long PatNum;
    @JsonAlias({"ToothNum", "tooth_num"}) private String ToothNum;
    @JsonAlias({"DateTimeDeleted", "date_time_deleted"}) private String DateTimeDeleted;
    @JsonAlias({"DeletedBy", "deleted_by"}) private Long DeletedBy;
    @JsonAlias({"serverDateTime", "server_date_time"}) private String serverDateTime;
}