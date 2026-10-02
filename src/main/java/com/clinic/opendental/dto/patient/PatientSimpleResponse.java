package com.clinic.opendental.dto.patient;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientSimpleResponse {

    private String serverDateTime;
    private Long PatNum;
    private String LName;
    private String FName;
    private String MiddleI;
    private String Preferred;
    private String PatStatus;
    private String Gender;
    private String Position;
    private String Birthdate;
    private String SSN;
    private String Address;
    private String Address2;
    private String City;
    private String State;
    private String Zip;
    private String HmPhone;
    private String WkPhone;
    private String WirelessPhone;
    private Long Guarantor;
    private String Email;
    private Double EstBalance;
    private Long PriProv;
    private String priProvAbbr;
    private Long SecProv;
    private String secProvAbbr;
    private Long FeeSched;
    private String BillingType;
    private String ImageFolder;
    private String FamFinUrgNote;
    private String MedUrgNote;
    private String ApptModNote;
    private String ChartNumber;
    private String MedicaidID;
    private Double Bal_0_30;
    private Double Bal_31_60;
    private Double Bal_61_90;
    private Double BalOver90;
    private Double InsEst;
    private Double BalTotal;
    private String dateTimeLastAging;
    private Long EmployerNum;
    private String DateFirstVisit;
    private Long ClinicNum;
    private String clinicAbbr;
    private String HasIns;
    @Builder.Default
    private String Premed = "false";
    private String Ward;
    private String PreferConfirmMethod;
    private String PreferContactMethod;
    private String PreferRecallMethod;
    private String Language;
    private String AdmitDate;
    private Long SiteNum;
    private String siteDesc;
    private String DateTStamp;
    private Long SuperFamily;
    private String TxtMsgOk;
    private Long SecUserNumEntry;
    private String SecDateEntry;
}