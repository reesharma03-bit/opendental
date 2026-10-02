package com.clinic.opendental.service.Impl;

import com.clinic.opendental.client.OpenDentalClient;
import com.clinic.opendental.dto.patient.CreatePatientRequest;
import com.clinic.opendental.dto.patient.PatientResponse;
import com.clinic.opendental.dto.patient.PatientDirectoryResponse;
import com.clinic.opendental.dto.patient.PatientSimpleResponse;
import com.clinic.opendental.dto.patient.UpdatePatientRequest;
import com.clinic.opendental.exception.ApiException;
import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.model.Patient;
import com.clinic.opendental.model.PatientId;
import com.clinic.opendental.repository.ClinicRepository;
import com.clinic.opendental.repository.PatientRepository;
import com.clinic.opendental.service.PatientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatientServiceImpl implements PatientService {

    private final OpenDentalClient client;
    private final PatientRepository patientRepository;
    private final ClinicRepository clinicRepository;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    @Transactional(readOnly = true)
    public List<PatientResponse> getPatients(Map<String, String> params) {
        // Try to fetch from OpenDental API and sync to database
        try {
            List<PatientResponse> apiPatients = client.getPatients(params);
            // Sync to database asynchronously
            syncPatientsToDb(apiPatients);
            return apiPatients;
        } catch (Exception e) {
            log.warn("OpenDental API unavailable, falling back to database: {}", e.getMessage());
            // Fallback to database
            return patientRepository.findAll().stream()
                    .map(this::toPatientResponse)
                    .filter(p -> matchesSearchParams(p, params))
                    .collect(Collectors.toList());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<PatientDirectoryResponse> getPatientDirectoryFromDatabase(Map<String, String> params) {
        String search = param(params, "Search");
        String needle = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return patientRepository.findAll().stream()
                .filter(patient -> matchesSearchParams(toPatientResponse(patient), params))
                .filter(patient -> needle.isEmpty()
                        || Stream.of(
                                patient.getFName(),
                                patient.getLName(),
                                patient.getPreferred(),
                                patient.getEmail(),
                                patient.getWirelessPhone(),
                                patient.getHmPhone(),
                                String.valueOf(patient.getId().getPatNum()))
                        .filter(value -> value != null && !value.isBlank())
                        .anyMatch(value -> value.toLowerCase(Locale.ROOT).contains(needle)))
                .map(patient -> new PatientDirectoryResponse(
                        patient.getId().getClinicId() + ":" + patient.getId().getPatNum(),
                        patient.getId().getPatNum(),
                        patient.getClinicAbbr(),
                        patient.getFName(),
                        patient.getLName(),
                        patient.getMiddleI(),
                        patient.getPreferred(),
                        patient.getPatStatus(),
                        patient.getGender(),
                        patient.getPosition(),
                        patient.getBirthdate() == null ? null : patient.getBirthdate().format(DATE_FORMAT),
                        patient.getEmail(),
                        patient.getWirelessPhone(),
                        patient.getHmPhone(),
                        patient.getWkPhone(),
                        patient.getTxtMsgOk(),
                        patient.getCity(),
                        patient.getState()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PatientSimpleResponse> getSimplePatients(Map<String, String> params) {
        try {
            List<PatientSimpleResponse> apiPatients = client.getSimplePatients(params);
            return apiPatients;
        } catch (Exception e) {
            log.warn("OpenDental API unavailable for simple patients, falling back to database: {}", e.getMessage());
            return patientRepository.findAll().stream()
                    .map(this::toSimplePatientResponse)
                    .filter(p -> matchesSearchParams(p, params))
                    .collect(Collectors.toList());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PatientResponse getPatient(Long patNum) {
        try {
            PatientResponse apiPatient = client.getPatient(patNum);
            // Sync to database
            savePatientToDb(apiPatient);
            return apiPatient;
        } catch (Exception e) {
            log.warn("OpenDental API unavailable for patient {}, falling back to database: {}", patNum, e.getMessage());
            Patient patient = patientRepository.findAll().stream()
                    .filter(p -> p.getId().getPatNum().equals(patNum))
                    .findFirst()
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                            "Patient not found with PatNum: " + patNum));
            return toPatientResponse(patient);
        }
    }

    @Override
    @Transactional
    public PatientResponse createPatient(CreatePatientRequest request) {
        try {
            PatientResponse response = client.createPatient(request);
            savePatientToDb(response);
            return response;
        } catch (Exception e) {
            log.error("Failed to create patient via OpenDental API: {}", e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to create patient: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public PatientResponse updatePatient(Long patNum, UpdatePatientRequest request) {
        try {
            PatientResponse response = client.updatePatient(patNum, request);
            savePatientToDb(response);
            return response;
        } catch (Exception e) {
            log.error("Failed to update patient via OpenDental API: {}", e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to update patient: " + e.getMessage());
        }
    }

    // ========== Database sync helpers ==========

    @Transactional
    protected void syncPatientsToDb(List<PatientResponse> apiPatients) {
        for (PatientResponse dto : apiPatients) {
            savePatientToDb(dto);
        }
    }

    @Transactional
    protected void savePatientToDb(PatientResponse dto) {
        try {
            Patient patient = toPatientEntity(dto);
            patientRepository.save(patient);
        } catch (Exception e) {
            log.error("Failed to sync patient {} to database: {}", dto.getPatNum(), e.getMessage());
        }
    }

    private UUID resolveClinicId() {
        // Use the first active clinic as default; can be enhanced to route by clinic code
        List<Clinic> clinics = clinicRepository.findByIsActiveTrue();
        if (clinics.isEmpty()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No active clinic configured. Please register a clinic in the clinics table.");
        }
        return clinics.get(0).getId();
    }

    private Patient toPatientEntity(PatientResponse dto) {
        UUID clinicId = resolveClinicId();

        Patient.PatientBuilder builder = Patient.builder()
                .id(new PatientId(clinicId, dto.getPatNum()))
                .lName(dto.getLName())
                .fName(dto.getFName())
                .middleI(dto.getMiddleI())
                .preferred(dto.getPreferred())
                .patStatus(dto.getPatStatus())
                .gender(dto.getGender())
                .position(dto.getPosition())
                .ssn(dto.getSSN())
                .address(dto.getAddress())
                .address2(dto.getAddress2())
                .city(dto.getCity())
                .state(dto.getState())
                .zip(dto.getZip())
                .hmPhone(dto.getHmPhone())
                .wkPhone(dto.getWkPhone())
                .wirelessPhone(dto.getWirelessPhone())
                .guarantor(dto.getGuarantor())
                .email(dto.getEmail())
                .priProv(dto.getPriProv())
                .secProv(dto.getSecProv())
                .feeSched(dto.getFeeSched())
                .billingType(dto.getBillingType())
                .chartNumber(dto.getChartNumber())
                .medicaidId(dto.getMedicaidID())
                .employerNum(dto.getEmployerNum())
                .clinicNum(dto.getClinicNum())
                .clinicAbbr(dto.getClinicAbbr())
                .hasIns(dto.getHasIns())
                .premed("true".equalsIgnoreCase(dto.getPremed()))
                .ward(dto.getWard())
                .preferConfirmMethod(dto.getPreferConfirmMethod())
                .preferContactMethod(dto.getPreferContactMethod())
                .preferRecallMethod(dto.getPreferRecallMethod())
                .language(dto.getLanguage())
                .siteNum(dto.getSiteNum())
                .siteDesc(dto.getSiteDesc())
                .superFamily(dto.getSuperFamily())
                .txtMsgOk(dto.getTxtMsgOk())
                .secUserNumEntry(dto.getSecUserNumEntry())
                .estBalance(dto.getEstBalance() != null ? BigDecimal.valueOf(dto.getEstBalance()) : BigDecimal.ZERO)
                .bal030(dto.getBal_0_30() != null ? BigDecimal.valueOf(dto.getBal_0_30()) : BigDecimal.ZERO)
                .bal3160(dto.getBal_31_60() != null ? BigDecimal.valueOf(dto.getBal_31_60()) : BigDecimal.ZERO)
                .bal6190(dto.getBal_61_90() != null ? BigDecimal.valueOf(dto.getBal_61_90()) : BigDecimal.ZERO)
                .balOver90(dto.getBalOver90() != null ? BigDecimal.valueOf(dto.getBalOver90()) : BigDecimal.ZERO)
                .insEst(dto.getInsEst() != null ? BigDecimal.valueOf(dto.getInsEst()) : BigDecimal.ZERO)
                .balTotal(dto.getBalTotal() != null ? BigDecimal.valueOf(dto.getBalTotal()) : BigDecimal.ZERO);

        // Parse dates safely
        if (dto.getBirthdate() != null && !dto.getBirthdate().isEmpty() && !dto.getBirthdate().equals("0001-01-01")) {
            builder.birthdate(LocalDate.parse(dto.getBirthdate(), DATE_FORMAT));
        }
        if (dto.getDateFirstVisit() != null && !dto.getDateFirstVisit().isEmpty() && !dto.getDateFirstVisit().equals("0001-01-01")) {
            builder.dateFirstVisit(LocalDate.parse(dto.getDateFirstVisit(), DATE_FORMAT));
        }
        if (dto.getAdmitDate() != null && !dto.getAdmitDate().isEmpty() && !dto.getAdmitDate().equals("0001-01-01")) {
            builder.admitDate(LocalDate.parse(dto.getAdmitDate(), DATE_FORMAT));
        }
        if (dto.getSecDateEntry() != null && !dto.getSecDateEntry().isEmpty() && !dto.getSecDateEntry().equals("0001-01-01")) {
            builder.secDateEntry(LocalDate.parse(dto.getSecDateEntry(), DATE_FORMAT));
        }
        if (dto.getDateTimeLastAging() != null && !dto.getDateTimeLastAging().isEmpty() && !dto.getDateTimeLastAging().equals("0001-01-01 00:00:00")) {
            builder.dateTimeLastAging(LocalDateTime.parse(dto.getDateTimeLastAging(), DATETIME_FORMAT));
        }

        return builder.build();
    }

    /**
     * OpenDental applies the search filters server-side. When the API is unreachable the
     * database fallback has to apply the same query parameters itself, otherwise a name
     * search would return every patient stored in the database.
     */
    private boolean matchesSearchParams(PatientResponse patient, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return true;
        }
        return matchesFilter(param(params, "LName"), patient.getLName(), true)
                && matchesFilter(param(params, "FName"), patient.getFName(), true)
                && matchesFilter(param(params, "PatNum"), asText(patient.getPatNum()), false)
                && matchesFilter(param(params, "Birthdate"), patient.getBirthdate(), false)
                && matchesFilter(param(params, "PatStatus"), patient.getPatStatus(), false);
    }

    private boolean matchesSearchParams(PatientSimpleResponse patient, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return true;
        }
        return matchesFilter(param(params, "LName"), patient.getLName(), true)
                && matchesFilter(param(params, "FName"), patient.getFName(), true)
                && matchesFilter(param(params, "PatNum"), asText(patient.getPatNum()), false)
                && matchesFilter(param(params, "Birthdate"), patient.getBirthdate(), false)
                && matchesFilter(param(params, "PatStatus"), patient.getPatStatus(), false);
    }

    /** Query parameter lookup that ignores the casing of the parameter name. */
    private String param(Map<String, String> params, String name) {
        if (params == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String asText(Long value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * @param prefix true for the name fields (OpenDental matches on the start of the name),
     *               false for fields that must match exactly. Blank filters are ignored.
     */
    private boolean matchesFilter(String filter, String value, boolean prefix) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        if (value == null) {
            return false;
        }
        String needle = filter.trim().toLowerCase();
        String haystack = value.toLowerCase();
        return prefix ? haystack.startsWith(needle) : haystack.equals(needle);
    }

    private PatientResponse toPatientResponse(Patient entity) {
        PatientResponse.PatientResponseBuilder builder = PatientResponse.builder()
                .PatNum(entity.getId().getPatNum())
                .LName(entity.getLName())
                .FName(entity.getFName())
                .MiddleI(entity.getMiddleI())
                .Preferred(entity.getPreferred())
                .PatStatus(entity.getPatStatus())
                .Gender(entity.getGender())
                .Position(entity.getPosition())
                .SSN(entity.getSsn())
                .Address(entity.getAddress())
                .Address2(entity.getAddress2())
                .City(entity.getCity())
                .State(entity.getState())
                .Zip(entity.getZip())
                .HmPhone(entity.getHmPhone())
                .WkPhone(entity.getWkPhone())
                .WirelessPhone(entity.getWirelessPhone())
                .Guarantor(entity.getGuarantor())
                .Email(entity.getEmail())
                .PriProv(entity.getPriProv())
                .SecProv(entity.getSecProv())
                .FeeSched(entity.getFeeSched())
                .BillingType(entity.getBillingType())
                .ChartNumber(entity.getChartNumber())
                .MedicaidID(entity.getMedicaidId())
                .EmployerNum(entity.getEmployerNum())
                .ClinicNum(entity.getClinicNum())
                .clinicAbbr(entity.getClinicAbbr())
                .HasIns(entity.getHasIns())
                .Premed(entity.getPremed() != null && entity.getPremed() ? "true" : "false")
                .Ward(entity.getWard())
                .PreferConfirmMethod(entity.getPreferConfirmMethod())
                .PreferContactMethod(entity.getPreferContactMethod())
                .PreferRecallMethod(entity.getPreferRecallMethod())
                .Language(entity.getLanguage())
                .SiteNum(entity.getSiteNum())
                .siteDesc(entity.getSiteDesc())
                .SuperFamily(entity.getSuperFamily())
                .TxtMsgOk(entity.getTxtMsgOk())
                .SecUserNumEntry(entity.getSecUserNumEntry())
                .EstBalance(entity.getEstBalance() != null ? entity.getEstBalance().doubleValue() : 0.0)
                .Bal_0_30(entity.getBal030() != null ? entity.getBal030().doubleValue() : 0.0)
                .Bal_31_60(entity.getBal3160() != null ? entity.getBal3160().doubleValue() : 0.0)
                .Bal_61_90(entity.getBal6190() != null ? entity.getBal6190().doubleValue() : 0.0)
                .BalOver90(entity.getBalOver90() != null ? entity.getBalOver90().doubleValue() : 0.0)
                .InsEst(entity.getInsEst() != null ? entity.getInsEst().doubleValue() : 0.0)
                .BalTotal(entity.getBalTotal() != null ? entity.getBalTotal().doubleValue() : 0.0);

        if (entity.getBirthdate() != null) {
            builder.Birthdate(entity.getBirthdate().format(DATE_FORMAT));
        }
        if (entity.getDateFirstVisit() != null) {
            builder.DateFirstVisit(entity.getDateFirstVisit().format(DATE_FORMAT));
        }
        if (entity.getAdmitDate() != null) {
            builder.AdmitDate(entity.getAdmitDate().format(DATE_FORMAT));
        }
        if (entity.getSecDateEntry() != null) {
            builder.SecDateEntry(entity.getSecDateEntry().format(DATE_FORMAT));
        }
        if (entity.getDateTimeLastAging() != null) {
            builder.dateTimeLastAging(entity.getDateTimeLastAging().format(DATETIME_FORMAT));
        }

        return builder.build();
    }

    private PatientSimpleResponse toSimplePatientResponse(Patient entity) {
        PatientSimpleResponse.PatientSimpleResponseBuilder builder = PatientSimpleResponse.builder()
                .PatNum(entity.getId().getPatNum())
                .LName(entity.getLName())
                .FName(entity.getFName())
                .MiddleI(entity.getMiddleI())
                .Preferred(entity.getPreferred())
                .PatStatus(entity.getPatStatus())
                .Gender(entity.getGender())
                .Position(entity.getPosition())
                .SSN(entity.getSsn())
                .Address(entity.getAddress())
                .Address2(entity.getAddress2())
                .City(entity.getCity())
                .State(entity.getState())
                .Zip(entity.getZip())
                .HmPhone(entity.getHmPhone())
                .WkPhone(entity.getWkPhone())
                .WirelessPhone(entity.getWirelessPhone())
                .Guarantor(entity.getGuarantor())
                .Email(entity.getEmail())
                .PriProv(entity.getPriProv())
                .SecProv(entity.getSecProv())
                .FeeSched(entity.getFeeSched())
                .BillingType(entity.getBillingType())
                .ChartNumber(entity.getChartNumber())
                .MedicaidID(entity.getMedicaidId())
                .EmployerNum(entity.getEmployerNum())
                .ClinicNum(entity.getClinicNum())
                .clinicAbbr(entity.getClinicAbbr())
                .HasIns(entity.getHasIns())
                .Premed(entity.getPremed() != null && entity.getPremed() ? "true" : "false")
                .Ward(entity.getWard())
                .PreferConfirmMethod(entity.getPreferConfirmMethod())
                .PreferContactMethod(entity.getPreferContactMethod())
                .PreferRecallMethod(entity.getPreferRecallMethod())
                .Language(entity.getLanguage())
                .SiteNum(entity.getSiteNum())
                .siteDesc(entity.getSiteDesc())
                .SuperFamily(entity.getSuperFamily())
                .TxtMsgOk(entity.getTxtMsgOk())
                .SecUserNumEntry(entity.getSecUserNumEntry())
                .EstBalance(entity.getEstBalance() != null ? entity.getEstBalance().doubleValue() : 0.0)
                .Bal_0_30(entity.getBal030() != null ? entity.getBal030().doubleValue() : 0.0)
                .Bal_31_60(entity.getBal3160() != null ? entity.getBal3160().doubleValue() : 0.0)
                .Bal_61_90(entity.getBal6190() != null ? entity.getBal6190().doubleValue() : 0.0)
                .BalOver90(entity.getBalOver90() != null ? entity.getBalOver90().doubleValue() : 0.0)
                .InsEst(entity.getInsEst() != null ? entity.getInsEst().doubleValue() : 0.0)
                .BalTotal(entity.getBalTotal() != null ? entity.getBalTotal().doubleValue() : 0.0);

        if (entity.getBirthdate() != null) {
            builder.Birthdate(entity.getBirthdate().format(DATE_FORMAT));
        }
        if (entity.getDateFirstVisit() != null) {
            builder.DateFirstVisit(entity.getDateFirstVisit().format(DATE_FORMAT));
        }
        if (entity.getAdmitDate() != null) {
            builder.AdmitDate(entity.getAdmitDate().format(DATE_FORMAT));
        }
        if (entity.getSecDateEntry() != null) {
            builder.SecDateEntry(entity.getSecDateEntry().format(DATE_FORMAT));
        }
        if (entity.getDateTimeLastAging() != null) {
            builder.dateTimeLastAging(entity.getDateTimeLastAging().format(DATETIME_FORMAT));
        }

        return builder.build();
    }
}