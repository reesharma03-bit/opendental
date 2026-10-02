package com.clinic.opendental.dto.patient;

public record PatientDirectoryResponse(
        String recordKey,
        Long patNum,
        String clinicAbbr,
        String fName,
        String lName,
        String middleI,
        String preferred,
        String patStatus,
        String gender,
        String position,
        String birthdate,
        String email,
        String wirelessPhone,
        String hmPhone,
        String wkPhone,
        String txtMsgOk,
        String city,
        String state
) {
}