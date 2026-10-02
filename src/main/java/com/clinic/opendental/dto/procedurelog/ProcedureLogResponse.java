package com.clinic.opendental.dto.procedurelog;

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
public class ProcedureLogResponse {

    @JsonAlias("ProcNum") private Long ProcNum;
    @JsonAlias("PatNum") private Long PatNum;
    @JsonAlias("AptNum") private Long AptNum;
    @JsonAlias("ProcDate") private String ProcDate;
    @JsonAlias("ProcFee") private String ProcFee;
    @JsonAlias("Surf") private String Surf;
    @JsonAlias("ToothNum") private String ToothNum;
    @JsonAlias("ToothRange") private String ToothRange;
    @JsonAlias("Priority") private Long Priority;
    @JsonAlias("priority") private String priority;
    @JsonAlias("ProcStatus") private String ProcStatus;
    @JsonAlias("ProvNum") private Long ProvNum;
    @JsonAlias("provAbbr") private String provAbbr;
    @JsonAlias("Dx") private Long Dx;
    @JsonAlias("dxName") private String dxName;
    @JsonAlias("PlannedAptNum") private Long PlannedAptNum;
    @JsonAlias("PlaceService") private String PlaceService;
    @JsonAlias("Prosthesis") private String Prosthesis;
    @JsonAlias("DateOriginalProsth") private String DateOriginalProsth;
    @JsonAlias("ClaimNote") private String ClaimNote;
    @JsonAlias("DateEntryC") private String DateEntryC;
    @JsonAlias("ClinicNum") private Long ClinicNum;
    @JsonAlias("DiagnosticCode") private String DiagnosticCode;
    @JsonAlias("IsPrincDiag") private String IsPrincDiag;
    @JsonAlias("CodeNum") private Long CodeNum;
    @JsonAlias("procCode") private String procCode;
    @JsonAlias("descript") private String descript;
    @JsonAlias("UnitQty") private Integer UnitQty;
    @JsonAlias("BaseUnits") private Integer BaseUnits;
    @JsonAlias("DateTP") private String DateTP;
    @JsonAlias("SiteNum") private Long SiteNum;
    @JsonAlias("HideGraphics") private String HideGraphics;
    @JsonAlias("CanadianTypeCodes") private String CanadianTypeCodes;
    @JsonAlias("ProcTime") private String ProcTime;
    @JsonAlias("ProcTimeEnd") private String ProcTimeEnd;
    @JsonAlias("DateTStamp") private String DateTStamp;
    @JsonAlias("Prognosis") private Long Prognosis;
    @JsonAlias("IsLocked") private String IsLocked;
    @JsonAlias("BillingNote") private String BillingNote;
    @JsonAlias("SnomedBodySite") private String SnomedBodySite;
    @JsonAlias("DiagnosticCode2") private String DiagnosticCode2;
    @JsonAlias("DiagnosticCode3") private String DiagnosticCode3;
    @JsonAlias("DiagnosticCode4") private String DiagnosticCode4;
    @JsonAlias("Discount") private Double Discount;
    @JsonAlias("IsDateProsthEst") private String IsDateProsthEst;
    @JsonAlias("IcdVersion") private Integer IcdVersion;
    @JsonAlias("SecDateEntry") private String SecDateEntry;
    @JsonAlias("DiscountPlanAmt") private Double DiscountPlanAmt;
    @JsonAlias("serverDateTime") private String serverDateTime;
}
