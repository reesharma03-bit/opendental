package com.clinic.opendental.dto.appointmentdeleted;

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
public class AppointmentDeletedResponse {

    @JsonAlias({"AppointmentNum", "appointment_num"}) private Long AppointmentNum;
    @JsonAlias({"PatNum", "pat_num"}) private Long PatNum;
    @JsonAlias({"AptDateTime", "apt_date_time"}) private String AptDateTime;
    @JsonAlias({"DateTimeDeleted", "date_time_deleted"}) private String DateTimeDeleted;
    @JsonAlias({"DeletedBy", "deleted_by"}) private Long DeletedBy;
    @JsonAlias({"Note", "note"}) private String Note;
    @JsonAlias({"ClinicNum", "clinic_num"}) private Long ClinicNum;
    @JsonAlias({"serverDateTime", "server_date_time"}) private String serverDateTime;
}