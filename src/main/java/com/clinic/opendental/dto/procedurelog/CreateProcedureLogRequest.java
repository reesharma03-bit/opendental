package com.clinic.opendental.dto.procedurelog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateProcedureLogRequest {

    @NotNull(message = "PatNum is required")
    private Long PatNum;

    @NotBlank(message = "ProcDate is required")
    private String ProcDate;

    @NotBlank(message = "ProcStatus is required")
    private String ProcStatus;

    private Long CodeNum;
    private String procCode;

    private Long AptNum;
    private String ProcFee;
    private Long Priority;
    private String priority;
    private Long ProvNum;
    private Long Dx;
    private String dxName;
    private Long PlannedAptNum;
    private String PlaceService;
    private String Prosthesis;
    private String DateOriginalProsth;
    private String ClaimNote;
    private Long ClinicNum;
    private String DiagnosticCode;
    private String IsPrincDiag;
    private String DateTP;
    private Long SiteNum;
    private String ProcTime;
    private String ProcTimeEnd;
    private Long Prognosis;
    private String ToothNum;
    private String Surf;
    private String ToothRange;
    private String BillingNote;
    private String SnomedBodySite;
    private String DiagnosticCode2;
    private String DiagnosticCode3;
    private String DiagnosticCode4;
    private Double Discount;
    private String IsDateProsthEst;
    private Integer IcdVersion;
}