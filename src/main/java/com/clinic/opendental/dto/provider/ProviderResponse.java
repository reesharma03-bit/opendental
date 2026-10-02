package com.clinic.opendental.dto.provider;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProviderResponse {

    @JsonAlias({"ProvNum", "prov_num"}) private Long ProvNum;
    @JsonAlias({"Abbrev", "abbrev"}) private String Abbrev;
    @JsonAlias({"FName", "f_name"}) private String FName;
    @JsonAlias({"LName", "l_name"}) private String LName;
    @JsonAlias({"Suffix", "suffix"}) private String Suffix;
    @JsonAlias({"Specialty", "specialty"}) private String Specialty;
    @JsonAlias({"ProvStatus", "prov_status"}) private String ProvStatus;
    @JsonAlias({"ProvType", "prov_type"}) private String ProvType;
    @JsonAlias({"ClinicNum", "clinic_num"}) private Long ClinicNum;
    @JsonAlias({"serverDateTime", "server_date_time"}) private String serverDateTime;
}