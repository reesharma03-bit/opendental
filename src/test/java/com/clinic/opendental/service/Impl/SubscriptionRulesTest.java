package com.clinic.opendental.service.Impl;

import com.clinic.opendental.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Open Dental's subscription rules (apisubscriptions.html), enforced before anything is saved. */
class SubscriptionRulesTest {

    private static final String URL = "https://smileos.example/api/webhooks/opendental/appointment";

    @Test
    void databaseAndUiEventsCanBeCreated() {
        assertThatCode(() -> SubscriptionRules.checkCreate(Map.of(
                "EndPointUrl", URL, "Workstation", "All Workstations", "WatchTable", "Appointment", "PollingSeconds", 30,
                "DateTimeStart", "2026-10-04 00:00:00", "DateTimeStop", "0001-01-01 00:00:00")))
                .doesNotThrowAnyException();
        assertThatCode(() -> SubscriptionRules.checkCreate(Map.of(
                "EndPointUrl", "http://localhost:9000/events", "Workstation", "FRONTDESK1", "UiEventType", "PatientSelected")))
                .doesNotThrowAnyException();
    }

    @Test
    void aDatabaseEventNeedsAKnownTableAndPolling() {
        assertThatThrownBy(() -> SubscriptionRules.checkCreate(Map.of("EndPointUrl", URL, "WatchTable", "Claims", "PollingSeconds", 30)))
                .isInstanceOf(ApiException.class).hasMessageContaining("WatchTable must be one of");
        assertThatThrownBy(() -> SubscriptionRules.checkCreate(Map.of("EndPointUrl", URL, "WatchTable", "Patient")))
                .hasMessageContaining("PollingSeconds is required");
        assertThatThrownBy(() -> SubscriptionRules.checkCreate(Map.of("EndPointUrl", URL, "WatchTable", "Patient", "PollingSeconds", 0)))
                .hasMessageContaining("at least 1");
    }

    @Test
    void aSubscriptionIsOneKindOrTheOther() {
        assertThatThrownBy(() -> SubscriptionRules.checkCreate(Map.of("EndPointUrl", URL)))
                .hasMessageContaining("Give either WatchTable");
        assertThatThrownBy(() -> SubscriptionRules.checkCreate(Map.of(
                "EndPointUrl", URL, "WatchTable", "Patient", "PollingSeconds", 5, "UiEventType", "PatientSelected")))
                .hasMessageContaining("not both");
        assertThatThrownBy(() -> SubscriptionRules.checkCreate(Map.of("EndPointUrl", URL, "UiEventType", "PatientSelected", "PollingSeconds", 5)))
                .hasMessageContaining("only applies to database events");
        assertThatThrownBy(() -> SubscriptionRules.checkCreate(Map.of("EndPointUrl", "ftp://x", "UiEventType", "PatientSelected")))
                .hasMessageContaining("http(s)");
    }

    @Test
    void anUpdateMayOnlyChangeWhatOpenDentalAllows() {
        assertThatCode(() -> SubscriptionRules.checkUpdate(Map.of("PollingSeconds", 60, "Note", "slower"),
                Map.of("WatchTable", "Appointment"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> SubscriptionRules.checkUpdate(Map.of("WatchTable", "Patient"), Map.of("WatchTable", "Appointment")))
                .hasMessageContaining("remove the subscription and add a new one");
        assertThatThrownBy(() -> SubscriptionRules.checkUpdate(Map.of("PollingSeconds", 60), Map.of("UiEventType", "PatientSelected")))
                .hasMessageContaining("only applies to database events");
        assertThatThrownBy(() -> SubscriptionRules.checkUpdate(Map.of("DateTimeStop", "next week"), null))
                .hasMessageContaining("DateTimeStop must look like");
    }
}
