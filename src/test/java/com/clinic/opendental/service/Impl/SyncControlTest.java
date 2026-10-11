package com.clinic.opendental.service.Impl;

import com.clinic.opendental.model.Clinic;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** What a clinic's choice of screens means for the sync. */
class SyncControlTest {

    private static final Clinic CLINIC = Clinic.builder().id(UUID.randomUUID()).clinicCode("A").build();

    private static SyncControl choosing(Set<String> chosen) {
        SyncControl control = spy(new SyncControl(mock(JdbcTemplate.class), mock(PlatformTransactionManager.class)));
        doReturn(chosen).when(control).chosen(CLINIC.getId());
        return control;
    }

    @Test
    void withoutAChoiceEverythingIsSynced() {
        SyncControl control = choosing(null);

        assertThat(control.skips(CLINIC, "claims")).isFalse();
        assertThat(control.skips(CLINIC, "patients")).isFalse();
    }

    @Test
    void onlyChosenResourcesAndWhatTheyAreReadThroughAreSynced() {
        SyncControl control = choosing(Set.of("claims", "allergies", "periomeasures"));

        assertThat(control.skips(CLINIC, "claims")).isFalse();
        assertThat(control.skips(CLINIC, "allergies")).isFalse();
        // Allergies are read per patient, perio measures per perio exam.
        assertThat(control.skips(CLINIC, "patients")).isFalse();
        assertThat(control.skips(CLINIC, "perioexams")).isFalse();
        assertThat(control.skips(CLINIC, "appointments")).isTrue();
        assertThat(control.skips(CLINIC, "payments")).isTrue();
        assertThat(SyncControl.addedForDependencies(Set.of("claims", "allergies", "periomeasures")))
                .containsExactlyInAnyOrder("patients", "perioexams");
    }

    @Test
    void parentsOfParentsAreFollowed() {
        // Pay plan charges are read per pay plan, pay plans per patient.
        assertThat(SyncControl.addedForDependencies(Set.of("payplancharges")))
                .containsExactlyInAnyOrder("payplans", "patients");
    }

    @Test
    void everyCatalogResourceAndCoreTableCanBeChosen() {
        assertThat(SyncControl.SYNCABLE).contains("patients", "appointments", "procedurelogs", "documents",
                "claims", "allergies", "userods");
        assertThat(SyncControl.SYNCABLE).doesNotHaveDuplicates();
        SyncControl control = new SyncControl(mock(JdbcTemplate.class), mock(PlatformTransactionManager.class));
        assertThatThrownBy(() -> control.choose(CLINIC.getId(), List.of("claims", "chartmodules"), "me"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chartmodules");
    }

    @Test
    void withoutTheSettingsTablesSyncIsNotPaused() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class)))
                .thenThrow(new org.springframework.jdbc.BadSqlGrammarException("q", "SELECT", new java.sql.SQLException("no table")));
        assertThat(new SyncControl(jdbc, mock(PlatformTransactionManager.class)).paused()).isFalse();
    }
}
