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
        String birthdate,
        String email,
        String wirelessPhone,
        String hmPhone,
        String city,
        String state
) {
}