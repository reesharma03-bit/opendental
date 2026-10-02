package com.clinic.opendental.dto.patient;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Patient record mapper.
 *
 * <p>The app uses a global {@code SNAKE_CASE} Jackson naming strategy for its
 * REST API, but the Open Dental webhook sends <b>PascalCase</b> keys
 * ({@code PatNum}, {@code LName}, ...). Open Dental's query endpoints, on the
 * other hand, return <b>snake_case</b> keys ({@code pat_num}, {@code l_name},
 * ...) — the captured {@code opendental-appointments-sample.json} confirms the
 * list-style endpoints deserialize snake_case. Each field therefore declares a
 * {@link JsonAlias} accepting <em>both</em> the PascalCase and the snake_case
 * name so the record maps correctly during deserialization regardless of which
 * casing Open Dental uses, while our own REST API responses keep snake_case.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class PatientResponse {

    @JsonAlias({"PatNum", "pat_num"}) private Long PatNum;
    @JsonAlias({"LName", "l_name"}) private String LName;
    @JsonAlias({"FName", "f_name"}) private String FName;
    @JsonAlias({"MiddleI", "middle_i"}) private String MiddleI;
    @JsonAlias({"Preferred", "preferred"}) private String Preferred;
    @JsonAlias({"PatStatus", "pat_status"}) private String PatStatus;
    @JsonAlias({"Gender", "gender"}) private String Gender;
    @JsonAlias({"Position", "position"}) private String Position;
    @JsonAlias({"Birthdate", "birthdate"}) private String Birthdate;
    @JsonAlias({"SSN", "ssn"}) private String SSN;
    @JsonAlias({"Address", "address"}) private String Address;
    @JsonAlias({"Address2", "address2"}) private String Address2;
    @JsonAlias({"City", "city"}) private String City;
    @JsonAlias({"State", "state"}) private String State;
    @JsonAlias({"Zip", "zip"}) private String Zip;
    @JsonAlias({"HmPhone", "hm_phone"}) private String HmPhone;
    @JsonAlias({"WkPhone", "wk_phone"}) private String WkPhone;
    @JsonAlias({"WirelessPhone", "wireless_phone"}) private String WirelessPhone;
    @JsonAlias({"Guarantor", "guarantor"}) private Long Guarantor;
    @JsonAlias({"Email", "email"}) private String Email;
    @JsonAlias({"EstBalance", "est_balance"}) private Double EstBalance;
    @JsonAlias({"PriProv", "pri_prov"}) private Long PriProv;
    @JsonAlias({"priProvAbbr", "pri_prov_abbr"}) private String priProvAbbr;
    @JsonAlias({"SecProv", "sec_prov"}) private Long SecProv;
    @JsonAlias({"secProvAbbr", "sec_prov_abbr"}) private String secProvAbbr;
    @JsonAlias({"FeeSched", "fee_sched"}) private Long FeeSched;
    @JsonAlias({"BillingType", "billing_type"}) private String BillingType;
    @JsonAlias({"ImageFolder", "image_folder"}) private String ImageFolder;
    @JsonAlias({"FamFinUrgNote", "fam_fin_urg_note"}) private String FamFinUrgNote;
    @JsonAlias({"MedUrgNote", "med_urg_note"}) private String MedUrgNote;
    @JsonAlias({"ApptModNote", "appt_mod_note"}) private String ApptModNote;
    @JsonAlias({"ChartNumber", "chart_number"}) private String ChartNumber;
    @JsonAlias({"MedicaidID", "medicaid_id"}) private String MedicaidID;
    @JsonAlias({"Bal_0_30", "bal_0_30"}) private Double Bal_0_30;
    @JsonAlias({"Bal_31_60", "bal_31_60"}) private Double Bal_31_60;
    @JsonAlias({"Bal_61_90", "bal_61_90"}) private Double Bal_61_90;
    @JsonAlias({"BalOver90", "bal_over_90"}) private Double BalOver90;
    @JsonAlias({"InsEst", "ins_est"}) private Double InsEst;
    @JsonAlias({"BalTotal", "bal_total"}) private Double BalTotal;
    @JsonAlias({"dateTimeLastAging", "date_time_last_aging"}) private String dateTimeLastAging;
    @JsonAlias({"EmployerNum", "employer_num"}) private Long EmployerNum;
    @JsonAlias({"DateFirstVisit", "date_first_visit"}) private String DateFirstVisit;
    @JsonAlias({"ClinicNum", "clinic_num"}) private Long ClinicNum;
    @JsonAlias({"clinicAbbr", "clinic_abbr"}) private String clinicAbbr;
    @JsonAlias({"HasIns", "has_ins"}) private String HasIns;
    @JsonAlias({"Premed", "premed"}) @Builder.Default private String Premed = "false";
    @JsonAlias({"Ward", "ward"}) private String Ward;
    @JsonAlias({"PreferConfirmMethod", "prefer_confirm_method"}) private String PreferConfirmMethod;
    @JsonAlias({"PreferContactMethod", "prefer_contact_method"}) private String PreferContactMethod;
    @JsonAlias({"PreferRecallMethod", "prefer_recall_method"}) private String PreferRecallMethod;
    @JsonAlias({"Language", "language"}) private String Language;
    @JsonAlias({"AdmitDate", "admit_date"}) private String AdmitDate;
    @JsonAlias({"SiteNum", "site_num"}) private Long SiteNum;
    @JsonAlias({"siteDesc", "site_desc"}) private String siteDesc;
    @JsonAlias({"DateTStamp", "date_t_stamp"}) private String DateTStamp;
    @JsonAlias({"SuperFamily", "super_family"}) private Long SuperFamily;
    @JsonAlias({"TxtMsgOk", "txt_msg_ok"}) private String TxtMsgOk;
    @JsonAlias({"SecUserNumEntry", "sec_user_num_entry"}) private Long SecUserNumEntry;
    @JsonAlias({"SecDateEntry", "sec_date_entry"}) private String SecDateEntry;
}
