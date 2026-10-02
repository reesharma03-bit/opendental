package com.clinic.opendental.dto.appointment;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlannedAppointmentRequest {

    @NotNull(message = "PatNum is required")
    private Long PatNum;

    private Long AppointmentTypeNum;
    private List<Long> procNums;
    private String Pattern;
    private Long Confirmed;
    private String Note;
    private Long ProvNum;
    private Long ProvHyg;
    private Long ClinicNum;
    private String IsHygiene;
    private String IsNewPatient;
    private String Priority;
    private String PatternSecondary;
}