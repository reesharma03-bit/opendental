package com.clinic.opendental.service.Impl;

import com.clinic.opendental.client.OpenDentalClient;
import com.clinic.opendental.config.OpenDentalConnection;
import com.clinic.opendental.exception.ApiException;
import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.repository.ClinicRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** A practice connects with just its Customer Key; webhook subscriptions are set up once, repeatably. */
class ClinicConnectionServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Clinic CLINIC = Clinic.builder().id(UUID.randomUUID()).clinicCode("SMITH")
            .baseUrl("https://api.opendental.com/api/v1").apiKey("ODFHIR dev/cust1234").isActive(true).build();

    private final OpenDentalClient client = mock(OpenDentalClient.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    private ClinicConnectionService service(String developerKey, String publicUrl) {
        return new ClinicConnectionService(mock(ClinicRepository.class), client, mock(OpenDentalConnection.class),
                mock(ResourceMirrorService.class), mock(FullSyncService.class), mock(SyncControl.class), jdbc,
                mock(PlatformTransactionManager.class), developerKey, "https://api.opendental.com/api/v1/", publicUrl, 30);
    }

    @Test
    void theCustomerKeyAloneIsEnoughAndFullKeysAreAccepted() {
        ClinicConnectionService service = service("devKEY", "");

        assertThat(service.composeKey(" cust1234 ")).isEqualTo("ODFHIR devKEY/cust1234");
        assertThat(service.composeKey("otherDev/cust1234")).isEqualTo("ODFHIR otherDev/cust1234");
        assertThat(service.composeKey("ODFHIR otherDev/cust1234")).isEqualTo("ODFHIR otherDev/cust1234");
        assertThatThrownBy(() -> service.composeKey("")).hasMessageContaining("Customer Key");
        assertThatThrownBy(() -> service("", "").composeKey("cust1234")).isInstanceOf(ApiException.class)
                .hasMessageContaining("OPENDENTAL_DEVELOPER_KEY");
        assertThat(ClinicConnectionService.maskedKey("ODFHIR devKEY/cust1234")).isEqualTo("••••1234");
        assertThat(ClinicConnectionService.codeFor("Smith & Jones Dental!")).isEqualTo("SMITH_JONES_DENTAL");
    }

    @Test
    void theOpenDentalAddressIsRequired() {
        ClinicConnectionService service = service("dev", "");
        assertThatThrownBy(() -> service.test("cust1234", " ")).hasMessageContaining("API address");
        assertThatThrownBy(() -> service.test("cust1234", "localhost:30223")).hasMessageContaining("http://");
        verifyNoInteractions(client);
    }

    @Test
    void openDentalRefusalsAreExplainedInPlainWords() {
        String badKey = ClinicConnectionService.explain(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized",
                null, "Invalid customer key".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
        assertThat(badKey).startsWith("Open Dental didn't accept this key");
        String office = ClinicConnectionService.explain(HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "x",
                null, "The eConnector is not running".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
        assertThat(office).startsWith("Open Dental couldn't reach your office");
    }

    @Test
    void withoutAPublicAddressNothingIsSentToOpenDental() {
        List<ClinicConnectionService.Webhook> result = service("dev", "").setUpWebhooks(CLINIC);

        assertThat(result).allMatch(w -> w.state().equals("off") && w.detail().contains("PUBLIC_API_URL"));
        verifyNoInteractions(client);
    }

    @Test
    void withoutANamedComputerEveryComputerSendsEvents() {
        when(jdbc.queryForList(anyString(), eq(String.class), any())).thenThrow(new RuntimeException("no clinic_webhooks table"));
        when(client.getRaw(eq("/subscriptions"), any(), any(), any())).thenReturn(JSON.createArrayNode());

        List<ClinicConnectionService.Webhook> result = service("dev", "http://localhost:8080").setUpWebhooks(CLINIC);

        assertThat(result).allMatch(w -> w.state().equals("ok"));
        // Blank means every computer on Open Dental before 26.2.1 ("All Workstations" would never fire there).
        verify(client, times(result.size())).sendRaw(eq(HttpMethod.POST), eq("/subscriptions"),
                argThat(body -> body instanceof Map<?, ?> m && "".equals(m.get("Workstation"))), any(), any());
        verify(client).sendRaw(eq(HttpMethod.POST), eq("/subscriptions"), argThat(body -> body instanceof Map<?, ?> m
                && "http://localhost:8080/api/webhooks/opendental/patient".equals(m.get("EndPointUrl"))), any(), any());
    }

    @Test
    void newerOpenDentalThatRefusesABlankComputerGetsAllWorkstations() {
        when(jdbc.queryForList(anyString(), eq(String.class), any())).thenReturn(List.of());
        when(client.getRaw(eq("/subscriptions"), any(), any(), any())).thenReturn(JSON.createArrayNode());
        when(client.sendRaw(eq(HttpMethod.POST), eq("/subscriptions"),
                argThat(b -> b instanceof Map<?, ?> m && "".equals(m.get("Workstation"))), any(), any()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", null,
                        "Workstation is required".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        List<ClinicConnectionService.Webhook> result = service("dev", "http://localhost:8080").setUpWebhooks(CLINIC);

        assertThat(result).allMatch(w -> w.state().equals("ok"));
        verify(client, times(result.size())).sendRaw(eq(HttpMethod.POST), eq("/subscriptions"),
                argThat(b -> b instanceof Map<?, ?> m && "All Workstations".equals(m.get("Workstation"))), any(), any());
    }

    @Test
    void disconnectRemovesEveryOneOfOursEvenIfOneIsAlreadyGone() throws Exception {
        ClinicRepository repo = mock(ClinicRepository.class);
        when(repo.findByIsActiveTrue()).thenReturn(List.of(CLINIC));
        when(client.getRaw(eq("/subscriptions"), any(), any(), any())).thenReturn(JSON.readTree("""
                [{"SubscriptionNum": 1, "EndPointUrl": "http://localhost:8080/api/webhooks/opendental/patient"},
                 {"SubscriptionNum": 2, "EndPointUrl": "http://localhost:8080/api/webhooks/opendental/appointment"},
                 {"SubscriptionNum": 3, "EndPointUrl": "https://another-vendor.example/hooks"}]"""));
        when(client.sendRaw(eq(HttpMethod.DELETE), eq("/subscriptions/1"), any(), any(), any()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null,
                        "Subscription not found.".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
        FullSyncService fullSync = mock(FullSyncService.class);
        ClinicConnectionService service = new ClinicConnectionService(repo, client, mock(OpenDentalConnection.class),
                mock(ResourceMirrorService.class), fullSync, mock(SyncControl.class), jdbc,
                mock(PlatformTransactionManager.class), "dev", "https://api.opendental.com/api/v1", "http://localhost:8080", 30);
        java.util.List<Runnable> later = new java.util.ArrayList<>();
        service.background = later::add;

        service.disconnect();

        // Syncing stops at once; the webhooks are removed afterwards, without the page waiting.
        verify(repo).saveAndFlush(argThat(c -> Boolean.FALSE.equals(c.getIsActive())));
        verify(fullSync).stop("Disconnected");
        verify(client, never()).sendRaw(eq(HttpMethod.DELETE), any(), any(), any(), any());
        later.forEach(Runnable::run);

        verify(client).sendRaw(eq(HttpMethod.DELETE), eq("/subscriptions/2"), any(), any(), any());
        verify(client, never()).sendRaw(eq(HttpMethod.DELETE), eq("/subscriptions/3"), any(), any(), any());
    }

    @Test
    void connectingRegistersEveryDatabaseEventAndRefreshesTheSubscriptionPage() {
        ClinicRepository repo = mock(ClinicRepository.class);
        when(repo.findByIsActiveTrue()).thenReturn(List.of());
        when(repo.findByClinicCode(anyString())).thenReturn(java.util.Optional.empty());
        when(repo.saveAndFlush(any())).thenAnswer(inv -> {
            Clinic saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(client.getClinics(anyString(), anyString())).thenReturn(List.of());
        when(client.getRaw(eq("/subscriptions"), any(), any(), any())).thenReturn(JSON.createArrayNode());
        ResourceMirrorService mirror = mock(ResourceMirrorService.class);
        FullSyncService fullSync = mock(FullSyncService.class);
        ClinicConnectionService service = new ClinicConnectionService(repo, client, mock(OpenDentalConnection.class), mirror,
                fullSync, mock(SyncControl.class), jdbc, mock(PlatformTransactionManager.class),
                "dev", "https://api.opendental.com/api/v1", "http://localhost:8080", 30);

        Map<String, Object> result = service.connect("My Test Practice", "cust1234", "http://localhost:30223/api/v1", "");

        @SuppressWarnings("unchecked")
        List<ClinicConnectionService.Webhook> webhooks = (List<ClinicConnectionService.Webhook>) result.get("webhooks");
        assertThat(webhooks).extracting(ClinicConnectionService.Webhook::event)
                .containsAll(SubscriptionRules.WATCH_TABLES).contains("PatientSelected");
        assertThat(webhooks).allMatch(w -> w.state().equals("ok"));
        for (String table : SubscriptionRules.WATCH_TABLES) {
            verify(client).sendRaw(eq(HttpMethod.POST), eq("/subscriptions"), argThat(b -> b instanceof Map<?, ?> m
                    && table.equals(m.get("WatchTable"))
                    && ("http://localhost:8080/api/webhooks/opendental/" + table.toLowerCase()).equals(m.get("EndPointUrl"))),
                    eq("http://localhost:30223/api/v1"), eq("ODFHIR dev/cust1234"));
        }
        // The Subscription page reads SmileOS's copy: it is refreshed before the first full sync starts.
        var order = inOrder(mirror, fullSync);
        order.verify(mirror).sync(any(), argThat(r -> r != null && r.resource().equals("subscriptions")), any());
        order.verify(fullSync).startFullSync("connect");
    }

    @Test
    void settingUpAddsOnlyWhatIsMissingAndCorrectsOurOwn() throws Exception {
        when(jdbc.queryForList(anyString(), eq(String.class), any())).thenReturn(List.of("FRONTDESK-PC"));
        ArrayNode existing = (ArrayNode) JSON.readTree("""
                [
                 {"SubscriptionNum": 1, "WatchTable": "Patient", "Workstation": "FRONTDESK-PC", "PollingSeconds": 30,
                  "EndPointUrl": "https://smile.example/api/webhooks/opendental/patient"},
                 {"SubscriptionNum": 2, "WatchTable": "Appointment", "Workstation": "FRONTDESK-PC", "PollingSeconds": 30,
                  "EndPointUrl": "https://old.example/api/webhooks/opendental/appointment"},
                 {"SubscriptionNum": 3, "WatchTable": "Schedule", "Workstation": "X", "PollingSeconds": 5,
                  "EndPointUrl": "https://another-vendor.example/hooks"}
                ]""");
        when(client.getRaw(eq("/subscriptions"), any(), any(), any())).thenReturn(existing);

        List<ClinicConnectionService.Webhook> result = service("dev", "https://smile.example/").setUpWebhooks(CLINIC);

        int wanted = SubscriptionRules.WATCH_TABLES.size() + SubscriptionRules.UI_EVENT_TYPES.size();
        assertThat(result).hasSize(wanted).allMatch(w -> w.state().equals("ok"));
        // Patient is already right; Appointment points at an old address; everything else is added,
        // including Schedule (the other vendor's subscription is not ours and is left alone).
        verify(client).sendRaw(eq(HttpMethod.PUT), eq("/subscriptions/2"),
                eq(Map.of("EndPointUrl", "https://smile.example/api/webhooks/opendental/appointment")), any(), any());
        verify(client, times(wanted - 2)).sendRaw(eq(HttpMethod.POST), eq("/subscriptions"), any(), any(), any());
        verify(client, never()).sendRaw(eq(HttpMethod.PUT), eq("/subscriptions/1"), any(), any(), any());
        verify(client, never()).sendRaw(any(), eq("/subscriptions/3"), any(), any(), any());
        verify(client).sendRaw(eq(HttpMethod.POST), eq("/subscriptions"), argThat(body -> body instanceof Map<?, ?> m
                && "PatientSelected".equals(m.get("UiEventType")) && "".equals(m.get("Workstation"))
                && !m.containsKey("PollingSeconds")), any(), any());
        verify(client).sendRaw(eq(HttpMethod.POST), eq("/subscriptions"), argThat(body -> body instanceof Map<?, ?> m
                && "Schedule".equals(m.get("WatchTable")) && "FRONTDESK-PC".equals(m.get("Workstation"))
                && Integer.valueOf(30).equals(m.get("PollingSeconds"))
                && "https://smile.example/api/webhooks/opendental/schedule".equals(m.get("EndPointUrl"))), any(), any());
    }
}
