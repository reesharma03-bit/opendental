package com.clinic.opendental.service.Impl;

import com.clinic.opendental.client.OpenDentalClient;
import com.clinic.opendental.dto.appointment.AppointmentResponse;
import com.clinic.opendental.dto.appointment.CreateAppointmentRequest;
import com.clinic.opendental.dto.patient.CreatePatientRequest;
import com.clinic.opendental.dto.patient.PatientResponse;
import com.clinic.opendental.dto.patient.UpdatePatientRequest;
import com.clinic.opendental.model.*;
import com.clinic.opendental.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * "Our database first, then Open Dental": queued changes are pushed in order,
 * temporary keys are swapped for Open Dental's keys, and failures are retried.
 */
class OdSyncServiceTest {

    private static final UUID CLINIC_ID = UUID.randomUUID();
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Map<Long, OdSyncTask> queue = new HashMap<>();
    private long nextId = 1;

    private OdSyncTaskRepository tasks;
    private PatientRepository patients;
    private AppointmentRepository appointments;
    private DocumentRepository documents;
    private ProcedureLogRepository procedureLogs;
    private OpenDentalClient client;
    private ReconciliationSyncService mappers;
    private ResourceRecordStore records;
    private OdSyncService service;

    @BeforeEach
    void setUp() {
        tasks = mock(OdSyncTaskRepository.class);
        patients = mock(PatientRepository.class);
        appointments = mock(AppointmentRepository.class);
        documents = mock(DocumentRepository.class);
        procedureLogs = mock(ProcedureLogRepository.class);
        client = mock(OpenDentalClient.class);
        mappers = mock(ReconciliationSyncService.class);
        records = mock(ResourceRecordStore.class);
        ClinicRepository clinics = mock(ClinicRepository.class);

        when(clinics.findById(CLINIC_ID)).thenReturn(Optional.of(
                Clinic.builder().id(CLINIC_ID).baseUrl("http://od").apiKey("ODFHIR dev/cust").build()));

        // In-memory queue
        when(tasks.save(any(OdSyncTask.class))).thenAnswer(inv -> store(inv.getArgument(0)));
        when(tasks.saveAndFlush(any(OdSyncTask.class))).thenAnswer(inv -> store(inv.getArgument(0)));
        when(tasks.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(queue.get((Long) inv.getArgument(0))));
        when(tasks.claim(anyLong(), any())).thenAnswer(inv -> {
            OdSyncTask t = queue.get((Long) inv.getArgument(0));
            if (t == null || !OdSyncTask.PENDING.equals(t.getStatus())) return 0;
            t.setStatus(OdSyncTask.IN_PROGRESS);
            return 1;
        });
        when(tasks.existsEarlier(any(), any(), any(), any(), any())).thenAnswer(inv -> queue.values().stream()
                .anyMatch(t -> sameRecord(t, inv.getArgument(1), inv.getArgument(2))
                        && t.getId() < (Long) inv.getArgument(3) && isOpen(t)));
        when(tasks.existsLater(any(), any(), any(), any(), any())).thenAnswer(inv -> queue.values().stream()
                .anyMatch(t -> sameRecord(t, inv.getArgument(1), inv.getArgument(2))
                        && t.getId() > (Long) inv.getArgument(3) && isOpen(t)));
        when(tasks.moveToRealId(any(), any(), any(), any(), any())).thenAnswer(inv -> {
            queue.values().stream()
                    .filter(t -> sameRecord(t, inv.getArgument(1), inv.getArgument(2)) && isOpen(t))
                    .forEach(t -> t.setLocalId(inv.getArgument(3)));
            return 0;
        });
        when(tasks.cancelForRecord(any(), any(), any(), any())).thenAnswer(inv -> {
            queue.values().stream()
                    .filter(t -> sameRecord(t, inv.getArgument(1), inv.getArgument(2)) && isOpen(t))
                    .forEach(t -> t.setStatus(OdSyncTask.CANCELLED));
            return 0;
        });

        service = new OdSyncService(tasks, clinics, patients, appointments, documents, procedureLogs, client,
                mappers, records, mock(EntityManager.class), mock(PlatformTransactionManager.class), true, 3);
    }

    @Test
    void storedRequestReadsBackWithEveryField() {
        CreatePatientRequest request = CreatePatientRequest.builder()
                .LName("Smith").FName("John").WirelessPhone("555-0199").Birthdate("1976-05-24").PriProv(2L).build();

        UpdatePatientRequest copy = OdSyncService.convert(request, UpdatePatientRequest.class);

        assertThat(copy.getLName()).isEqualTo("Smith");
        assertThat(copy.getFName()).isEqualTo("John");
        assertThat(copy.getWirelessPhone()).isEqualTo("555-0199");
        assertThat(copy.getBirthdate()).isEqualTo("1976-05-24");
        assertThat(copy.getPriProv()).isEqualTo(2L);
    }

    @Test
    void newPatientIsSavedLocallyFirstUnderATemporaryKey() {
        long[] savedUnder = new long[1];

        long taskId = service.recordCreate(CLINIC_ID, OdSyncService.PATIENT, OdSyncService.CREATE,
                CreatePatientRequest.builder().LName("Smith").FName("John").build(), id -> savedUnder[0] = id);

        assertThat(savedUnder[0]).isEqualTo(-taskId);
        assertThat(queue.get(taskId).getStatus()).isEqualTo(OdSyncTask.PENDING);
        assertThat(queue.get(taskId).getPayload()).contains("\"LName\":\"Smith\"");
        verifyNoInteractions(client);
    }

    @Test
    void pushedPatientMovesToTheKeyOpenDentalAssigned() {
        long taskId = service.recordCreate(CLINIC_ID, OdSyncService.PATIENT, OdSyncService.CREATE,
                CreatePatientRequest.builder().LName("Smith").FName("John").build(), id -> { });
        long tempId = -taskId;
        Patient local = Patient.builder().id(new PatientId(CLINIC_ID, tempId)).lName("Smith").fName("John").build();
        when(patients.findById(new PatientId(CLINIC_ID, tempId))).thenReturn(Optional.of(local));
        PatientResponse created = PatientResponse.builder().PatNum(4321L).LName("Smith").FName("John").build();
        when(client.createPatient(any(), eq("http://od"), eq("ODFHIR dev/cust"))).thenReturn(created);
        Patient fromOpenDental = Patient.builder().id(new PatientId(CLINIC_ID, 4321L)).build();
        when(mappers.toPatientEntity(created, CLINIC_ID)).thenReturn(fromOpenDental);

        long key = service.pushNow(taskId);

        assertThat(key).isEqualTo(4321L);
        OdSyncTask task = queue.get(taskId);
        assertThat(task.getStatus()).isEqualTo(OdSyncTask.DONE);
        assertThat(task.getOdId()).isEqualTo(4321L);
        assertThat(task.getPayload()).isNull();
        assertThat(local.getId().getPatNum()).isEqualTo(4321L);
        verify(patients).saveAndFlush(local);
        verify(appointments).movePatient(CLINIC_ID, tempId, 4321L);
        verify(documents).movePatient(CLINIC_ID, tempId, 4321L);
        verify(procedureLogs).movePatient(CLINIC_ID, tempId, 4321L);
        verify(patients).deleteById(new PatientId(CLINIC_ID, tempId));
        verify(patients).save(fromOpenDental);
    }

    @Test
    void unreachableOpenDentalKeepsTheChangeQueuedForRetry() {
        long taskId = service.recordCreate(CLINIC_ID, OdSyncService.PATIENT, OdSyncService.CREATE,
                CreatePatientRequest.builder().LName("Smith").FName("John").build(), id -> { });
        when(client.createPatient(any(), any(), any())).thenThrow(new ResourceAccessException("Connection refused"));

        long key = service.pushNow(taskId);

        assertThat(key).isEqualTo(-taskId);
        OdSyncTask task = queue.get(taskId);
        assertThat(task.getStatus()).isEqualTo(OdSyncTask.PENDING);
        assertThat(task.getAttempts()).isEqualTo(1);
        assertThat(task.getLastError()).contains("Connection refused");
        assertThat(task.getNextAttemptAt()).isAfter(LocalDateTime.now());
        assertThat(task.getPayload()).isNotNull();
    }

    @Test
    void givesUpAfterMaxAttempts() {
        long taskId = service.recordCreate(CLINIC_ID, OdSyncService.PATIENT, OdSyncService.CREATE,
                CreatePatientRequest.builder().LName("Smith").FName("John").build(), id -> { });
        when(client.createPatient(any(), any(), any())).thenThrow(new ResourceAccessException("down"));

        for (int i = 0; i < 3; i++) {
            queue.get(taskId).setNextAttemptAt(LocalDateTime.now());
            service.process(taskId);
        }

        assertThat(queue.get(taskId).getStatus()).isEqualTo(OdSyncTask.FAILED);
        assertThat(queue.get(taskId).getAttempts()).isEqualTo(3);
    }

    @Test
    void appointmentForANewPatientWaitsForThatPatient() {
        long patientTask = service.recordCreate(CLINIC_ID, OdSyncService.PATIENT, OdSyncService.CREATE,
                CreatePatientRequest.builder().LName("Smith").FName("John").build(), id -> { });
        long appointmentTask = service.recordCreate(CLINIC_ID, OdSyncService.APPOINTMENT, OdSyncService.CREATE,
                CreateAppointmentRequest.builder().PatNum(-patientTask).Op(1L).AptDateTime("2026-10-05 09:00:00").build(),
                id -> { });

        service.process(appointmentTask);

        OdSyncTask task = queue.get(appointmentTask);
        assertThat(task.getStatus()).isEqualTo(OdSyncTask.PENDING);
        assertThat(task.getAttempts()).isZero();
        assertThat(task.getNextAttemptAt()).isAfter(LocalDateTime.now());
        verify(client, never()).createAppointment(any(), any(), any());
    }

    @Test
    void appointmentIsSentWithThePatientKeyOpenDentalAssigned() {
        long patientTask = service.recordCreate(CLINIC_ID, OdSyncService.PATIENT, OdSyncService.CREATE,
                CreatePatientRequest.builder().LName("Smith").FName("John").build(), id -> { });
        queue.get(patientTask).setStatus(OdSyncTask.DONE);
        queue.get(patientTask).setOdId(4321L);
        long appointmentTask = service.recordCreate(CLINIC_ID, OdSyncService.APPOINTMENT, OdSyncService.CREATE,
                CreateAppointmentRequest.builder().PatNum(-patientTask).Op(1L).AptDateTime("2026-10-05 09:00:00").build(),
                id -> { });
        AppointmentResponse created = AppointmentResponse.builder().AptNum(900L).PatNum(4321L).build();
        when(client.createAppointment(any(), any(), any())).thenReturn(created);
        when(mappers.toAppointmentEntity(created, CLINIC_ID))
                .thenReturn(Appointment.builder().id(new AppointmentId(CLINIC_ID, 900L)).patNum(4321L).build());

        long key = service.pushNow(appointmentTask);

        ArgumentCaptor<CreateAppointmentRequest> sent = ArgumentCaptor.forClass(CreateAppointmentRequest.class);
        verify(client).createAppointment(sent.capture(), eq("http://od"), eq("ODFHIR dev/cust"));
        assertThat(sent.getValue().getPatNum()).isEqualTo(4321L);
        assertThat(sent.getValue().getAptDateTime()).isEqualTo("2026-10-05 09:00:00");
        assertThat(key).isEqualTo(900L);
    }

    @Test
    void laterChangeWaitsForTheRecordsEarlierChange() {
        long createTask = service.recordCreate(CLINIC_ID, OdSyncService.PATIENT, OdSyncService.CREATE,
                CreatePatientRequest.builder().LName("Smith").FName("John").build(), id -> { });
        long updateTask = service.recordChange(CLINIC_ID, OdSyncService.PATIENT, OdSyncService.UPDATE, -createTask,
                UpdatePatientRequest.builder().City("Austin").build(), () -> { });

        assertThat(service.process(updateTask)).isFalse();

        assertThat(queue.get(updateTask).getStatus()).isEqualTo(OdSyncTask.PENDING);
        verifyNoInteractions(client);
    }

    @Test
    void queuedUpdateFollowsThePatientToItsRealKey() {
        long createTask = service.recordCreate(CLINIC_ID, OdSyncService.PATIENT, OdSyncService.CREATE,
                CreatePatientRequest.builder().LName("Smith").FName("John").build(), id -> { });
        long updateTask = service.recordChange(CLINIC_ID, OdSyncService.PATIENT, OdSyncService.UPDATE, -createTask,
                UpdatePatientRequest.builder().City("Austin").build(), () -> { });
        PatientResponse created = PatientResponse.builder().PatNum(4321L).build();
        when(client.createPatient(any(), any(), any())).thenReturn(created);

        service.pushNow(createTask);

        assertThat(queue.get(updateTask).getLocalId()).isEqualTo(4321L);
        // Open Dental's copy predates the queued edit, so it must not overwrite our row.
        verify(mappers, never()).toPatientEntity(any(), any());

        when(client.updatePatient(eq(4321L), any(), any(), any())).thenReturn(created);
        when(mappers.toPatientEntity(created, CLINIC_ID))
                .thenReturn(Patient.builder().id(new PatientId(CLINIC_ID, 4321L)).build());
        service.pushNow(updateTask);

        ArgumentCaptor<UpdatePatientRequest> sent = ArgumentCaptor.forClass(UpdatePatientRequest.class);
        verify(client).updatePatient(eq(4321L), sent.capture(), eq("http://od"), eq("ODFHIR dev/cust"));
        assertThat(sent.getValue().getCity()).isEqualTo("Austin");
        assertThat(queue.get(updateTask).getStatus()).isEqualTo(OdSyncTask.DONE);
    }

    @Test
    void deletingARecordOpenDentalNeverReceivedDropsItsQueuedChanges() {
        long createTask = service.recordCreate(CLINIC_ID, OdSyncService.DOCUMENT, OdSyncService.SET_BY_URL,
                Map.of("PatNum", 5L, "url", "https://example.test/x.pdf"), id -> { });
        boolean[] deletedLocally = new boolean[1];

        Long deleteTask = service.recordDelete(CLINIC_ID, OdSyncService.DOCUMENT, -createTask,
                () -> deletedLocally[0] = true);

        assertThat(deleteTask).isNull();
        assertThat(deletedLocally[0]).isTrue();
        assertThat(queue.get(createTask).getStatus()).isEqualTo(OdSyncTask.CANCELLED);
    }

    @Test
    void newAllergyIsSentToOpenDentalAndTakesItsKey() {
        long taskId = service.recordCreate(CLINIC_ID, "resource:allergies", OdSyncService.CREATE,
                Map.of("PatNum", 48, "defDescription", "Penicillin"), id -> { });
        String tempKey = String.valueOf(-taskId);
        ObjectNode local = JSON.createObjectNode().put("AllergyNum", -taskId).put("PatNum", 48).put("defDescription", "Penicillin");
        when(records.find(CLINIC_ID, "allergies", tempKey)).thenReturn(Optional.of(local));
        ObjectNode created = JSON.createObjectNode().put("AllergyNum", 501).put("PatNum", 48).put("defDescription", "Penicillin");
        when(client.sendRaw(eq(HttpMethod.POST), eq("/allergies"), any(), eq("http://od"), eq("ODFHIR dev/cust")))
                .thenReturn(created);

        long key = service.pushNow(taskId);

        assertThat(key).isEqualTo(501L);
        verify(records).delete(CLINIC_ID, "allergies", tempKey);
        verify(records).save(CLINIC_ID, "allergies", "501", created);
        assertThat(queue.get(taskId).getStatus()).isEqualTo(OdSyncTask.DONE);
    }

    @Test
    void allergyForANewPatientIsSentWithThePatientsRealKey() {
        long patientTask = service.recordCreate(CLINIC_ID, OdSyncService.PATIENT, OdSyncService.CREATE,
                CreatePatientRequest.builder().LName("Smith").FName("John").build(), id -> { });
        queue.get(patientTask).setStatus(OdSyncTask.DONE);
        queue.get(patientTask).setOdId(4321L);
        long allergyTask = service.recordCreate(CLINIC_ID, "resource:allergies", OdSyncService.CREATE,
                Map.of("PatNum", -patientTask, "defDescription", "Latex"), id -> { });
        when(client.sendRaw(any(), any(), any(), any(), any()))
                .thenReturn(JSON.createObjectNode().put("AllergyNum", 9));

        service.pushNow(allergyTask);

        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(client).sendRaw(eq(HttpMethod.POST), eq("/allergies"), sent.capture(), any(), any());
        assertThat(((ObjectNode) sent.getValue()).get("PatNum").asLong()).isEqualTo(4321L);
    }

    @Test
    void diseaseDefinitionCreatedWithoutAResponseBodyIsFoundByName() {
        long taskId = service.recordCreate(CLINIC_ID, "resource:diseasedefs", OdSyncService.CREATE,
                Map.of("DiseaseName", "Asthma"), id -> { });
        when(client.sendRaw(eq(HttpMethod.POST), eq("/diseasedefs"), any(), any(), any())).thenReturn(null);
        ArrayNode existing = JSON.createArrayNode();
        existing.add(JSON.createObjectNode().put("DiseaseDefNum", 3).put("DiseaseName", "Asthma"));
        existing.add(JSON.createObjectNode().put("DiseaseDefNum", 12).put("DiseaseName", "Asthma"));
        existing.add(JSON.createObjectNode().put("DiseaseDefNum", 15).put("DiseaseName", "Diabetes"));
        when(client.getRaw(eq("/diseasedefs"), any(), any(), any())).thenReturn(existing);

        assertThat(service.pushNow(taskId)).isEqualTo(12L);
    }

    @Test
    void invalidRequestsAreNotRetriedButBadCredentialsAre() {
        assertThat(OdSyncService.isRetryable(HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, "\"PatNum is invalid.\"".getBytes(), null))).isFalse();
        assertThat(OdSyncService.isRetryable(HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, "\"Malformed API request.\"".getBytes(), null))).isTrue();
        assertThat(OdSyncService.isRetryable(HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED, "Unauthorized", HttpHeaders.EMPTY, new byte[0], null))).isTrue();
        assertThat(OdSyncService.isRetryable(new ResourceAccessException("timeout"))).isTrue();
    }

    private OdSyncTask store(OdSyncTask task) {
        if (task.getId() == null) {
            task.setId(nextId++);
        }
        queue.put(task.getId(), task);
        return task;
    }

    private static boolean sameRecord(OdSyncTask t, String entityType, Long localId) {
        return t.getEntityType().equals(entityType) && t.getLocalId().equals(localId);
    }

    private static boolean isOpen(OdSyncTask t) {
        return OdSyncTaskRepository.OPEN.contains(t.getStatus());
    }
}
