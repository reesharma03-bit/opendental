package com.clinic.opendental.dto.procedurelog;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupNoteResponse {

    private Long PatNum;
    private Long ProvNum;
    private Long ProcNum;
    private List<Long> ProcNums;
    private String Note;
    private String isSigned;
}