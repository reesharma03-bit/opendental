package com.clinic.opendental.dto.appointment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateAppointmentRequest {

    @NotNull(message = "PatNum is required")
    private Long PatNum;

    @NotNull(message = "Op is required")
    private Long Op;

    @NotBlank(message = "AptDateTime is required")
    private String AptDateTime;

    private String AptStatus;
    private String Pattern;
    private Long Confirmed;
    private String Note;
    private Long ProvNum;
    private Long ProvHyg;
    private Long Assistant;
    private Long ClinicNum;
    private String IsHygiene;
    private String DateTimeArrived;
    private String DateTimeSeated;
    private String DateTimeDismissed;
    private String IsNewPatient;
    private String Priority;
    private Long AppointmentTypeNum;
    private Long SecUserNumEntry;
    private String colorOverride;
    private String PatternSecondary;
    private String IsMirrored;
}