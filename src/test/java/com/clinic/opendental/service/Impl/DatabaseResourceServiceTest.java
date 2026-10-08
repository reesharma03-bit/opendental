package com.clinic.opendental.service.Impl;

import com.clinic.opendental.client.OpenDentalClient;
import com.clinic.opendental.dto.patient.CreatePatientRequest;
import com.clinic.opendental.dto.patient.PatientResponse;
import com.clinic.opendental.exception.ApiException;
import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.repository.ClinicRepository;
import com.clinic.opendental.service.AppointmentService;
import com.clinic.opendental.service.PatientService;
import com.clinic.opendental.service.ProcedureLogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The dashboard's view of every Open Dental resource: read from our database, written here first. */
class DatabaseResourceServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Clinic CLINIC = Clinic.builder().id(UUID.randomUUID()).clinicCode("CLINIC_A").build();

    private ResourceRecordStore records;
    private OdSyncService odSync;
    private OpenDentalClient client;
    private PatientService patients;
    private DatabaseResourceService service;

    @BeforeEach
    void setUp() {
        records = mock(ResourceRecordStore.class);
        odSync = mock(OdSyncService.class);
        client = mock(OpenDentalClient.class);
        patients = mock(PatientService.class);
        ClinicRepository clinics = mock(ClinicRepository.class);
        when(clinics.findByIsActiveTrue()).thenReturn(List.of(CLINIC));
        service = new DatabaseResourceService(records, odSync, clinics, client, patients,
                mock(AppointmentService.class), mock(ProcedureLogService.class));
    }

    @Test
    void anOpenDentalUserIsCreatedDirectlyAndItsPasswordIsNeverKept() throws Exception {
        when(client.sendRaw(eq(org.springframework.http.HttpMethod.POST), eq("/userods"), any(), any(), any()))
                .thenReturn(JSON.readTree("""
                        {"UserNum":7,"UserName":"Sally","EmployeeNum":0,"ProviderNum":0,"ClinicNum":0,"IsHidden":"false",
                         "UserGroupNum":2,"Password":"My1password","IsPasswordResetRequired":"false"}"""));

        JsonNode created = service.create("userods", Map.of("UserName", "Sally", "UserGroupNum", 2, "Password", "My1password"));

        assertThat(created.has("Password")).isFalse();
        assertThat(created.path("userGroupNums").get(0).asLong()).isEqualTo(2);
        ArgumentCaptor<ObjectNode> saved = ArgumentCaptor.forClass(ObjectNode.class);
        verify(records).save(eq(CLINIC.getId()), eq("userods"), eq("7"), saved.capture());
        assertThat(saved.getValue().toString()).doesNotContain("My1password");
        verifyNoInteractions(odSync); // the password never waits in the outbox
    }

    @Test
    void aWeakPasswordOrAnUnchangeableFieldIsRefusedBeforeOpenDentalIsCalled() {
        assertThatThrownBy(() -> service.create("userods", Map.of("UserName", "Sally", "UserGroupNum", 2, "Password", "password")))
                .isInstanceOf(ApiException.class).hasMessageContaining("at least 8 characters");
        when(records.find(CLINIC.getId(), "userods", "7")).thenReturn(Optional.of(JSON.createObjectNode().put("UserNum", 7)));
        assertThatThrownBy(() -> service.update("userods", "7", Map.of("UserName", "Bob")))
                .isInstanceOf(ApiException.class).hasMessageContaining("change it in Open Dental");
        verifyNoInteractions(client);
    }

    @Test
    void listsReadOnlyFromOurDatabase() {
        when(records.list(CLINIC.getId(), "carriers", null, 100, 0)).thenReturn(List.of(JSON.createObjectNode()));

        assertThat(service.list("carriers", null, 100, 0)).hasSize(1);
        verifyNoInteractions(client);
    }

    @Test
    void newRecordIsSavedLocallyWithDefaultsAndATemporaryKey() {
        when(odSync.recordCreate(eq(CLINIC.getId()), eq("resource:allergies"), eq(OdSyncService.CREATE), any(), any()))
                .thenAnswer(inv -> {
                    ((LongConsumer) inv.getArgument(4)).accept(-7L);
                    return 7L;
                });
        when(odSync.pushNow(7L)).thenReturn(-7L); // Open Dental unreachable: stays under the temporary key
        when(records.find(CLINIC.getId(), "allergies", "-7")).thenReturn(Optional.of(JSON.createObjectNode()));

        service.create("allergies", Map.of("PatNum", 48, "defDescription", "Penicillin"));

        ArgumentCaptor<ObjectNode> saved = ArgumentCaptor.forClass(ObjectNode.class);
        verify(records).save(eq(CLINIC.getId()), eq("allergies"), eq("-7"), saved.capture());
        assertThat(saved.getValue().get("AllergyNum").asLong()).isEqualTo(-7L);
        assertThat(saved.getValue().get("StatusIsActive").asText()).isEqualTo("true");
        assertThat(saved.getValue().get("defDescription").asText()).isEqualTo("Penicillin");
    }

    @Test
    void readOnlyResourcesRejectChanges() {
        assertThatThrownBy(() -> service.create("claimforms", Map.of("Description", "x")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("does not allow create");
        assertThatThrownBy(() -> service.delete("carriers", "5"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("does not allow delete");
        verifyNoInteractions(odSync);
    }

    @Test
    void patientsGoThroughThePatientService() {
        when(patients.createPatient(any())).thenReturn(PatientResponse.builder().PatNum(4321L).LName("Smith").build());

        JsonNode created = service.create("patients", Map.of("LName", "Smith", "FName", "John"));

        ArgumentCaptor<CreatePatientRequest> sent = ArgumentCaptor.forClass(CreatePatientRequest.class);
        verify(patients).createPatient(sent.capture());
        assertThat(sent.getValue().getLName()).isEqualTo("Smith");
        assertThat(sent.getValue().getFName()).isEqualTo("John");
        assertThat(created.get("PatNum").asLong()).isEqualTo(4321L);
        assertThat(created.get("LName").asText()).isEqualTo("Smith");
    }

    @Test
    void everyResourceReportsWhatItMayChange() {
        List<Map<String, Object>> resources = service.resources();

        Map<String, Object> carriers = resources.stream().filter(r -> r.get("resource").equals("carriers")).findFirst().orElseThrow();
        Map<String, Object> claimforms = resources.stream().filter(r -> r.get("resource").equals("claimforms")).findFirst().orElseThrow();
        assertThat(carriers).containsEntry("keyField", "CarrierNum").containsEntry("create", true).containsEntry("delete", false);
        assertThat(claimforms).containsEntry("create", false).containsEntry("update", false).containsEntry("delete", false);
        assertThat(resources).extracting(r -> r.get("resource")).contains("patients", "appointments", "procedurelogs", "allergies");
        // Every editable resource is also synced, so the screen has data to edit.
        OdResourceCatalog.WRITABLE.keySet().forEach(name -> assertThat(OdResourceCatalog.find(name)).as(name).isNotNull());
    }
}
