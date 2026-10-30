package com.clinic.opendental.service.Impl;

import com.clinic.opendental.client.OpenDentalClient;
import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.model.Patient;
import com.clinic.opendental.model.PatientId;
import com.clinic.opendental.service.Impl.OdSyncService;
import com.clinic.opendental.repository.ClinicRepository;
import com.clinic.opendental.repository.PatientRepository;
import com.clinic.opendental.security.Role;
import com.clinic.opendental.security.TestSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The Patients screen lists only the current clinic's patients, never another clinic's. */
class PatientServiceClinicTest {

    private static final Clinic SMITH = Clinic.builder().id(UUID.randomUUID()).clinicCode("SMITH").clinicName("Smith").isActive(true).build();
    private static final Clinic JONES = Clinic.builder().id(UUID.randomUUID()).clinicCode("JONES").clinicName("Jones").isActive(true).build();

    private final PatientRepository patients = mock(PatientRepository.class);
    private final ClinicRepository clinics = mock(ClinicRepository.class);
    private final PatientServiceImpl service = new PatientServiceImpl(mock(OpenDentalClient.class), patients, mock(com.clinic.opendental.repository.ProcedureLogRepository.class), clinics, mock(OdSyncService.class));


    @org.junit.jupiter.api.BeforeEach
    void stubs() {
        when(clinics.findByIsActiveTrue()).thenReturn(List.of(SMITH, JONES));
        when(clinics.findByClinicCode("JONES")).thenReturn(Optional.of(JONES));
        when(clinics.findById(SMITH.getId())).thenReturn(Optional.of(SMITH));
        when(patients.findByIdClinicId(SMITH.getId())).thenReturn(List.of(patient(SMITH, 1L, "Ann")));
        when(patients.findByIdClinicId(JONES.getId())).thenReturn(List.of(patient(JONES, 2L, "Bob")));
    }

    private static Patient patient(Clinic clinic, long patNum, String first) {
        Patient p = new Patient();
        p.setId(new PatientId(clinic.getId(), patNum));
        p.setFName(first);
        p.setLName("Test");
        return p;
    }

    @AfterEach
    void signOut() {
        TestSession.clear();
    }

    @Test
    void anAdminSeesTheChosenClinicsPatientsOnly() {
        TestSession.signIn(Role.ADMIN, null, "JONES");

        assertThat(service.getPatientDirectoryFromDatabase(Map.of())).extracting(p -> p.fName()).containsExactly("Bob");
        verify(patients, never()).findAll();
    }

    @Test
    void aDisconnectedClinicsPatientsAreHiddenUntilItIsConnectedAgain() {
        UUID dentist = TestSession.signIn(Role.DENTIST, null);
        Clinic mehta = Clinic.builder().id(UUID.randomUUID()).clinicCode("DR_MEHTA").clinicName("Dr Mehta").isActive(false)
                .ownerUserId(dentist).build();
        when(clinics.findByOwnerUserIdOrderByCreatedAtAsc(dentist)).thenReturn(List.of(mehta));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getPatientDirectoryFromDatabase(Map.of()))
                .hasMessageContaining("Dr Mehta isn't connected to Open Dental");
        verify(patients, never()).findByIdClinicId(mehta.getId());
    }

    @Test
    void aDentistSeesTheirOwnClinicsPatientsAndNeverAnotherDentists() {
        UUID dentist = TestSession.signIn(Role.DENTIST, null);
        Clinic own = Clinic.builder().id(SMITH.getId()).clinicCode("SMITH").clinicName("Smith").isActive(true).ownerUserId(dentist).build();
        when(clinics.findByOwnerUserIdOrderByCreatedAtAsc(dentist)).thenReturn(List.of(own));

        assertThat(service.getPatientDirectoryFromDatabase(Map.of())).extracting(p -> p.fName()).containsExactly("Ann");
        TestSession.clear();

        // Asking for another dentist's clinic by its code is refused before anything is read.
        TestSession.signIn(Role.DENTIST, null, "JONES");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getPatientDirectoryFromDatabase(Map.of()))
                .hasMessageContaining("don't have access");
        verify(patients, never()).findByIdClinicId(JONES.getId());
        verify(patients, never()).findAll(any(org.springframework.data.domain.Sort.class));
    }
}
