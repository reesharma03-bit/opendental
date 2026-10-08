package com.clinic.opendental.service;

import com.clinic.opendental.security.Permission;
import com.clinic.opendental.security.PermissionResolver;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The patient open in Open Dental on each workstation (PatientSelected UI event). */
class SelectedPatientsTest {

    @Test
    void theLatestSelectionPerWorkstationIsShownNewestFirst() throws Exception {
        SelectedPatients selected = new SelectedPatients();
        UUID clinic = UUID.randomUUID();

        selected.record(clinic, "FRONTDESK1", 48, "Smith, John");
        Thread.sleep(5);
        selected.record(clinic, "OP2", 51, "Doe, Jane");
        Thread.sleep(5);
        selected.record(clinic, "FRONTDESK1", 60, "Lee, Ann");

        assertThat(selected.recent(clinic)).extracting(SelectedPatients.Selection::workstation, SelectedPatients.Selection::patNum)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("FRONTDESK1", 60L), org.assertj.core.groups.Tuple.tuple("OP2", 51L));
        assertThat(selected.recent(UUID.randomUUID())).as("other clinics see nothing").isEmpty();
    }

    @Test
    void emptyOrUnknownPatientsAreIgnored() {
        SelectedPatients selected = new SelectedPatients();
        UUID clinic = UUID.randomUUID();

        selected.record(clinic, "OP1", 0, "Nobody");
        selected.record(null, "OP1", 5, "No clinic");

        assertThat(selected.recent(clinic)).isEmpty();
    }

    @Test
    void readingSelectionsNeedsPatientsAccess() {
        assertThat(new PermissionResolver().required("GET", "/api/patients/selected").permission()).isEqualTo(Permission.PATIENTS_READ);
    }
}
