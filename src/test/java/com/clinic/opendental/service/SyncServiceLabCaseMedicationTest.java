package com.clinic.opendental.service;

import com.clinic.opendental.dto.labcase.LabCaseResponse;
import com.clinic.opendental.dto.labcasedeleted.LabCaseDeletedResponse;
import com.clinic.opendental.dto.medicationpat.MedicationPatResponse;
import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.model.ref.LabCase;
import com.clinic.opendental.model.ref.LabCaseId;
import com.clinic.opendental.model.ref.MedicationPat;
import com.clinic.opendental.repository.ClinicRepository;
import com.clinic.opendental.repository.ref.LabCaseRepository;
import com.clinic.opendental.repository.ref.MedicationPatRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** LabCase and MedicationPat webhooks save to their own tables, like the other webhook resources. */
@ExtendWith(MockitoExtension.class)
class SyncServiceLabCaseMedicationTest {

    private static final UUID CLINIC_ID = UUID.randomUUID();

    @Mock private ClinicRepository clinicRepository;
    @Mock private LabCaseRepository labCaseRepository;
    @Mock private MedicationPatRepository medicationPatRepository;
    @InjectMocks private SyncService syncService;

    @BeforeEach
    void setUp() {
        lenient().when(clinicRepository.findByClinicCode("A")).thenReturn(Optional.of(Clinic.builder().id(CLINIC_ID).clinicCode("A").build()));
    }

    @Test
    void aLabCaseIsSavedWithOpenDentalsDatesAndFee() throws Exception {
        // Open Dental's documented example.
        LabCaseResponse dto = new ObjectMapper().readValue("""
                {"LabCaseNum":226,"PatNum":33,"LaboratoryNum":1,"AptNum":143,"PlannedAptNum":0,
                 "DateTimeDue":"0001-01-01 00:00:00","DateTimeCreated":"2022-09-30 14:23:12","DateTimeSent":"2022-10-03 14:24:12",
                 "DateTimeRecd":"2022-10-04 14:24:12","DateTimeChecked":"2022-10-05 14:24:12","ProvNum":3,
                 "Instructions":"Repair clasps on Max partial","LabFee":0.0,"DateTStamp":"2022-10-05 14:24:12","InvoiceNum":""}""",
                LabCaseResponse.class);

        assertThat(syncService.saveLabCaseFromDto(dto, "A").failedCount()).isZero();

        ArgumentCaptor<LabCase> saved = ArgumentCaptor.forClass(LabCase.class);
        verify(labCaseRepository).save(saved.capture());
        LabCase labCase = saved.getValue();
        assertThat(labCase.getId()).isEqualTo(new LabCaseId(CLINIC_ID, 226L));
        assertThat(labCase.getDateTimeDue()).as("Open Dental's 'no date'").isNull();
        assertThat(labCase.getDateTimeSent()).isEqualTo(LocalDateTime.of(2022, 10, 3, 14, 24, 12));
        assertThat(labCase.getLabFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(labCase.getIsDeleted()).isFalse();
    }

    @Test
    void aDeletedLabCaseIsSoftDeleted() {
        LabCase stored = LabCase.builder().id(new LabCaseId(CLINIC_ID, 226L)).isDeleted(false).build();
        when(labCaseRepository.findById(new LabCaseId(CLINIC_ID, 226L))).thenReturn(Optional.of(stored));

        syncService.saveLabCaseDeletedFromDto(LabCaseDeletedResponse.builder().LabCaseNum(226L).DeletedBy(4L).build(), "A");

        assertThat(stored.getIsDeleted()).isTrue();
        assertThat(stored.getDeletedBy()).isEqualTo(4L);
        assertThat(stored.getDeletedAt()).isNotNull();
        verify(labCaseRepository).save(stored);
    }

    @Test
    void aPatientMedicationIsSavedEvenWithPatNumAsText() throws Exception {
        // Open Dental's example sends PatNum as a string.
        MedicationPatResponse dto = new ObjectMapper().readValue("""
                {"MedicationPatNum":45,"PatNum":"234","medName":"Metformin","MedicationNum":12,
                 "PatNote":"500mg, taken twice a day.","DateStart":"2000-06-20","DateStop":"0001-01-01","ProvNum":1}""",
                MedicationPatResponse.class);

        syncService.saveMedicationPatFromDto(dto, "A");

        ArgumentCaptor<MedicationPat> saved = ArgumentCaptor.forClass(MedicationPat.class);
        verify(medicationPatRepository).save(saved.capture());
        assertThat(saved.getValue().getPatNum()).isEqualTo(234L);
        assertThat(saved.getValue().getMedName()).isEqualTo("Metformin");
        assertThat(saved.getValue().getDateStart()).isEqualTo(LocalDate.of(2000, 6, 20));
        assertThat(saved.getValue().getDateStop()).isNull();
    }

    @Test
    void aRecordWithoutItsKeyIsRefused() {
        assertThat(syncService.saveLabCaseFromDto(new LabCaseResponse(), "A").failedCount()).isEqualTo(1);
        verify(labCaseRepository, never()).save(any());
    }
}
