package com.clinic.opendental.service.Impl;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clinic.opendental.client.OpenDentalClient;
import com.clinic.opendental.dto.patient.PatientResponse;
import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.model.Patient;
import com.clinic.opendental.model.PatientId;
import com.clinic.opendental.repository.AppointmentRepository;
import com.clinic.opendental.repository.ClinicRepository;
import com.clinic.opendental.repository.DocumentRepository;
import com.clinic.opendental.repository.OdClinicRepository;
import com.clinic.opendental.repository.PatientRepository;
import com.clinic.opendental.repository.ProcedureLogRepository;
import com.clinic.opendental.repository.ref.PatFieldRepository;

/**
 * Verifies that {@code reconcilePatients} correctly applies difference
 * detection:
 * <ul>
 *   <li>inserts missing records,</li>
 *   <li>updates records whose data changed,</li>
 *   <li>skips records whose data is identical, and</li>
 *   <li>skips (without crashing) records whose primary key is null.</li>
 * </ul>
 */
class ReconciliationSyncServicePatientsTest {

    private final UUID clinicId = UUID.randomUUID();
    private final Clinic clinic = Clinic.builder()
            .id(clinicId)
            .clinicCode("CLINIC_A")
            .baseUrl("http://opendental.test/api/v1")
            .apiKey("test-key")
            .build();

    private Method getReconcilePatientsMethod() throws Exception {
        Method m = ReconciliationSyncService.class
                .getDeclaredMethod("reconcilePatients", Clinic.class);
        m.setAccessible(true);
        return m;
    }

    private static PatientResponse patientDto(Long patNum, String lName) {
        return PatientResponse.builder()
                .PatNum(patNum).LName(lName).FName("Test")
                .HasIns("Y").EstBalance(0.0).BalTotal(0.0).build();
    }

    private static Patient patientEntity(UUID clinicId, Long patNum, String lName) {
        Patient p = new Patient();
        p.setId(new PatientId(clinicId, patNum));
        p.setLName(lName); p.setFName("Test");
        p.setHasIns("Y");
        p.setEstBalance(BigDecimal.valueOf(0.0));
        p.setBalTotal(BigDecimal.valueOf(0.0));
        // Defaults matching toPatientEntity from ReconciliationSyncService:
        p.setPremed(false);
        p.setBal030(BigDecimal.ZERO);
        p.setBal3160(BigDecimal.ZERO);
        p.setBal6190(BigDecimal.ZERO);
        p.setBalOver90(BigDecimal.ZERO);
        p.setInsEst(BigDecimal.ZERO);
        return p;
    }

    private static Patient patientEntityDifferent(UUID clinicId, Long patNum) {
        Patient p = patientEntity(clinicId, patNum, "Smith");
        // EstBalance differs from the default 0.0 set above
        p.setEstBalance(BigDecimal.valueOf(150.00));
        return p;
    }
// ================================================================
    // Test: null-patNum record is skipped (not inserted)
    // ================================================================
    @Test
    void skipNullPatNumRecord() throws Exception {
        OpenDentalClient client = mock(OpenDentalClient.class);
        PatientRepository patientRepository = mock(PatientRepository.class);
        when(client.getPatients(eq(Map.of()), eq(clinic.getBaseUrl()), eq(clinic.getApiKey())))
                .thenReturn(List.of(patientDto(null, "NullPK")));
        when(patientRepository.findByIdClinicId(eq(clinicId))).thenReturn(List.of());

        ReconciliationSyncService service = new ReconciliationSyncService(
                mock(ClinicRepository.class), patientRepository,
                mock(AppointmentRepository.class), mock(DocumentRepository.class),
                mock(ProcedureLogRepository.class), mock(PatFieldRepository.class), mock(OdClinicRepository.class),
                null, null, null,
                null, client);

        getReconcilePatientsMethod().invoke(service, clinic);
        verify(patientRepository, never()).save(any());
    }

    // ================================================================
    // Test: missing record is inserted
    // ================================================================
    @Test
    void insertMissingRecord() throws Exception {
        OpenDentalClient client = mock(OpenDentalClient.class);
        PatientRepository patientRepository = mock(PatientRepository.class);
        when(client.getPatients(eq(Map.of()), eq(clinic.getBaseUrl()), eq(clinic.getApiKey())))
                .thenReturn(List.of(patientDto(101L, "New")));
        when(patientRepository.findByIdClinicId(eq(clinicId))).thenReturn(List.of());

        ReconciliationSyncService service = new ReconciliationSyncService(
                mock(ClinicRepository.class), patientRepository,
                mock(AppointmentRepository.class), mock(DocumentRepository.class),
                mock(ProcedureLogRepository.class), mock(PatFieldRepository.class), mock(OdClinicRepository.class),
                null, null, null,
                null, client);

        getReconcilePatientsMethod().invoke(service, clinic);
        verify(patientRepository).save(any());
    }

    // ================================================================
    // Test: changed record updated, unchanged record skipped
    // ================================================================
    @Test
    void updateChangedSkipUnchanged() throws Exception {
        Patient storedUnchanged = patientEntity(clinicId, 1L, "Unchanged");
        Patient storedChanged   = patientEntityDifferent(clinicId, 2L);

        PatientResponse incomingUnchanged = patientDto(1L, "Unchanged");
        PatientResponse incomingChanged   = patientDto(2L, "Smith");

        OpenDentalClient client = mock(OpenDentalClient.class);
        when(client.getPatients(eq(Map.of()), eq(clinic.getBaseUrl()), eq(clinic.getApiKey())))
                .thenReturn(List.of(incomingUnchanged, incomingChanged));

        PatientRepository patientRepository = mock(PatientRepository.class);
        when(patientRepository.findByIdClinicId(eq(clinicId)))
                .thenReturn(List.of(storedUnchanged, storedChanged));

        ReconciliationSyncService service = new ReconciliationSyncService(
                mock(ClinicRepository.class), patientRepository,
                mock(AppointmentRepository.class), mock(DocumentRepository.class),
                mock(ProcedureLogRepository.class), mock(PatFieldRepository.class), mock(OdClinicRepository.class),
                null, null, null,
                null, client);

        getReconcilePatientsMethod().invoke(service, clinic);
        // Only the changed record triggers a save
        verify(patientRepository).save(any());
    }

    // ================================================================
    // Test: null-patNum doesn't block valid records
    // ================================================================
    @Test
    void skipNullDoesNotBlockValidRecords() throws Exception {
        OpenDentalClient client = mock(OpenDentalClient.class);
        PatientRepository patientRepository = mock(PatientRepository.class);
        when(client.getPatients(eq(Map.of()), eq(clinic.getBaseUrl()), eq(clinic.getApiKey())))
                .thenReturn(List.of(
                        patientDto(null, "NullPK"),
                        patientDto(101L, "Good")
                ));
        when(patientRepository.findByIdClinicId(eq(clinicId))).thenReturn(List.of());

        ReconciliationSyncService service = new ReconciliationSyncService(
                mock(ClinicRepository.class), patientRepository,
                mock(AppointmentRepository.class), mock(DocumentRepository.class),
                mock(ProcedureLogRepository.class), mock(PatFieldRepository.class), mock(OdClinicRepository.class),
                null, null, null,
                null, client);

        getReconcilePatientsMethod().invoke(service, clinic);
        verify(patientRepository).save(any());
    }
}
