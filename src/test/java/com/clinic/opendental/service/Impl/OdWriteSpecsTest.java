package com.clinic.opendental.service.Impl;

import com.clinic.opendental.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The write specs (from Open Dental's API pages) and the operations the dashboard offers stay in step. */
class OdWriteSpecsTest {

    @Test
    void everyWritableResourceHasASpecThatMatchesItsOperations() {
        OdResourceCatalog.WRITABLE.forEach((resource, writable) -> {
            WriteSpec spec = OdWriteSpecs.of(resource);
            assertThat(spec).as("write spec for " + resource).isNotNull();
            assertThat(spec.canCreate()).as(resource + " create").isEqualTo(writable.create());
            assertThat(spec.canUpdate()).as(resource + " update").isEqualTo(writable.update());
            assertThat(spec.delete()).as(resource + " delete").isEqualTo(writable.delete());
        });
        OdWriteSpecs.SPECS.keySet().forEach(resource -> {
            assertThat(OdResourceCatalog.WRITABLE).as("operations for " + resource).containsKey(resource);
            assertThat(OdResourceCatalog.find(resource)).as(resource + " is synced").isNotNull();
        });
    }

    @Test
    void specsParseRequiredFieldsKindsAndAllowedValues() {
        WriteSpec guardians = OdWriteSpecs.of("guardians");
        WriteSpec.Field child = guardians.create().get(0);
        assertThat(child.name()).isEqualTo("PatNumChild");
        assertThat(child.required()).isTrue();
        assertThat(child.kind()).isEqualTo("patient");
        WriteSpec.Field relationship = guardians.create().stream().filter(f -> f.name().equals("Relationship")).findFirst().orElseThrow();
        assertThat(relationship.kind()).isEqualTo("select");
        assertThat(relationship.options()).contains("Mother", "Sitter");
        assertThat(guardians.delete()).isTrue();

        WriteSpec commlogs = OdWriteSpecs.of("commlogs");
        assertThat(commlogs.create().stream().filter(f -> f.name().equals("Mode_")).findFirst().orElseThrow().options()).contains("In Person");
        assertThat(OdWriteSpecs.of("inssubs").create().stream().filter(f -> f.name().equals("DateTerm")).findFirst().orElseThrow().kind()).isEqualTo("date");
        assertThat(OdWriteSpecs.of("toothinitials").create().stream().filter(f -> f.name().equals("ToothNum")).findFirst().orElseThrow().kind()).isEqualTo("text");
        assertThat(OdWriteSpecs.of("claims").create().stream().filter(f -> f.name().equals("procNums")).findFirst().orElseThrow().kind()).isEqualTo("list");
    }

    @Test
    void readOnlyResourcesOfferNothing() {
        for (String resource : List.of("operatories", "schedules", "scheduleops", "rxpats", "pharmacies", "recalltypes",
                "sheetdefs", "sheetfielddefs", "tasklists", "quickpastecats", "quickpastenotes", "usergroups", "usergroupattaches",
                "claimforms", "clockevents", "histappointments", "etranss", "familymodules", "patientraces")) {
            assertThat(OdResourceCatalog.WRITABLE).as(resource).doesNotContainKey(resource);
        }
    }

    @Test
    void changesAreCheckedAgainstWhatOpenDentalAccepts() {
        WriteSpec fees = OdWriteSpecs.of("fees");
        assertThatCode(() -> fees.checkCreate("fees", Map.of("Amount", 125.5, "FeeSched", 53, "CodeNum", 1))).doesNotThrowAnyException();
        assertThatThrownBy(() -> fees.checkCreate("fees", Map.of("Amount", 125.5)))
                .isInstanceOf(ApiException.class).hasMessageContaining("FeeSched is required").hasMessageContaining("CodeNum is required");
        assertThatThrownBy(() -> fees.checkUpdate("fees", Map.of("CodeNum", 2)))
                .hasMessageContaining("CodeNum can't be changed on fees");
        assertThatThrownBy(() -> OdWriteSpecs.of("guardians").checkCreate("guardians",
                Map.of("PatNumChild", 1, "PatNumGuardian", 2, "Relationship", "Uncle")))
                .hasMessageContaining("Relationship must be one of");
        assertThatThrownBy(() -> OdWriteSpecs.of("diseases").checkUpdate("diseases", Map.of("DateStart", "07/01/2026")))
                .hasMessageContaining("DateStart must be a date");
    }
}
