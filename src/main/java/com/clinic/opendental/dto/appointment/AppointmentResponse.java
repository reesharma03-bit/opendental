package com.clinic.opendental.dto.appointment;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Appointment record mapper.
 *
 * <p>The app uses a global {@code SNAKE_CASE} Jackson naming strategy for its
 * REST API, but Open Dental returns <b>PascalCase</b> keys ({@code AptNum},
 * {@code PatNum}, ...). Each field therefore declares {@link JsonAlias} with the
 * PascalCase name so the response maps correctly during deserialization, and
 * {@link JsonIgnoreProperties} tolerates any keys we do not model yet.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppointmentResponse {

    @JsonAlias({"serverDateTime", "ServerDateTime"})
    private String serverDateTime;

    @JsonAlias("AptNum") private Long AptNum;
    @JsonAlias("PatNum") private Long PatNum;
    @JsonAlias("AptStatus") private String AptStatus;
        @JsonAlias("Pattern") private String Pattern;
    // Open Dental emits TWO keys for this field: "Confirmed":19 (number) AND
    // "confirmed":"Unconfirmed" (string). Under the global SNAKE_CASE strategy the
    // Java field `Confirmed` derives the JSON property name "confirmed", so a plain
    // @JsonAlias would let the lowercase String collide into the Long and throw
    // "Cannot deserialize value of type java.lang.Long from String \"Unconfirmed\"".
    // @JsonProperty pins the exact PascalCase name; the lowercase dup is then unknown->ignored.
    @JsonProperty("Confirmed")
    private Long Confirmed;
    @JsonAlias("TimeLocked") private String TimeLocked;
    @JsonAlias("Op") private Long Op;
    @JsonAlias("Note") private String Note;
    @JsonAlias("ProvNum") private Long ProvNum;
    @JsonAlias({"provAbbr", "ProvAbbr"}) private String provAbbr;
    @JsonAlias("ProvHyg") private Long ProvHyg;
    @JsonAlias("AptDateTime") private String AptDateTime;
    @JsonAlias("NextAptNum") private Long NextAptNum;
    @JsonAlias("UnschedStatus") private Long UnschedStatus;
    @JsonAlias("IsNewPatient") private String IsNewPatient;
    @JsonAlias("ProcDescript") private String ProcDescript;
    @JsonAlias("Assistant") private Long Assistant;
    @JsonAlias("ClinicNum") private Long ClinicNum;
    @JsonAlias("IsHygiene") private String IsHygiene;
    @JsonAlias("DateTStamp") private String DateTStamp;
    @JsonAlias("DateTimeArrived") private String DateTimeArrived;
    @JsonAlias("DateTimeSeated") private String DateTimeSeated;
    @JsonAlias("DateTimeDismissed") private String DateTimeDismissed;
    @JsonAlias("InsPlan1") private Long InsPlan1;
    @JsonAlias("InsPlan2") private Long InsPlan2;
    @JsonAlias("DateTimeAskedToArrive") private String DateTimeAskedToArrive;
    @JsonAlias({"colorOverride", "ColorOverride"}) private String colorOverride;
    @JsonAlias("AppointmentTypeNum") private Long AppointmentTypeNum;
    @JsonAlias("SecUserNumEntry") private Long SecUserNumEntry;
    @JsonAlias("SecDateTEntry") private String SecDateTEntry;
    @JsonAlias("Priority") private String Priority;
    @JsonAlias("PatternSecondary") private String PatternSecondary;
    @JsonAlias("ItemOrderPlanned") private Long ItemOrderPlanned;
    @JsonAlias("IsMirrored") private String IsMirrored;
    @JsonAlias({"eServiceLogType", "EServiceLogType"}) private String eServiceLogType;
}
