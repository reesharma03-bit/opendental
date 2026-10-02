package com.clinic.opendental.dto.appointment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlotResponse {

    private String DateTimeStart;
    private String DateTimeEnd;
    private Long ProvNum;
    private Long OpNum;
}