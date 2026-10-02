package com.clinic.opendental.service;

import com.clinic.opendental.dto.patient.CreatePatientRequest;
import com.clinic.opendental.dto.patient.PatientResponse;
import com.clinic.opendental.dto.patient.PatientSimpleResponse;
import com.clinic.opendental.dto.patient.UpdatePatientRequest;

import java.util.List;
import java.util.Map;

public interface PatientService {

    List<PatientResponse> getPatients(Map<String, String> params);

    List<PatientSimpleResponse> getSimplePatients(Map<String, String> params);

    PatientResponse getPatient(Long patNum);

    PatientResponse createPatient(CreatePatientRequest request);

    PatientResponse updatePatient(Long patNum, UpdatePatientRequest request);
}