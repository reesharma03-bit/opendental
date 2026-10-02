package com.clinic.opendental.dto.patient;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePatientRequest {

    @JsonAlias({"LName", "l_name"})
    private String LName;
    @JsonAlias({"FName", "f_name"})
    private String FName;
    @JsonAlias({"MiddleI", "middle_i"})
    private String MiddleI;
    @JsonAlias({"Preferred"})
    private String Preferred;
    @JsonAlias({"PatStatus", "pat_status"})
    private String PatStatus;
    @JsonAlias({"Gender"})
    private String Gender;
    @JsonAlias({"Position"})
    private String Position;
    @JsonAlias({"Birthdate"})
    private String Birthdate;
    @JsonAlias({"SSN"})
    private String SSN;
    @JsonAlias({"Address"})
    private String Address;
    @JsonAlias({"Address2"})
    private String Address2;
    @JsonAlias({"City"})
    private String City;
    @JsonAlias({"State"})
    private String State;
    @JsonAlias({"Zip"})
    private String Zip;
    @JsonAlias({"HmPhone", "hm_phone", "hmphone"})
    private String HmPhone;
    @JsonAlias({"WkPhone", "wk_phone", "wkphone"})
    private String WkPhone;
    @JsonAlias({"WirelessPhone", "wireless_phone", "wirelessphone"})
    private String WirelessPhone;
    @JsonAlias({"Guarantor"})
    private Long Guarantor;
    @JsonAlias({"Email"})
    private String Email;
    @JsonAlias({"PriProv", "pri_prov", "priprov"})
    private Long PriProv;
    @JsonAlias({"SecProv", "sec_prov", "secprov"})
    private Long SecProv;
    @JsonAlias({"FeeSched", "fee_sched", "feesched"})
    private Long FeeSched;
    @JsonAlias({"BillingType", "billing_type", "billingtype"})
    private String BillingType;
    @JsonAlias({"FamFinUrgNote", "fam_fin_urg_note", "famfinurgnote"})
    private String FamFinUrgNote;
    @JsonAlias({"MedUrgNote", "med_urg_note", "medurgnote"})
    private String MedUrgNote;
    @JsonAlias({"ApptModNote", "appt_mod_note", "apptmodnote"})
    private String ApptModNote;
    @JsonAlias({"ChartNumber", "chart_number", "chartnumber"})
    private String ChartNumber;
    @JsonAlias({"MedicaidID", "medicaid_id", "medicaidid"})
    private String MedicaidID;
    @JsonAlias({"EmployerNum", "employer_num", "employernum"})
    private Long EmployerNum;
    @JsonAlias({"DateFirstVisit", "date_first_visit", "datefirstvisit"})
    private String DateFirstVisit;
    @JsonAlias({"ClinicNum", "clinic_num", "clinicnum"})
    private Long ClinicNum;
    @Builder.Default
    @JsonAlias({"Premed"})
    private Boolean Premed = false;
    @JsonAlias({"Ward"})
    private String Ward;
    @JsonAlias({"PreferConfirmMethod", "prefer_confirm_method", "preferconfirmmethod"})
    private String PreferConfirmMethod;
    @JsonAlias({"PreferContactMethod", "prefer_contact_method", "prefercontactmethod"})
    private String PreferContactMethod;
    @JsonAlias({"PreferRecallMethod", "prefer_recall_method", "preferrecallmethod"})
    private String PreferRecallMethod;
    @JsonAlias({"Language"})
    private String Language;
    @JsonAlias({"AdmitDate", "admit_date", "admitdate"})
    private String AdmitDate;
    @JsonAlias({"SuperFamily", "super_family", "superfamily"})
    private Long SuperFamily;
    @JsonAlias({"TxtMsgOk", "txt_msg_ok", "txtmsgok"})
    private String TxtMsgOk;
}