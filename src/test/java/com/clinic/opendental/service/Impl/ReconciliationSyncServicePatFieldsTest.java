package com.clinic.opendental.service.Impl;

import com.clinic.opendental.client.OpenDentalClient;
import com.clinic.opendental.dto.patfield.PatFieldResponse;
import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.model.ref.PatField;
import com.clinic.opendental.model.ref.PatFieldId;
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
import com.clinic.opendental.service.Impl.ReconciliationSyncService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@code reconcilePatFields} correctly applies difference
 * detection:
 * <ul>
 *   <li>inserts missing records,</li>
 *   <li>updates records whose data changed,</li>
 *   <li>skips records whose data is identical, and</li>
 *   <li>skips (without crashing) records whose primary key is null.</li>
 * </ul>
 */
class ReconciliationSyncServicePatFieldsTest {

    private final UUID clinicId = UUID.randomUUID();
    private final Clinic clinic = Clinic.builder()
            .id(clinicId)
            .clinicCode("CLINIC_A")
            .baseUrl("http://opendental.test/api/v1")
            .apiKey("test-key")
            .build();

    private Method getReconcilePatFieldsMethod() throws Exception {
        Method m = ReconciliationSyncService.class
                .getDeclaredMethod("reconcilePatFields", Clinic.class);
        m.setAccessible(true);
        return m;
    }

    private static PatFieldResponse patFieldDto(Long patFieldNum, String fieldName, String fieldValue) {
        return PatFieldResponse.builder()
                .PatFieldNum(patFieldNum)
                .PatNum(1L)
                .FieldName(fieldName)
                .FieldValue(fieldValue)
                .FieldDesc("desc-" + fieldName)
                .FieldType("type-" + fieldName)
                .ClinicNum(5L)
                .build();
    }

    private static PatField patFieldEntity(UUID clinicId, Long patFieldNum, String fieldName, String fieldValue) {
        PatField p = new PatField();
        p.setId(new PatFieldId(clinicId, patFieldNum));
        p.setPatNum(1L);
        p.setFieldName(fieldName);
        p.setFieldValue(fieldValue);
        p.setFieldDesc("desc-" + fieldName);
        p.setFieldType("type-" + fieldName);
        p.setClinicNum(5L);
        return p;
    }

    private static PatField patFieldEntityDifferent(UUID clinicId, Long patFieldNum) {
        PatField p = patFieldEntity(clinicId, patFieldNum, "FieldName", "originalValue");
        p.setFieldValue("changedValue");
        return p;
    }

    private static ReconciliationSyncService buildService(Clinic clinic,
                                                          OpenDentalClient client,
                                                          PatFieldRepository patFieldRepository) {
        return new ReconciliationSyncService(
                mock(ClinicRepository.class),
                mock(PatientRepository.class),
                mock(AppointmentRepository.class),
                mock(DocumentRepository.class),
                mock(ProcedureLogRepository.class),
                patFieldRepository,
                mock(OdClinicRepository.class),
                mock(ScheduleRepository.class),
                mock(ToothInitialRepository.class),
                mock(ProviderRepository.class),
                mock(OperatoryRepository.class),
                client);
    }

    // ================================================================
    // Test: record missing in Supabase is inserted
    // ================================================================
    @Test
    void insertMissingRecord() throws Exception {
        PatFieldRepository patFieldRepository = mock(PatFieldRepository.class);
        OpenDentalClient client = mock(OpenDentalClient.class);
        when(client.getPatFields(eq(new java.util.HashMap<>()), eq(clinic.getBaseUrl()), eq(clinic.getApiKey())))
                .thenReturn(List.of(patFieldDto(101L, "FieldName", "FieldValue")));

        ReconciliationSyncService service = buildService(clinic, client, patFieldRepository);

        getReconcilePatFieldsMethod().invoke(service, clinic);
        verify(patFieldRepository).save(any());
    }

    // ================================================================
    // Test: changed record updated, unchanged record skipped
    // ================================================================
    @Test
    void updateChangedSkipUnchanged() throws Exception {
        PatField storedUnchanged = patFieldEntity(clinicId, 1L, "FieldName", "FieldValue");
        PatField storedChanged = patFieldEntityDifferent(clinicId, 2L);

        PatFieldResponse incomingUnchanged = patFieldDto(1L, "FieldName", "FieldValue");
        PatFieldResponse incomingChanged = patFieldDto(2L, "FieldName", "FieldValue");

        OpenDentalClient client = mock(OpenDentalClient.class);
        when(client.getPatFields(eq(new java.util.HashMap<>()), eq(clinic.getBaseUrl()), eq(clinic.getApiKey())))
                .thenReturn(List.of(incomingUnchanged, incomingChanged));

        PatFieldRepository patFieldRepository = mock(PatFieldRepository.class);
        when(patFieldRepository.findByIdClinicId(eq(clinicId)))
                .thenReturn(List.of(storedUnchanged, storedChanged));

        ReconciliationSyncService service = buildService(clinic, client, patFieldRepository);

        getReconcilePatFieldsMethod().invoke(service, clinic);
        // Only the changed record triggers a save
        verify(patFieldRepository).save(any());
    }

    // ================================================================
    // Test: null-patFieldNum doesn't block valid records
    // ================================================================
    @Test
    void skipNullDoesNotBlockValidRecords() throws Exception {
        PatFieldRepository patFieldRepository = mock(PatFieldRepository.class);
        OpenDentalClient client = mock(OpenDentalClient.class);
        when(client.getPatFields(eq(new java.util.HashMap<>()), eq(clinic.getBaseUrl()), eq(clinic.getApiKey())))
                .thenReturn(List.of(
                        patFieldDto(null, "NullPK", "FieldValue"),
                        patFieldDto(101L, "GoodField", "GoodValue")
                ));
        when(patFieldRepository.findByIdClinicId(eq(clinicId))).thenReturn(List.of());

        ReconciliationSyncService service = buildService(clinic, client, patFieldRepository);

        getReconcilePatFieldsMethod().invoke(service, clinic);
        verify(patFieldRepository).save(any());
    }

    // ================================================================
    // Test: identical record is never saved
    // ================================================================
    @Test
    void identicalRecordIsNotSaved() throws Exception {
        PatField stored = patFieldEntity(clinicId, 1L, "FieldName", "FieldValue");

        PatFieldResponse incoming = patFieldDto(1L, "FieldName", "FieldValue");

        OpenDentalClient client = mock(OpenDentalClient.class);
        when(client.getPatFields(eq(new java.util.HashMap<>()), eq(clinic.getBaseUrl()), eq(clinic.getApiKey())))
                .thenReturn(List.of(incoming));

        PatFieldRepository patFieldRepository = mock(PatFieldRepository.class);
        when(patFieldRepository.findByIdClinicId(eq(clinicId)))
                .thenReturn(List.of(stored));

        ReconciliationSyncService service = buildService(clinic, client, patFieldRepository);

        getReconcilePatFieldsMethod().invoke(service, clinic);
        // No save should happen for an identical record
        verify(patFieldRepository, never()).save(any());
    }
}

