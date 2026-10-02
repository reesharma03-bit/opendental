package com.clinic.opendental.service.Impl;

import java.lang.reflect.Method;
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
import com.clinic.opendental.repository.AppointmentRepository;
import com.clinic.opendental.repository.ClinicRepository;
import com.clinic.opendental.repository.DocumentRepository;
import com.clinic.opendental.repository.OdClinicRepository;
import com.clinic.opendental.repository.PatientRepository;
import com.clinic.opendental.repository.ProcedureLogRepository;
import com.clinic.opendental.repository.ref.PatFieldRepository;
import com.clinic.opendental.repository.ref.OperatoryRepository;
import com.clinic.opendental.repository.ref.ProviderRepository;
import com.clinic.opendental.repository.ref.ScheduleRepository;
import com.clinic.opendental.repository.ref.ToothInitialRepository;

/**
 * Verifies the documents reconciliation no longer calls Open Dental's
 * {@code GET /documents} with empty params (which returned 400 "PatNum is required").
 * Documents are patient-scoped, so they must be fetched per patient with PatNum.
 */
class ReconciliationSyncServiceDocumentsTest {

    @Test
    void reconcileDocumentsFetchesDocumentsPerPatientUsingPatNum() throws Exception {
        OpenDentalClient client = mock(OpenDentalClient.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        when(documentRepository.findByIdClinicId(any())).thenReturn(List.of());

        PatientResponse patient9 = new PatientResponse();
        patient9.setPatNum(9L);
        PatientResponse patient47 = new PatientResponse();
        patient47.setPatNum(47L);

        Clinic clinic = Clinic.builder()
                .id(UUID.randomUUID())
                .clinicCode("CLINIC_A")
                .baseUrl("http://opendental.test/api/v1")
                .apiKey("test-key")
                .build();

        // Patients for the installation are fetched once (no PatNum filter) ...
        when(client.getPatients(eq(Map.of()), eq(clinic.getBaseUrl()), eq(clinic.getApiKey())))
                .thenReturn(List.of(patient9, patient47));
        // ... and documents are fetched per patient, scoped with PatNum.
        when(client.getDocuments(eq(Map.of("PatNum", "9")), eq(clinic.getBaseUrl()), eq(clinic.getApiKey())))
                .thenReturn(List.of());
        when(client.getDocuments(eq(Map.of("PatNum", "47")), eq(clinic.getBaseUrl()), eq(clinic.getApiKey())))
                .thenReturn(List.of());

        ReconciliationSyncService service = new ReconciliationSyncService(
                mock(ClinicRepository.class),
                mock(PatientRepository.class),
                mock(AppointmentRepository.class),
                documentRepository,
                mock(ProcedureLogRepository.class),
                mock(PatFieldRepository.class),
                mock(OdClinicRepository.class),
                mock(ScheduleRepository.class),
                mock(ToothInitialRepository.class),
                mock(ProviderRepository.class),
                mock(OperatoryRepository.class),
                client);

        Method reconcileDocuments = ReconciliationSyncService.class
                .getDeclaredMethod("reconcileDocuments", Clinic.class);
        reconcileDocuments.setAccessible(true);
        // Must not throw: the old empty-param call returned 400 and was swallowed
        // by the per-entity try/catch, leaving an empty merge set each run.
        reconcileDocuments.invoke(service, clinic);

        verify(client).getPatients(eq(Map.of()), eq(clinic.getBaseUrl()), eq(clinic.getApiKey()));
        verify(client).getDocuments(eq(Map.of("PatNum", "9")), eq(clinic.getBaseUrl()), eq(clinic.getApiKey()));
        verify(client).getDocuments(eq(Map.of("PatNum", "47")), eq(clinic.getBaseUrl()), eq(clinic.getApiKey()));
        // Open Dental requires PatNum -> documents must NEVER be fetched with empty params.
        verify(client, never()).getDocuments(eq(Map.of()), any(), any());
    }
}
