package com.clinic.opendental.dto.medicationpat;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A patient's medication as Open Dental sends it (GET /medicationpats and the MedicationPat webhook). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class MedicationPatResponse {
    @JsonAlias({"MedicationPatNum", "medication_pat_num"}) private Long MedicationPatNum;
    /** Open Dental's example sends this as a string ("234"); Jackson reads either. */
    @JsonAlias({"PatNum", "pat_num"}) private Long PatNum;
    @JsonAlias({"medName", "MedName", "med_name"}) private String medName;
    @JsonAlias({"MedicationNum", "medication_num"}) private Long MedicationNum;
    @JsonAlias({"PatNote", "pat_note"}) private String PatNote;
    @JsonAlias({"DateStart", "date_start"}) private String DateStart;
    @JsonAlias({"DateStop", "date_stop"}) private String DateStop;
    @JsonAlias({"ProvNum", "prov_num"}) private Long ProvNum;
}
