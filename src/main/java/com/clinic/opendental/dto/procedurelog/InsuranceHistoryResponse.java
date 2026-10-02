package com.clinic.opendental.dto.procedurelog;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InsuranceHistoryResponse {

    private String insHistPrefName;
    private String procDate;
    private Long ProcNum;
}