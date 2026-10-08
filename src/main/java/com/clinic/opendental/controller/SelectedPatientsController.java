package com.clinic.opendental.controller;

import com.clinic.opendental.repository.ClinicRepository;
import com.clinic.opendental.service.SelectedPatients;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * GET /api/patients/selected: which patient is open in Open Dental on each workstation
 * (from Open Dental's PatientSelected UI event), newest first. Needs Patients access.
 */
@RestController
@RequestMapping("/api/patients/selected")
@RequiredArgsConstructor
public class SelectedPatientsController {

    private final SelectedPatients selectedPatients;
    private final ClinicRepository clinicRepository;

    @GetMapping
    public List<Map<String, Object>> selected() {
        return clinicRepository.findByIsActiveTrue().stream().findFirst()
                .map(clinic -> selectedPatients.recent(clinic.getId()).stream()
                        .map(s -> Map.<String, Object>of(
                                "workstation", s.workstation(), "pat_num", s.patNum(), "name", s.name(), "at", s.at().toString()))
                        .toList())
                .orElse(List.of());
    }
}
