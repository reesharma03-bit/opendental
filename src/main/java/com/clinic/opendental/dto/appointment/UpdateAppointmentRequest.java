package com.clinic.opendental.dto.appointment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateAppointmentRequest {

    private String AptStatus;
    private String Pattern;
    private Long Confirmed;
    private Long Op;
    private String Note;
    private Long ProvNum;
    private Long ProvHyg;
    private String AptDateTime;
    private Long Assistant;
    private Long ClinicNum;
    private String IsHygiene;
    private String DateTimeArrived;
    private String DateTimeSeated;
    private String DateTimeDismissed;
    private String IsNewPatient;
    private String Priority;
    private Long AppointmentTypeNum;
    private Long UnschedStatus;
    private String colorOverride;
    private String PatternSecondary;
    private String IsMirrored;
}