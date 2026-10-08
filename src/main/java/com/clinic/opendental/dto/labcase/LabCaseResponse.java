package com.clinic.opendental.dto.labcase;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A lab case as Open Dental sends it (GET /labcases and the LabCase webhook). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class LabCaseResponse {
    @JsonAlias({"LabCaseNum", "lab_case_num"}) private Long LabCaseNum;
    @JsonAlias({"PatNum", "pat_num"}) private Long PatNum;
    @JsonAlias({"LaboratoryNum", "laboratory_num"}) private Long LaboratoryNum;
    @JsonAlias({"AptNum", "apt_num"}) private Long AptNum;
    @JsonAlias({"PlannedAptNum", "planned_apt_num"}) private Long PlannedAptNum;
    @JsonAlias({"DateTimeDue", "date_time_due"}) private String DateTimeDue;
    @JsonAlias({"DateTimeCreated", "date_time_created"}) private String DateTimeCreated;
    @JsonAlias({"DateTimeSent", "date_time_sent"}) private String DateTimeSent;
    @JsonAlias({"DateTimeRecd", "date_time_recd"}) private String DateTimeRecd;
    @JsonAlias({"DateTimeChecked", "date_time_checked"}) private String DateTimeChecked;
    @JsonAlias({"ProvNum", "prov_num"}) private Long ProvNum;
    @JsonAlias({"Instructions", "instructions"}) private String Instructions;
    @JsonAlias({"LabFee", "lab_fee"}) private Double LabFee;
    @JsonAlias({"DateTStamp", "date_t_stamp"}) private String DateTStamp;
    @JsonAlias({"InvoiceNum", "invoice_num"}) private String InvoiceNum;
}
