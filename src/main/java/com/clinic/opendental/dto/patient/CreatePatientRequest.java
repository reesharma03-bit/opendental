package com.clinic.opendental.dto.patient;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePatientRequest {

    @NotBlank(message = "LName is required")
    private String LName;

    @NotBlank(message = "FName is required")
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
    private Long PriProv;
    private Long SecProv;
    private Long FeeSched;
    private String BillingType;
    private String FamFinUrgNote;
    private String MedUrgNote;
    private String ApptModNote;
    private String ChartNumber;
    private String MedicaidID;
    private Long EmployerNum;
    private String DateFirstVisit;
    private Long ClinicNum;
    @Builder.Default
    private Boolean Premed = false;
    private String Ward;
    private String PreferConfirmMethod;
    private String PreferContactMethod;
    private String PreferRecallMethod;
    private String Language;
    private String AdmitDate;
    private Long SuperFamily;
    private String TxtMsgOk;
}