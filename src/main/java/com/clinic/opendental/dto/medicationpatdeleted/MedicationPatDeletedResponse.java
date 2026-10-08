package com.clinic.opendental.dto.medicationpatdeleted;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A patient's medication removed in Open Dental (MedicationPatDeleted webhook). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class MedicationPatDeletedResponse {
    @JsonAlias({"MedicationPatNum", "medication_pat_num"}) private Long MedicationPatNum;
    @JsonAlias({"PatNum", "pat_num"}) private Long PatNum;
    @JsonAlias({"DateTimeDeleted", "date_time_deleted"}) private String DateTimeDeleted;
    @JsonAlias({"DeletedBy", "deleted_by"}) private Long DeletedBy;
}
