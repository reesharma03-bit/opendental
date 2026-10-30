package com.clinic.opendental.service.Impl;

import com.clinic.opendental.client.OpenDentalClient;
import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.service.Impl.OdResourceCatalog.Resource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Copying Open Dental resources into od_resource_records: every page is read, rows are
 * keyed by their Open Dental key, and rows are only pruned after a clean fetch.
 */
class ResourceMirrorServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Clinic CLINIC = Clinic.builder()
            .id(UUID.randomUUID()).clinicCode("CLINIC_A").baseUrl("http://od").apiKey("key").build();
    private static final Timestamp RUN_START = Timestamp.from(Instant.now());

    private OpenDentalClient client;
    private JdbcTemplate jdbc;
    private ResourceMirrorService service;

    @BeforeEach
    void setUp() {
        client = mock(OpenDentalClient.class);
        jdbc = mock(JdbcTemplate.class);
        service = new ResourceMirrorService(client, jdbc, mock(org.springframework.transaction.PlatformTransactionManager.class));
    }

    @Test
    void readsEveryPageOfAList() {
        Resource carriers = new Resource("carriers", "/carriers", "CarrierNum", null, null);
        when(client.getRaw(eq("/carriers"), eq(Map.of()), any(), any())).thenReturn(rows("CarrierNum", 1, 100));
        when(client.getRaw(eq("/carriers"), eq(Map.of("Offset", "100")), any(), any())).thenReturn(rows("CarrierNum", 101, 30));

        ResourceMirrorService.Result result = service.sync(CLINIC, carriers, RUN_START);

        assertThat(result.records()).isEqualTo(130);
        assertThat(result.ok()).isTrue();
        verify(client, times(2)).getRaw(eq("/carriers"), anyMap(), eq("http://od"), eq("key"));
        List<Object[]> saved = savedRows(2); // saved page by page
        assertThat(saved).hasSize(130);
        assertThat(saved.get(0)[3]).isEqualTo("1");
        assertThat(saved.get(129)[3]).isEqualTo("130");
        // Clean fetch: rows Open Dental no longer has are removed.
        verify(jdbc).update(startsWith("DELETE FROM od_resource_records"), eq(CLINIC.getId()), eq("carriers"), eq(RUN_START), any(UUID.class));
    }

    @Test
    void stopsWhenAnEndpointIgnoresOffset() {
        Resource fees = new Resource("fees", "/fees", "FeeNum", null, null);
        when(client.getRaw(eq("/fees"), anyMap(), any(), any())).thenReturn(rows("FeeNum", 1, 100));

        ResourceMirrorService.Result result = service.sync(CLINIC, fees, RUN_START);

        assertThat(result.records()).isEqualTo(100);
        verify(client, times(2)).getRaw(eq("/fees"), anyMap(), any(), any());
    }

    @Test
    void failedListKeepsExistingRows() {
        Resource claims = new Resource("claims", "/claims", "ClaimNum", null, null);
        when(client.getRaw(eq("/claims"), anyMap(), any(), any())).thenThrow(new ResourceAccessException("timeout"));

        ResourceMirrorService.Result result = service.sync(CLINIC, claims, RUN_START);

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("timeout");
        verify(jdbc, never()).update(startsWith("DELETE FROM od_resource_records"), any(), any(), any(), any());
    }

    @Test
    void perPatientResourceQueriesEachPatientAndTagsRowsWithIt() {
        Resource allergies = new Resource("allergies", "/allergies", "AllergyNum", OdResourceCatalog.PATIENTS, "PatNum");
        when(jdbc.queryForList(startsWith("SELECT pat_num FROM patients"), eq(Long.class), any()))
                .thenReturn(List.of(7L, 8L));
        when(client.getRaw(eq("/allergies"), eq(Map.of("PatNum", "7")), any(), any()))
                .thenReturn(array(JSON.createObjectNode().put("AllergyNum", 70)));
        when(client.getRaw(eq("/allergies"), eq(Map.of("PatNum", "8")), any(), any()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null));

        ResourceMirrorService.Result result = service.sync(CLINIC, allergies, RUN_START);

        assertThat(result.ok()).isTrue();
        assertThat(result.records()).isEqualTo(1);
        Object[] row = savedRows(1).get(0);
        assertThat(row[3]).isEqualTo("70");
        assertThat(row[4]).isEqualTo(7L); // pat_num from the patient queried
    }

    @Test
    void discountPlanSubsAreNotAskedPerPatientWhenTheClinicHasNoDiscountPlans() {
        Resource subs = new Resource("discountplansubs", "/discountplansubs", "DiscountSubNum", OdResourceCatalog.PATIENTS, "PatNum");
        when(client.getRaw(eq("/discountplans"), anyMap(), any(), any())).thenReturn(JSON.createArrayNode());
        when(jdbc.queryForList(startsWith("SELECT pat_num FROM patients"), eq(Long.class), any())).thenReturn(List.of(7L, 8L));

        ResourceMirrorService.Result result = service.sync(CLINIC, subs, RUN_START);

        assertThat(result.ok()).isTrue();
        assertThat(result.records()).isZero();
        verify(client, never()).getRaw(eq("/discountplansubs"), anyMap(), any(), any());
    }

    @Test
    void discountPlanSubsAreAskedPerPatientWhenTheClinicHasDiscountPlans() {
        Resource subs = new Resource("discountplansubs", "/discountplansubs", "DiscountSubNum", OdResourceCatalog.PATIENTS, "PatNum");
        when(client.getRaw(eq("/discountplans"), anyMap(), any(), any()))
                .thenReturn(array(JSON.createObjectNode().put("DiscountPlanNum", 1)));
        when(jdbc.queryForList(startsWith("SELECT pat_num FROM patients"), eq(Long.class), any())).thenReturn(List.of(7L));
        when(client.getRaw(eq("/discountplansubs"), eq(Map.of("PatNum", "7")), any(), any()))
                .thenReturn(JSON.createObjectNode().put("DiscountSubNum", 32));

        ResourceMirrorService.Result result = service.sync(CLINIC, subs, RUN_START);

        assertThat(result.records()).isEqualTo(1);
    }

    @Test
    void changedScopeAsksOnlyPatientsAddedOrChangedThisWeek() {
        Resource races = new Resource("patientraces", "/patientraces", "PatientRaceNum", OdResourceCatalog.PATIENTS, "PatNum");
        when(jdbc.queryForList(contains("interval '7 days'"), eq(Long.class), any())).thenReturn(List.of(9L));

        service.sync(CLINIC, races, RUN_START, ResourceMirrorService.Scope.CHANGED);

        verify(jdbc).queryForList(argThat((String sql) -> sql.contains("updated_at >=") && !sql.contains("% 7")), eq(Long.class), any());
        verify(client).getRaw(eq("/patientraces"), eq(Map.of("PatNum", "9")), any(), any());
        verify(client, times(1)).getRaw(eq("/patientraces"), anyMap(), any(), any());
    }

    @Test
    void stoppedPartWayKeepsEverythingAndContinuesAfterTheLastPatientDone() {
        Resource allergies = new Resource("allergies", "/allergies", "AllergyNum", OdResourceCatalog.PATIENTS, "PatNum");
        when(jdbc.queryForList(startsWith("SELECT pat_num FROM patients"), eq(Long.class), any()))
                .thenReturn(List.of(7L, 8L, 9L));
        when(client.getRaw(eq("/allergies"), any(), any(), any())).thenReturn(array());

        ResourceMirrorService.Progress[] holder = new ResourceMirrorService.Progress[1];
        holder[0] = new ResourceMirrorService.Progress(() -> holder[0].parentsDone >= 1, null); // e.g. disconnected
        ResourceMirrorService.Result stopped = service.sync(CLINIC, allergies, RUN_START, ResourceMirrorService.Scope.ALL, holder[0]);

        assertThat(holder[0].stopped).isTrue();
        assertThat(holder[0].lastParent).isEqualTo(7L);
        assertThat(stopped.error()).isEqualTo("stopped");
        verify(jdbc, never()).update(startsWith("DELETE FROM od_resource_records"), any(), any(), any(), any());

        clearInvocations(client);
        ResourceMirrorService.Progress next = new ResourceMirrorService.Progress(() -> false, 7L);
        ResourceMirrorService.Result rest = service.sync(CLINIC, allergies, RUN_START, ResourceMirrorService.Scope.ALL, next);

        assertThat(rest.ok()).isTrue();
        assertThat(next.parentsDone).isEqualTo(3);
        assertThat(next.parentsTotal).isEqualTo(3);
        verify(client, never()).getRaw(eq("/allergies"), eq(Map.of("PatNum", "7")), any(), any());
        verify(client).getRaw(eq("/allergies"), eq(Map.of("PatNum", "9")), any(), any());
        // Only what belongs to the patients visited now may be removed.
        verify(jdbc).update(contains("pat_num = ANY"), any(), any(), any(), any(), eq("{8,9}"));
    }

    @Test
    void failureForOneParentKeepsExistingRows() {
        Resource popups = new Resource("popups", "/popups", "PopupNum", OdResourceCatalog.PATIENTS, "PatNum");
        when(jdbc.queryForList(startsWith("SELECT pat_num FROM patients"), eq(Long.class), any()))
                .thenReturn(List.of(7L, 8L));
        when(client.getRaw(eq("/popups"), eq(Map.of("PatNum", "7")), any(), any())).thenReturn(array());
        when(client.getRaw(eq("/popups"), eq(Map.of("PatNum", "8")), any(), any()))
                .thenThrow(new ResourceAccessException("reset"));

        ResourceMirrorService.Result result = service.sync(CLINIC, popups, RUN_START);

        assertThat(result.ok()).isFalse();
        assertThat(result.failedCalls()).isEqualTo(1);
        verify(jdbc, never()).update(startsWith("DELETE FROM od_resource_records"), any(), any(), any(), any());
    }

    @Test
    void parentInPathRowsAreKeyedPerParent() {
        Resource family = new Resource("familymodules", "/familymodules/{id}/Insurance", "InsSubNum",
                OdResourceCatalog.PATIENTS, "PatNum");
        JsonNode row = JSON.createObjectNode().put("InsSubNum", 5);

        assertThat(ResourceMirrorService.recordKey(family, row, 48L)).isEqualTo("48:5");
    }

    @Test
    void rowWithoutItsKeyIsKeyedByContent() {
        Resource notes = new Resource("procnotes", "/procnotes", "ProcNoteNum", null, null);
        JsonNode a = JSON.createObjectNode().put("Note", "a");
        JsonNode b = JSON.createObjectNode().put("Note", "b");

        assertThat(ResourceMirrorService.recordKey(notes, a, null))
                .hasSize(64)
                .isNotEqualTo(ResourceMirrorService.recordKey(notes, b, null))
                .isEqualTo(ResourceMirrorService.recordKey(notes, a.deepCopy(), null));
    }

    @Test
    void fixedParametersAskForRecordsOpenDentalHidesByDefault() {
        Resource taskLists = OdResourceCatalog.LISTS.stream().filter(r -> r.resource().equals("tasklists")).findFirst().orElseThrow();
        when(client.getRaw(eq("/tasklists"), eq(Map.of("TaskListStatus", "Active")), any(), any())).thenReturn(rows("TaskListNum", 1, 2));
        when(client.getRaw(eq("/tasklists"), eq(Map.of("TaskListStatus", "Archived")), any(), any())).thenReturn(rows("TaskListNum", 3, 1));

        ResourceMirrorService.Result result = service.sync(CLINIC, taskLists, RUN_START);

        assertThat(result.records()).isEqualTo(3);
        Resource definitions = OdResourceCatalog.LISTS.stream().filter(r -> r.resource().equals("definitions")).findFirst().orElseThrow();
        assertThat(definitions.passes()).containsExactly(Map.of("includeHidden", "true"));
        Resource tasks = OdResourceCatalog.LISTS.stream().filter(r -> r.resource().equals("tasks")).findFirst().orElseThrow();
        assertThat(tasks.passes().get(0)).containsKey("DateTimeOriginal");
    }

    @Test
    void everySyncedResourceHasAPermissionArea() {
        com.clinic.opendental.security.PermissionResolver resolver = new com.clinic.opendental.security.PermissionResolver();
        OdResourceCatalog.LISTS.forEach(r -> assertThat(resolver.area(r.resource())).as(r.resource()).isNotNull());
        OdResourceCatalog.PER_PARENT.forEach(r -> assertThat(resolver.area(r.resource())).as(r.resource()).isNotNull());
    }

    @Test
    void catalogResourcesAreUniqueAndParentsComeFirst() {
        List<String> seen = new ArrayList<>();
        for (Resource r : OdResourceCatalog.LISTS) {
            assertThat(seen).doesNotContain(r.resource());
            seen.add(r.resource());
        }
        for (Resource r : OdResourceCatalog.PER_PARENT) {
            assertThat(seen).doesNotContain(r.resource());
            if (!r.parent().equals(OdResourceCatalog.PATIENTS) && !r.parent().equals(OdResourceCatalog.APPOINTMENTS)) {
                assertThat(seen).as("parent of " + r.resource()).contains(r.parent());
            }
            seen.add(r.resource());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> savedRows(int batches) {
        ArgumentCaptor<List<Object[]>> captor = ArgumentCaptor.forClass(List.class);
        verify(jdbc, times(batches)).batchUpdate(startsWith("INSERT INTO od_sync_staging"), captor.capture());
        List<Object[]> all = new ArrayList<>();
        captor.getAllValues().forEach(all::addAll);
        return all;
    }

    private static ArrayNode rows(String key, int first, int count) {
        ArrayNode array = JSON.createArrayNode();
        for (int i = 0; i < count; i++) {
            array.add(JSON.createObjectNode().put(key, first + i));
        }
        return array;
    }

    private static ArrayNode array(JsonNode... items) {
        ArrayNode array = JSON.createArrayNode();
        for (JsonNode item : items) {
            array.add(item);
        }
        return array;
    }

    @Test
    void treatPlanChangesArePulledWithTheirDocumentedSecDateTEditFilter() {
        Resource treatplans = OdResourceCatalog.find("treatplans");
        ArrayNode changed = JSON.createArrayNode();
        changed.add(JSON.createObjectNode().put("TreatPlanNum", 7).put("SecDateTEdit", "2026-10-04 10:15:00"));
        when(client.getRaw(eq("/treatplans"), anyMap(), any(), any())).thenReturn(changed);

        ResourceMirrorService.Result result = service.syncChanged(CLINIC, treatplans, java.time.LocalDateTime.of(2026, 10, 4, 10, 0));

        verify(client).getRaw("/treatplans", Map.of("SecDateTEdit", "2026-10-04 10:00:00"), "http://od", "key");
        assertThat(result.records()).isEqualTo(1);
        assertThat(result.latestChange()).isEqualTo(Timestamp.valueOf("2026-10-04 10:15:00"));
    }

    @Test
    void anUndocumentedFilterThatOpenDentalIgnoresIsNotUsed() {
        Resource allergies = OdResourceCatalog.find("allergies");
        when(jdbc.queryForObject(contains("max(od_tstamp)"), eq(Timestamp.class), any(), any()))
                .thenReturn(Timestamp.valueOf("2026-10-04 10:00:00"));
        ArrayNode everything = JSON.createArrayNode();
        everything.add(JSON.createObjectNode().put("AllergyNum", 1).put("DateTStamp", "2020-01-01 00:00:00"));
        everything.add(JSON.createObjectNode().put("AllergyNum", 2).put("DateTStamp", "2026-10-04 10:00:00"));
        when(client.getRaw(eq("/allergies"), anyMap(), any(), any())).thenReturn(everything);

        assertThat(service.acceptsChangedSince(CLINIC, allergies)).isFalse();
    }

    @Test
    void anUndocumentedFilterIsUsedOnceOpenDentalIsShownToHonourIt() {
        Resource hist = OdResourceCatalog.find("histappointments");
        when(jdbc.queryForObject(contains("max(od_tstamp)"), eq(Timestamp.class), any(), any()))
                .thenReturn(Timestamp.valueOf("2026-10-04 10:00:00"));
        ArrayNode newest = JSON.createArrayNode();
        newest.add(JSON.createObjectNode().put("HistApptNum", 9).put("HistDateTStamp", "2026-10-04 10:00:00"));
        when(client.getRaw(eq("/histappointments"), eq(Map.of("HistDateTStamp", "2099-01-01 00:00:00")), any(), any()))
                .thenReturn(JSON.createArrayNode());
        when(client.getRaw(eq("/histappointments"), eq(Map.of("HistDateTStamp", "2026-10-04 09:59:59")), any(), any()))
                .thenReturn(newest);

        assertThat(service.acceptsChangedSince(CLINIC, hist)).isTrue();

        service.syncChanged(CLINIC, hist, java.time.LocalDateTime.of(2026, 10, 4, 9, 0));
        verify(client).getRaw("/histappointments", Map.of("HistDateTStamp", "2026-10-04 09:00:00"), "http://od", "key");
    }

    @Test
    void aDocumentedFilterIsCheckedWithItsOwnParameter() {
        Resource commlogs = OdResourceCatalog.find("commlogs");
        when(jdbc.queryForObject(contains("od_tstamp IS NOT NULL"), eq(Boolean.class), any(), any())).thenReturn(true);
        when(client.getRaw(eq("/commlogs"), anyMap(), any(), any())).thenReturn(JSON.createArrayNode());

        assertThat(service.acceptsChangedSince(CLINIC, commlogs)).isTrue();
        verify(client).getRaw("/commlogs", Map.of("DateTStamp", "2099-01-01 00:00:00"), "http://od", "key");
    }

    @Test
    void histAppointmentsAreCheckpointedByHistDateTStamp() {
        Resource hist = OdResourceCatalog.find("histappointments");
        ArrayNode rows = JSON.createArrayNode();
        rows.add(JSON.createObjectNode().put("HistApptNum", 1).put("HistDateTStamp", "2026-10-04 11:00:00")
                .put("DateTStamp", "2026-10-04 09:00:00"));
        when(client.getRaw(eq("/histappointments"), anyMap(), any(), any())).thenReturn(rows);

        ResourceMirrorService.Result result = service.sync(CLINIC, hist, RUN_START);

        assertThat(result.latestChange()).isEqualTo(Timestamp.valueOf("2026-10-04 11:00:00"));
    }

    @Test
    void otherListedScreensAreVerifiedTheSameStrictWay() {
        Resource carriers = OdResourceCatalog.find("carriers");
        when(jdbc.queryForObject(contains("max(od_tstamp)"), eq(Timestamp.class), any(), any()))
                .thenReturn(Timestamp.valueOf("2026-10-04 10:00:00"));
        // Nothing after a far-future date, but older records for a recent one: not really filtering.
        when(client.getRaw(eq("/carriers"), eq(Map.of("DateTStamp", "2099-01-01 00:00:00")), any(), any()))
                .thenReturn(JSON.createArrayNode());
        ArrayNode older = JSON.createArrayNode();
        older.add(JSON.createObjectNode().put("CarrierNum", 1).put("DateTStamp", "2025-01-01 00:00:00"));
        when(client.getRaw(eq("/carriers"), eq(Map.of("DateTStamp", "2026-10-04 09:59:59")), any(), any())).thenReturn(older);

        assertThat(service.acceptsChangedSince(CLINIC, carriers)).isFalse();
    }
}
