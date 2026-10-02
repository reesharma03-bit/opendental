package com.clinic.opendental.dto.patient;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientSearchRequest {

    private String LName;
    private String FName;
    private String Phone;
    private String Address;
    private Boolean hideInactive;
    private String City;
    private String State;
    private String SSN;
    private String ChartNumber;
    private Boolean guarOnly;
    private Boolean showArchived;
    private String Birthdate;
    private Long SiteNum;
    private String SubscriberId;
    private String Email;
    private String Country;
    private String clinicNums;
    private String clinicAbbr;
    private String invoiceNumber;
    private Integer Offset;
}