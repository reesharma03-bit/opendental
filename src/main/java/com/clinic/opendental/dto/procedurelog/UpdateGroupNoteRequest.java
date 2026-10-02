package com.clinic.opendental.dto.procedurelog;

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
public class UpdateGroupNoteRequest {

    @NotNull(message = "PatNum is required")
    private Long PatNum;

    @NotBlank(message = "Note is required")
    private String Note;

    private String doAppendNote;
    private String isSigned;
    private Long ProvNum;
}