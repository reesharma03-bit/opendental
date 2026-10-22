package com.clinic.opendental.service.Impl;

import com.clinic.opendental.client.OpenDentalClient;
import com.clinic.opendental.security.AppUser;
import com.clinic.opendental.security.AppUserRepository;
import com.clinic.opendental.security.Role;
import com.clinic.opendental.security.TestSession;
import org.junit.jupiter.api.AfterEach;
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
        return new ClinicConnectionService(mock(ClinicRepository.class), client, mock(AppUserRepository.class),
                mock(ResourceMirrorService.class), mock(FullSyncService.class), mock(SyncControl.class), jdbc,
                mock(PlatformTransactionManager.class), developerKey, "https://api.opendental.com/api/v1/", publicUrl, 30);
    }

    @AfterEach
    void signOut() {
        TestSession.clear();
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
        TestSession.signIn(Role.ADMIN, null);
        when(client.getRaw(eq("/subscriptions"), any(), any(), any())).thenReturn(JSON.readTree("""
                [{"SubscriptionNum": 1, "EndPointUrl": "http://localhost:8080/api/webhooks/opendental/patient"},
                 {"SubscriptionNum": 2, "EndPointUrl": "http://localhost:8080/api/webhooks/opendental/appointment"},
                 {"SubscriptionNum": 3, "EndPointUrl": "https://another-vendor.example/hooks"}]"""));
        when(client.sendRaw(eq(HttpMethod.DELETE), eq("/subscriptions/1"), any(), any(), any()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null,
                        "Subscription not found.".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
        FullSyncService fullSync = mock(FullSyncService.class);
        ClinicConnectionService service = new ClinicConnectionService(repo, client, mock(AppUserRepository.class),
                mock(ResourceMirrorService.class), fullSync, mock(SyncControl.class), jdbc,
                mock(PlatformTransactionManager.class), "dev", "https://api.opendental.com/api/v1", "http://localhost:8080", 30);
        java.util.List<Runnable> later = new java.util.ArrayList<>();
        service.background = later::add;

        service.disconnect();

        // Syncing stops at once; the webhooks are removed afterwards, without the page waiting.
        verify(repo).saveAndFlush(argThat(c -> Boolean.FALSE.equals(c.getIsActive())));
        verify(fullSync).stop(CLINIC.getId(), "Disconnected");
        verify(client, never()).sendRaw(eq(HttpMethod.DELETE), any(), any(), any(), any());
        later.forEach(Runnable::run);

        verify(client).sendRaw(eq(HttpMethod.DELETE), eq("/subscriptions/2"), any(), any(), any());
        verify(client, never()).sendRaw(eq(HttpMethod.DELETE), eq("/subscriptions/3"), any(), any(), any());
    }

    @Test
    void aDentistRegistersTheirClinicOwnsItAndEveryDatabaseEventIsSetUp() {
        UUID dentistId = TestSession.signIn(Role.DENTIST, null);
        AppUser dentist = AppUser.builder().id(dentistId).email("dentist@example.com").role(Role.DENTIST).build();
        AppUserRepository users = mock(AppUserRepository.class);
        when(users.findById(dentistId)).thenReturn(java.util.Optional.of(dentist));
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
        ClinicConnectionService service = new ClinicConnectionService(repo, client, users, mirror,
                fullSync, mock(SyncControl.class), jdbc, mock(PlatformTransactionManager.class),
                "dev", "https://api.opendental.com/api/v1", "http://localhost:8080", 30);
        java.util.List<Runnable> later = new java.util.ArrayList<>();
        service.background = later::add;

        Map<String, Object> result = service.register("My Test Practice", "cust1234", "http://localhost:30223/api/v1", "");

        // Answers once the key is tested and the clinic saved; the webhooks follow.
        assertThat(result.get("message")).isEqualTo("Connected to Open Dental.");
        assertThat(result.get("clinicCode")).isEqualTo("MY_TEST_PRACTICE");
        assertThat(result.get("ownerEmail")).isEqualTo("dentist@example.com");
        verify(repo).saveAndFlush(argThat(c -> dentistId.equals(c.getOwnerUserId())));
        verify(users, never()).save(any()); // ownership is on the clinic, not the user
        verify(client, never()).sendRaw(any(), any(), any(), any(), any());
        verify(fullSync, never()).startFullSync(any(), any());
        later.forEach(Runnable::run);

        for (String table : SubscriptionRules.WATCH_TABLES) {
            verify(client).sendRaw(eq(HttpMethod.POST), eq("/subscriptions"), argThat(b -> b instanceof Map<?, ?> m
                    && table.equals(m.get("WatchTable"))
                    && ("http://localhost:8080/api/webhooks/opendental/" + table.toLowerCase()).equals(m.get("EndPointUrl"))),
                    eq("http://localhost:30223/api/v1"), eq("ODFHIR dev/cust1234"));
        }
        // The Subscription page reads SmileOS's copy: it is refreshed before the first full sync starts.
        var order = inOrder(mirror, fullSync);
        order.verify(mirror).sync(any(), argThat(r -> r != null && r.resource().equals("subscriptions")), any());
        order.verify(fullSync).startFullSync(eq("connect"), any());
    }

    @Test
    void aDentistCanRegisterSeveralClinicsAndCantGiveThemToSomeoneElse() {
        UUID dentistId = TestSession.signIn(Role.DENTIST, null);
        AppUser dentist = AppUser.builder().id(dentistId).email("dentist@example.com").role(Role.DENTIST).active(true).build();
        AppUserRepository users = mock(AppUserRepository.class);
        when(users.findById(dentistId)).thenReturn(java.util.Optional.of(dentist));
        ClinicRepository repo = mock(ClinicRepository.class);
        when(repo.findByClinicCode(anyString())).thenReturn(java.util.Optional.empty());
        when(repo.saveAndFlush(any())).thenAnswer(inv -> {
            Clinic saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(client.getClinics(anyString(), anyString())).thenReturn(List.of());
        ClinicConnectionService service = new ClinicConnectionService(repo, client, users, mock(ResourceMirrorService.class),
                mock(FullSyncService.class), mock(SyncControl.class), jdbc, mock(PlatformTransactionManager.class),
                "dev", "https://api.opendental.com/api/v1", "", 30);
        service.background = r -> { };
        UUID someoneElse = UUID.randomUUID();

        service.register("Downtown Smiles", "cust1234", "http://localhost:30223/api/v1", "", someoneElse);
        service.register("Uptown Smiles", "cust5678", "http://localhost:30223/api/v1", "");

        // Both are the dentist's own; a dentist naming another owner is ignored.
        verify(repo, times(2)).saveAndFlush(argThat(c -> dentistId.equals(c.getOwnerUserId())));
        verify(users, never()).findById(someoneElse);
    }

    @Test
    void twoConnectedClinicsNeverShareACustomerKey() {
        UUID sharma = UUID.randomUUID();
        UUID otherDentist = UUID.randomUUID();
        Clinic downtown = Clinic.builder().id(UUID.randomUUID()).clinicCode("SHARMA_DOWNTOWN").clinicName("Sharma Downtown")
                .apiKey("ODFHIR dev/KEY1111").ownerUserId(sharma).isActive(true).baseUrl("http://localhost:30223/api/v1").build();
        Clinic elsewhere = Clinic.builder().id(UUID.randomUUID()).clinicCode("ELSEWHERE").clinicName("Elsewhere Dental")
                .apiKey("ODFHIR dev/KEY9999").ownerUserId(otherDentist).isActive(true).build();
        Clinic oldOne = Clinic.builder().id(UUID.randomUUID()).clinicCode("OLD_ONE").clinicName("Old One")
                .apiKey("ODFHIR dev/KEY5555").ownerUserId(sharma).isActive(false).baseUrl("http://localhost:30223/api/v1").build();
        ClinicRepository repo = mock(ClinicRepository.class);
        when(repo.findAll()).thenReturn(List.of(downtown, elsewhere, oldOne));
        when(repo.findByClinicCode(anyString())).thenReturn(java.util.Optional.empty());
        when(repo.findByClinicCode("SHARMA_DOWNTOWN")).thenReturn(java.util.Optional.of(downtown));
        when(repo.findByClinicCode("OLD_ONE")).thenReturn(java.util.Optional.of(oldOne));
        when(repo.saveAndFlush(any())).thenAnswer(inv -> {
            Clinic saved = inv.getArgument(0);
            if (saved.getId() == null) saved.setId(UUID.randomUUID());
            return saved;
        });
        when(client.getClinics(anyString(), anyString())).thenReturn(List.of());
        AppUserRepository users = mock(AppUserRepository.class);
        when(users.findById(sharma)).thenReturn(java.util.Optional.of(
                AppUser.builder().id(sharma).email("dr.sharma@example.com").role(Role.DENTIST).active(true).build()));
        ClinicConnectionService service = new ClinicConnectionService(repo, client, users, mock(ResourceMirrorService.class),
                mock(FullSyncService.class), mock(SyncControl.class), jdbc, mock(PlatformTransactionManager.class),
                "dev", "https://api.opendental.com/api/v1", "", 30);
        service.background = r -> { };
        String url = "http://localhost:30223/api/v1";
        signInAsDentist(sharma);

        // Dr Sharma's second clinic with the first one's key: refused; with its own key: registered.
        assertThatThrownBy(() -> service.register("Sharma Uptown", "KEY1111", url, ""))
                .isInstanceOf(ApiException.class).hasMessageContaining("already used by connected clinic \"Sharma Downtown\"");
        // Another dentist's connected clinic's key: refused without saying whose clinic it is.
        assertThatThrownBy(() -> service.register("Sharma Uptown", "otherDev/KEY9999", url, ""))
                .hasMessageContaining("already used by another connected clinic").hasMessageNotContaining("Elsewhere");
        service.register("Sharma Uptown", "KEY2222", url, "");
        verify(repo).saveAndFlush(argThat(c -> "ODFHIR dev/KEY2222".equals(c.getApiKey()) && sharma.equals(c.getOwnerUserId())));
        // A disconnected clinic's key is free for a new clinic.
        service.register("Sharma New Branch", "KEY5555", url, "");
        verify(repo).saveAndFlush(argThat(c -> "ODFHIR dev/KEY5555".equals(c.getApiKey()) && "Sharma New Branch".equals(c.getClinicName())));
        TestSession.clear();

        // ...but the old clinic can't be connected again with it while the new one uses it.
        when(repo.findAll()).thenReturn(List.of(downtown, elsewhere, oldOne,
                Clinic.builder().id(UUID.randomUUID()).clinicCode("SHARMA_NEW_BRANCH").clinicName("Sharma New Branch")
                        .apiKey("ODFHIR dev/KEY5555").ownerUserId(sharma).isActive(true).build()));
        TestSession.signIn(Role.ADMIN, null, "OLD_ONE");
        assertThatThrownBy(() -> service.update("Old One", "KEY5555", url, ""))
                .hasMessageContaining("Sharma New Branch");
        TestSession.clear();

        // An Admin is told which connected clinic has the key.
        TestSession.signIn(Role.ADMIN, null, "SHARMA_DOWNTOWN");
        assertThatThrownBy(() -> service.update("Sharma Downtown", "KEY9999", url, ""))
                .hasMessageContaining("Elsewhere Dental");
        // Keeping its own key (or pasting it again) is fine.
        service.update("Sharma Downtown", "KEY1111", url, "");
        service.update("Sharma Downtown", "", url, "");
    }

    @Test
    void aClinicNamedByItsCodeIsEditedWithoutBeingChosenFirstButOnlyByWhoMayManageIt() {
        UUID sharma = UUID.randomUUID();
        Clinic disconnected = Clinic.builder().id(UUID.randomUUID()).clinicCode("DR_SHARMA").clinicName("Dr Sharma")
                .baseUrl("http://localhost:30223/api/v1").apiKey("ODFHIR dev/old").ownerUserId(sharma).isActive(false).build();
        ClinicRepository repo = mock(ClinicRepository.class);
        when(repo.findByClinicCode("DR_SHARMA")).thenReturn(java.util.Optional.of(disconnected));
        when(repo.findByIsActiveTrue()).thenReturn(List.of()); // nothing connected, nothing chosen
        when(repo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(client.getClinics(anyString(), anyString())).thenReturn(List.of());
        ClinicConnectionService service = new ClinicConnectionService(repo, client, mock(AppUserRepository.class),
                mock(ResourceMirrorService.class), mock(FullSyncService.class), mock(SyncControl.class), jdbc,
                mock(PlatformTransactionManager.class), "dev", "https://api.opendental.com/api/v1", "", 30);
        service.background = r -> { };
        String url = "http://localhost:30223/api/v1";

        // Another dentist can't touch it.
        signInAsDentist(UUID.randomUUID());
        assertThatThrownBy(() -> service.update("DR_SHARMA", "Dr Sharma", "cust7777", url, "", null))
                .hasMessageContaining("don't have access to clinic DR_SHARMA");
        TestSession.clear();

        // An Admin who chose nothing reconnects it by its code.
        TestSession.signIn(Role.ADMIN, null);
        service.update("DR_SHARMA", "Dr Sharma", "cust7777", url, "", null);
        assertThat(disconnected.getIsActive()).isTrue();
        assertThat(disconnected.getApiKey()).isEqualTo("ODFHIR dev/cust7777");
    }

    @Test
    void aClinicCantBeRemovedWhileItsDisconnectIsStillCleaningUpInOpenDental() {
        Clinic clinic = Clinic.builder().id(UUID.randomUUID()).clinicCode("DR_SHARMA").clinicName("Dr Sharma")
                .baseUrl("http://localhost:30223/api/v1").apiKey("ODFHIR dev/k").isActive(true).build();
        ClinicRepository repo = mock(ClinicRepository.class);
        when(repo.findByClinicCode("DR_SHARMA")).thenReturn(java.util.Optional.of(clinic));
        when(repo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        ClinicConnectionService service = new ClinicConnectionService(repo, client, mock(AppUserRepository.class),
                mock(ResourceMirrorService.class), mock(FullSyncService.class), mock(SyncControl.class), jdbc,
                mock(PlatformTransactionManager.class), "dev", "https://api.opendental.com/api/v1", "", 30);
        java.util.List<Runnable> later = new java.util.ArrayList<>();
        service.background = later::add;
        TestSession.signIn(Role.ADMIN, null);

        service.disconnect("DR_SHARMA"); // removing its webhooks in Open Dental is still pending
        assertThatThrownBy(() -> service.remove("DR_SHARMA", "Dr Sharma")).hasMessageContaining("still finishing the disconnect");
        verify(repo, never()).delete(any());

        when(client.getRaw(eq("/subscriptions"), any(), any(), any())).thenReturn(JSON.createArrayNode());
        later.forEach(Runnable::run); // the clean-up ends
        assertThat(service.remove("DR_SHARMA", "Dr Sharma")).isEqualTo("Dr Sharma (DR_SHARMA)");
    }

    @Test
    void onlyADisconnectedClinicIsRemovedAfterTypingItsNameByAnAdminOrItsDentist() {
        UUID sharma = UUID.randomUUID();
        Clinic connected = Clinic.builder().id(UUID.randomUUID()).clinicCode("LIVE").clinicName("Live Dental")
                .ownerUserId(sharma).isActive(true).build();
        Clinic old = Clinic.builder().id(UUID.randomUUID()).clinicCode("DR_SHARMA").clinicName("Dr Sharma")
                .ownerUserId(sharma).isActive(false).build();
        ClinicRepository repo = mock(ClinicRepository.class);
        when(repo.findByClinicCode("LIVE")).thenReturn(java.util.Optional.of(connected));
        when(repo.findByClinicCode("DR_SHARMA")).thenReturn(java.util.Optional.of(old));
        ClinicConnectionService service = new ClinicConnectionService(repo, client, mock(AppUserRepository.class),
                mock(ResourceMirrorService.class), mock(FullSyncService.class), mock(SyncControl.class), jdbc,
                mock(PlatformTransactionManager.class), "dev", "https://api.opendental.com/api/v1", "", 30);

        signInAsDentist(UUID.randomUUID());
        assertThatThrownBy(() -> service.remove("DR_SHARMA", "Dr Sharma")).hasMessageContaining("don't have access");
        TestSession.clear();

        signInAsDentist(sharma);
        assertThatThrownBy(() -> service.remove("LIVE", "Live Dental")).hasMessageContaining("Disconnect it first");
        assertThatThrownBy(() -> service.remove("DR_SHARMA", "Dr Sharm")).hasMessageContaining("Type the clinic's name");
        verify(repo, never()).delete(any());

        assertThat(service.remove("DR_SHARMA", " dr sharma ")).isEqualTo("Dr Sharma (DR_SHARMA)");
        verify(repo).delete(old);
        verify(repo, never()).delete(connected);
        // Removing deletes from SmileOS's database only: Open Dental is never called.
        verifyNoInteractions(client);
    }

    @Test
    void anAdminRegistersAClinicOnlyForAnExistingActiveDentist() {
        TestSession.signIn(Role.ADMIN, null);
        AppUserRepository users = mock(AppUserRepository.class);
        AppUser frontDesk = AppUser.builder().id(UUID.randomUUID()).email("desk@example.com").role(Role.FRONT_DESK).active(true).build();
        AppUser away = AppUser.builder().id(UUID.randomUUID()).email("away@example.com").role(Role.DENTIST).active(false).build();
        when(users.findById(frontDesk.getId())).thenReturn(java.util.Optional.of(frontDesk));
        when(users.findById(away.getId())).thenReturn(java.util.Optional.of(away));
        ClinicConnectionService service = new ClinicConnectionService(mock(ClinicRepository.class), client, users,
                mock(ResourceMirrorService.class), mock(FullSyncService.class), mock(SyncControl.class), jdbc,
                mock(PlatformTransactionManager.class), "dev", "https://api.opendental.com/api/v1", "", 30);
        String url = "http://localhost:30223/api/v1";

        assertThatThrownBy(() -> service.register("Smiles", "cust1", url, "", null)).hasMessageContaining("Choose the dentist");
        assertThatThrownBy(() -> service.register("Smiles", "cust1", url, "", UUID.randomUUID())).hasMessageContaining("doesn't exist");
        assertThatThrownBy(() -> service.register("Smiles", "cust1", url, "", frontDesk.getId())).hasMessageContaining("not a dentist");
        assertThatThrownBy(() -> service.register("Smiles", "cust1", url, "", away.getId())).hasMessageContaining("deactivated");
        verifyNoInteractions(client);
    }

    @Test
    void anAdminLooksUpTheDentistByEmailBeforeRegisteringTheirClinic() {
        AppUserRepository users = mock(AppUserRepository.class);
        AppUser dentist = AppUser.builder().id(UUID.randomUUID()).email("dr.mehta@yourclinic.com").fullName("Dr Mehta")
                .role(Role.DENTIST).active(true).build();
        when(users.findByEmail("dr.mehta@yourclinic.com")).thenReturn(java.util.Optional.of(dentist));
        when(users.findByEmail("new@yourclinic.com")).thenReturn(java.util.Optional.empty());
        ClinicConnectionService service = new ClinicConnectionService(mock(ClinicRepository.class), client, users,
                mock(ResourceMirrorService.class), mock(FullSyncService.class), mock(SyncControl.class), jdbc,
                mock(PlatformTransactionManager.class), "dev", "https://api.opendental.com/api/v1", "", 30);

        TestSession.signIn(Role.DENTIST, null);
        assertThatThrownBy(() -> service.findDentist("dr.mehta@yourclinic.com")).hasMessageContaining("Only a SmileOS admin");
        TestSession.clear();

        TestSession.signIn(Role.ADMIN, null);
        assertThat(service.findDentist("dr.mehta@yourclinic.com")).containsEntry("exists", true)
                .containsEntry("isDentist", true).containsEntry("id", dentist.getId());
        assertThat(service.findDentist("new@yourclinic.com")).containsEntry("exists", false);
        assertThatThrownBy(() -> service.findDentist("not-an-email")).hasMessageContaining("email");
    }

    @Test
    void onlyAnAdminChangesWhoOwnsAClinic() {
        UUID ownerId = UUID.randomUUID();
        AppUser newOwner = AppUser.builder().id(UUID.randomUUID()).email("new@example.com").role(Role.DENTIST).active(true).build();
        Clinic clinic = Clinic.builder().id(UUID.randomUUID()).clinicCode("SMILES").clinicName("Smiles").ownerUserId(ownerId)
                .baseUrl("http://localhost:30223/api/v1").apiKey("ODFHIR dev/k").isActive(true).build();
        ClinicRepository repo = mock(ClinicRepository.class);
        when(repo.findByClinicCode("SMILES")).thenReturn(java.util.Optional.of(clinic));
        when(repo.findByOwnerUserIdOrderByCreatedAtAsc(ownerId)).thenReturn(List.of(clinic));
        when(repo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        AppUserRepository users = mock(AppUserRepository.class);
        when(users.findById(newOwner.getId())).thenReturn(java.util.Optional.of(newOwner));
        ClinicConnectionService service = new ClinicConnectionService(repo, client, users, mock(ResourceMirrorService.class),
                mock(FullSyncService.class), mock(SyncControl.class), jdbc, mock(PlatformTransactionManager.class),
                "dev", "https://api.opendental.com/api/v1", "", 30);
        service.background = r -> { };

        // The owning dentist edits their clinic but can't hand it over.
        signInAsDentist(ownerId);
        assertThatThrownBy(() -> service.update("Smiles", "", "http://localhost:30223/api/v1", "", newOwner.getId()))
                .hasMessageContaining("Only a SmileOS admin");
        TestSession.clear();

        TestSession.signIn(Role.ADMIN, null, "SMILES");
        service.update("Smiles", "", "http://localhost:30223/api/v1", "", newOwner.getId());
        assertThat(clinic.getOwnerUserId()).isEqualTo(newOwner.getId());
    }

    /** Signs in a dentist with a given user id (the clinic owner). */
    private static UUID signInAsDentist(UUID userId) {
        org.springframework.security.oauth2.jwt.Jwt jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("t")
                .header("alg", "none").subject(userId.toString()).claim("role", "DENTIST").claim("clinic", "").build();
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt, List.of()));
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(new org.springframework.mock.web.MockHttpServletRequest()));
        return userId;
    }

    @Test
    void aClinicNameCanOnlyBeUsedOnceWhateverTheCase() {
        TestSession.signIn(Role.ADMIN, null);
        ClinicRepository repo = mock(ClinicRepository.class);
        when(repo.findByNameIgnoringCase("my test practice")).thenReturn(List.of(CLINIC));
        AppUserRepository users = mock(AppUserRepository.class);
        AppUser dentist = AppUser.builder().id(UUID.randomUUID()).email("d@example.com").role(Role.DENTIST).active(true).build();
        when(users.findById(dentist.getId())).thenReturn(java.util.Optional.of(dentist));
        ClinicConnectionService service = new ClinicConnectionService(repo, client, users,
                mock(ResourceMirrorService.class), mock(FullSyncService.class), mock(SyncControl.class), jdbc,
                mock(PlatformTransactionManager.class), "dev", "https://api.opendental.com/api/v1", "", 30);

        assertThatThrownBy(() -> service.register("my test practice", "cust1234", "http://localhost:30223/api/v1", "", dentist.getId()))
                .isInstanceOf(ApiException.class).hasMessageContaining("already exists").hasMessageContaining("Edit");
        verifyNoInteractions(client);
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void aClinicKeepsItsOwnNameEvenIfAnOlderClinicHasItTooButCantTakeAnotherOnesName() {
        Clinic first = Clinic.builder().id(UUID.randomUUID()).clinicCode("MY_TEST_PRACTICE").clinicName("MY_TEST_PRACTICE")
                .baseUrl("http://localhost:30223/api/v1").apiKey("ODFHIR dev/old").isActive(true).build();
        Clinic second = Clinic.builder().id(UUID.randomUUID()).clinicCode("MY_TEST_PRACTICE_2").clinicName("MY_TEST_PRACTICE")
                .baseUrl("http://localhost:30223/api/v1").apiKey("ODFHIR dev/old").isActive(true).build();
        ClinicRepository repo = mock(ClinicRepository.class);
        when(repo.findByClinicCode("MY_TEST_PRACTICE_2")).thenReturn(java.util.Optional.of(second));
        when(repo.findByNameIgnoringCase("MY_TEST_PRACTICE")).thenReturn(List.of(first, second));
        when(repo.findByNameIgnoringCase("clinic a - downtown")).thenReturn(List.of(CLINIC)); // the query ignores case
        when(repo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        ClinicConnectionService service = new ClinicConnectionService(repo, client, mock(AppUserRepository.class),
                mock(ResourceMirrorService.class), mock(FullSyncService.class), mock(SyncControl.class), jdbc,
                mock(PlatformTransactionManager.class), "dev", "https://api.opendental.com/api/v1", "", 30);
        service.background = r -> { };
        TestSession.signIn(Role.ADMIN, null, "MY_TEST_PRACTICE_2");

        // Unchanged name: saved, although the older clinic has the same one.
        service.update("MY_TEST_PRACTICE", "", "http://localhost:30223/api/v1", "");
        // Renamed to tell them apart: fine.
        service.update("My Test Practice (copy)", "", "http://localhost:30223/api/v1", "");
        assertThat(second.getClinicName()).isEqualTo("My Test Practice (copy)");
        // Renamed to another clinic's name: refused.
        assertThatThrownBy(() -> service.update("clinic a - downtown", "", "http://localhost:30223/api/v1", ""))
                .hasMessageContaining("already exists");
    }

    @Test
    void editingADisconnectedClinicNeedsItsKeyAndConnectsItAgain() {
        Clinic disconnected = Clinic.builder().id(UUID.randomUUID()).clinicCode("DR_MEHTA").clinicName("Dr Mehta")
                .baseUrl("http://localhost:30223/api/v1").apiKey("ODFHIR dev/old").isActive(false).build();
        ClinicRepository repo = mock(ClinicRepository.class);
        when(repo.findByClinicCode("DR_MEHTA")).thenReturn(java.util.Optional.of(disconnected));
        when(repo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(client.getClinics(anyString(), anyString())).thenReturn(List.of());
        FullSyncService fullSync = mock(FullSyncService.class);
        ClinicConnectionService service = new ClinicConnectionService(repo, client, mock(AppUserRepository.class),
                mock(ResourceMirrorService.class), fullSync, mock(SyncControl.class), jdbc,
                mock(PlatformTransactionManager.class), "dev", "https://api.opendental.com/api/v1", "", 30);
        java.util.List<Runnable> later = new java.util.ArrayList<>();
        service.background = later::add;
        TestSession.signIn(Role.ADMIN, null, "DR_MEHTA");

        assertThatThrownBy(() -> service.update("Dr Mehta Dental", "", "http://localhost:30223/api/v1", ""))
                .hasMessageContaining("paste its Customer Key");

        Map<String, Object> result = service.update("Dr Mehta Dental", "cust9999", "http://localhost:30223/api/v1", "");

        assertThat(disconnected.getIsActive()).isTrue();
        assertThat(disconnected.getClinicName()).isEqualTo("Dr Mehta Dental");
        assertThat(disconnected.getApiKey()).isEqualTo("ODFHIR dev/cust9999");
        assertThat(result.get("message").toString()).contains("connected again");
        later.forEach(Runnable::run);
        verify(fullSync).startFullSync("connect", disconnected.getId()); // catches up with Open Dental
    }

    @Test
    void anAdminRegistersAClinicForADentistWhoThenOwnsIt() {
        TestSession.signIn(Role.ADMIN, null);
        ClinicRepository repo = mock(ClinicRepository.class);
        // A clinic with the same name exists: the new one still gets a code of its own.
        when(repo.findByClinicCode("SMITH_DENTAL")).thenReturn(java.util.Optional.of(CLINIC));
        when(repo.findByClinicCode("SMITH_DENTAL_2")).thenReturn(java.util.Optional.empty());
        when(repo.saveAndFlush(any())).thenAnswer(inv -> {
            Clinic saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(client.getClinics(anyString(), anyString())).thenReturn(List.of());
        AppUserRepository users = mock(AppUserRepository.class);
        AppUser dentist = AppUser.builder().id(UUID.randomUUID()).email("smith@example.com").role(Role.DENTIST).active(true).build();
        when(users.findById(dentist.getId())).thenReturn(java.util.Optional.of(dentist));
        ClinicConnectionService service = new ClinicConnectionService(repo, client, users, mock(ResourceMirrorService.class),
                mock(FullSyncService.class), mock(SyncControl.class), jdbc, mock(PlatformTransactionManager.class),
                "dev", "https://api.opendental.com/api/v1", "", 30);
        service.background = r -> { };

        Map<String, Object> result = service.register("Smith Dental", "cust1234", "http://localhost:30223/api/v1", "", dentist.getId());

        assertThat(result.get("clinicCode")).isEqualTo("SMITH_DENTAL_2");
        assertThat(result.get("ownerEmail")).isEqualTo("smith@example.com");
        verify(repo).saveAndFlush(argThat(c -> dentist.getId().equals(c.getOwnerUserId())));
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
