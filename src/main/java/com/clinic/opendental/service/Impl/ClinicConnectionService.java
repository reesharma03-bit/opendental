package com.clinic.opendental.service.Impl;

import com.clinic.opendental.client.OpenDentalClient;
import com.clinic.opendental.config.OpenDentalConnection;
import com.clinic.opendental.dto.clinic.ClinicResponse;
import com.clinic.opendental.exception.ApiException;
import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.repository.ClinicRepository;
import com.clinic.opendental.service.WebhookClinics;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Connecting a practice's Open Dental to SmileOS from the dashboard (the "Open Dental" page),
 * and keeping its webhook subscriptions in place.
 *
 * <p>A practice only gives its <b>Customer Key</b> (Open Dental: Setup → Advanced Setup → API).
 * SmileOS adds its own Developer Key (OPENDENTAL_DEVELOPER_KEY) and Open Dental's Remote API
 * address, tests the key, and saves the clinics row.</p>
 *
 * <p>Webhooks: one subscription per watch table (and PatientSelected), each pointing at
 * {@code <PUBLIC_API_URL>/api/webhooks/opendental/<table>}. Setting them up is repeatable: the
 * clinic's subscriptions are listed first, ours (recognised by that path) are corrected and only
 * missing ones are added; other apps' subscriptions are left alone. Done when a practice
 * connects, shortly after the API starts, and nightly.</p>
 *
 * <p>Events are sent from the office computer the practice names (Open Dental's Workstation),
 * or from every computer until one is named. With several computers that can mean the same
 * event more than once, which is harmless: every event just saves the record again.</p>
 */
@Service
@Slf4j
public class ClinicConnectionService {

    static final String WEBHOOK_PATH = "/api/webhooks/opendental/";
    /**
     * "Every computer" in Open Dental: blank before version 26.2.1 (where "All Workstations" is
     * read as a computer name and never fires), "All Workstations" from 26.2.1 (blank refused).
     */
    static final String ALL_WORKSTATIONS = "All Workstations";

    /** What Open Dental said, in words a practice can act on. */
    public record Test(boolean ok, String message, int openDentalClinics) {
    }

    /** One wanted subscription and how it stands in Open Dental. */
    public record Webhook(String event, String state, String detail) {
    }

    private final ClinicRepository clinics;
    private final OpenDentalClient client;
    private final OpenDentalConnection connection;
    private final ResourceMirrorService mirror;
    private final FullSyncService fullSync;
    private final SyncControl syncControl;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final String developerKey;
    private final String remoteUrl;
    private final String publicUrl;
    private final int pollingSeconds;
    /** Removing webhooks after a disconnect: one Open Dental call a second, so not while the page waits. */
    Executor background = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "od-webhook-removal");
        thread.setDaemon(true);
        return thread;
    });

    public ClinicConnectionService(ClinicRepository clinics, OpenDentalClient client, OpenDentalConnection connection,
                                   ResourceMirrorService mirror, FullSyncService fullSync, SyncControl syncControl,
                                   JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                                   @Value("${opendental.developer-key:}") String developerKey,
                                   @Value("${opendental.remote-url:https://api.opendental.com/api/v1}") String remoteUrl,
                                   @Value("${public-api-url:}") String publicUrl,
                                   @Value("${opendental.webhook-polling-seconds:30}") int pollingSeconds) {
        this.clinics = clinics;
        this.client = client;
        this.connection = connection;
        this.mirror = mirror;
        this.fullSync = fullSync;
        this.syncControl = syncControl;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
        this.developerKey = developerKey == null ? "" : developerKey.trim();
        this.remoteUrl = trimUrl(remoteUrl);
        this.publicUrl = trimUrl(publicUrl);
        this.pollingSeconds = Math.max(1, pollingSeconds);
    }

    // ------------------------------------------------------------------ setup

    public boolean hasDeveloperKey() {
        return !developerKey.isEmpty();
    }

    public String remoteUrl() {
        return remoteUrl;
    }

    /** Where Open Dental sends webhooks; empty until PUBLIC_API_URL is set. */
    public String publicUrl() {
        return publicUrl;
    }

    /** The practice's connected clinic (the dashboard serves one practice), or null. */
    public Clinic current() {
        return clinics.findByIsActiveTrue().stream().findFirst().orElse(null);
    }

    /**
     * The full Authorization value from what the practice pasted: just the Customer Key
     * (the usual case), "DeveloperKey/CustomerKey" or "ODFHIR DeveloperKey/CustomerKey".
     */
    String composeKey(String input) {
        String k = input == null ? "" : input.trim();
        if (k.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Paste the Customer Key from Open Dental.");
        }
        if (k.regionMatches(true, 0, "ODFHIR ", 0, 7)) {
            k = k.substring(7).trim();
        }
        if (k.contains("/")) {
            return "ODFHIR " + k;
        }
        if (k.contains(" ")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A Customer Key has no spaces. Copy it again from Open Dental.");
        }
        if (developerKey.isEmpty()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "SmileOS's Open Dental developer key isn't set on the server (OPENDENTAL_DEVELOPER_KEY). Ask your SmileOS administrator.");
        }
        return "ODFHIR " + developerKey + "/" + k;
    }

    /** Shown instead of the key: its last four characters. */
    public static String maskedKey(String apiKey) {
        String customer = WebhookClinics.customerKey(apiKey);
        return customer.length() <= 4 ? "••••" : "••••" + customer.substring(customer.length() - 4);
    }

    /** Tries the key with one small read. */
    public Test test(String keyInput, String baseUrl) {
        return check(composeKey(keyInput), requireAddress(baseUrl));
    }

    private Test check(String apiKey, String baseUrl) {
        try {
            List<ClinicResponse> found = client.getClinics(baseUrl, apiKey);
            int count = found == null ? 0 : found.size();
            return new Test(true, count > 0
                    ? "Connected. Open Dental lists " + count + " clinic" + (count == 1 ? "" : "s") + "."
                    : "Connected to Open Dental.", count);
        } catch (HttpStatusCodeException e) {
            return new Test(false, explain(e), 0);
        } catch (ResourceAccessException e) {
            return new Test(false, "Couldn't reach " + baseUrl + ". Check the address and the internet connection.", 0);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            return new Test(false, "Open Dental couldn't be checked: " + e.getMessage(), 0);
        }
    }

    static String explain(HttpStatusCodeException e) {
        String body = e.getResponseBodyAsString();
        String said = body == null ? "" : body.replaceAll("\\s+", " ").trim();
        if (said.length() > 300) said = said.substring(0, 300) + "…";
        String lower = said.toLowerCase(Locale.ROOT);
        int status = e.getStatusCode().value();
        if (lower.contains("econnector") || status == 502 || status == 503 || status == 504) {
            return "Open Dental couldn't reach your office. Make sure the eConnector service is running on the office server, then try again."
                    + (said.isEmpty() ? "" : " (Open Dental said: " + said + ")");
        }
        if (status == 401 || status == 403 || lower.contains("malformed") || lower.contains("key")) {
            return "Open Dental didn't accept this key. Copy the whole Customer Key again, and check that SmileOS is enabled "
                    + "in Open Dental (Setup → Advanced Setup → API)." + (said.isEmpty() ? "" : " (Open Dental said: " + said + ")");
        }
        return "Open Dental answered " + status + (said.isEmpty() ? "." : ": " + said);
    }

    /**
     * Connects the practice: tests the key, saves the clinic, sets up webhooks (when a public
     * address is set) and starts the first full sync.
     */
    public Map<String, Object> connect(String name, String keyInput, String baseUrl, String workstation) {
        if (current() != null) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Open Dental is already connected. Change the key or disconnect it first.");
        }
        String clinicName = name == null ? "" : name.trim();
        if (clinicName.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Give the practice a name.");
        }
        String apiKey = composeKey(keyInput);
        String address = requireAddress(baseUrl);
        Test test = check(apiKey, address);
        if (!test.ok()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, test.message());
        }
        Clinic clinic = clinics.findByClinicCode(codeFor(clinicName)).filter(c -> !Boolean.TRUE.equals(c.getIsActive()))
                .orElseGet(Clinic::new);
        clinic.setClinicName(clinicName);
        if (clinic.getClinicCode() == null) clinic.setClinicCode(uniqueCode(clinicName));
        clinic.setBaseUrl(address);
        clinic.setApiKey(apiKey);
        clinic.setIsActive(true);
        clinic = clinics.saveAndFlush(clinic);
        connection.refresh();
        saveWorkstation(clinic, workstation);
        List<Webhook> webhooks = trySetUpWebhooks(clinic);
        startFirstSync();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", test.message());
        result.put("webhooks", webhooks);
        return result;
    }

    /** A new key (or address) for the connected practice; webhooks are set up again for it. */
    public Map<String, Object> changeKey(String keyInput, String baseUrl) {
        Clinic clinic = requireCurrent();
        String apiKey = composeKey(keyInput);
        String address = baseUrl == null || baseUrl.isBlank() ? clinic.getBaseUrl() : trimUrl(baseUrl);
        Test test = check(apiKey, address);
        if (!test.ok()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, test.message());
        }
        clinic.setApiKey(apiKey);
        clinic.setBaseUrl(address);
        clinics.saveAndFlush(clinic);
        connection.refresh();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", test.message());
        result.put("webhooks", trySetUpWebhooks(clinic));
        return result;
    }

    /**
     * Stops syncing the practice right away (a running full sync stops after the resource it is
     * on), then removes SmileOS's webhook subscriptions in Open Dental in the background.
     */
    public void disconnect() {
        Clinic clinic = requireCurrent();
        clinic.setIsActive(false);
        clinics.saveAndFlush(clinic);
        connection.refresh();
        fullSync.stop("Disconnected");
        background.execute(() -> removeWebhooks(clinic));
    }

    private void removeWebhooks(Clinic clinic) {
        List<JsonNode> existing;
        try {
            existing = subscriptions(clinic);
        } catch (Exception e) {
            log.warn("Could not list the webhook subscriptions of {} to remove them: {}", clinic.getClinicCode(), e.getMessage());
            existing = List.of();
        }
        int removed = 0;
        for (JsonNode sub : existing) {
            if (!isOurs(sub)) continue;
            long num = sub.path("SubscriptionNum").asLong();
            try {
                client.sendRaw(HttpMethod.DELETE, "/subscriptions/" + num, null, clinic.getBaseUrl(), clinic.getApiKey());
                removed++;
            } catch (HttpClientErrorException.NotFound e) {
                // Already gone.
            } catch (Exception e) {
                log.warn("Could not remove webhook subscription #{} of {}: {}", num, clinic.getClinicCode(), e.getMessage());
            }
        }
        refreshMirror(clinic);
        log.info("Disconnected {}: removed {} webhook subscription(s) in Open Dental", clinic.getClinicCode(), removed);
    }

    // --------------------------------------------------------------- webhooks

    /** The office computer the practice named for webhook events; empty when none. */
    public String workstation(Clinic clinic) {
        try {
            List<String> rows = jdbc.queryForList("SELECT workstation FROM clinic_webhooks WHERE clinic_id = ?",
                    String.class, clinic.getId());
            return rows.isEmpty() || rows.get(0) == null ? "" : rows.get(0);
        } catch (Exception e) {
            return "";
        }
    }

    public List<Webhook> saveWorkstationAndSetUp(String workstation) {
        Clinic clinic = requireCurrent();
        String name = workstation == null ? "" : workstation.trim();
        if (name.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter the name of the office computer that runs the Open Dental API Service.");
        }
        saveWorkstation(clinic, name);
        return setUpWebhooks(clinic);
    }

    private void saveWorkstation(Clinic clinic, String workstation) {
        if (workstation == null || workstation.isBlank()) return;
        try {
            tx.executeWithoutResult(s -> jdbc.update("""
                    INSERT INTO clinic_webhooks (clinic_id, workstation, updated_at) VALUES (?, ?, now())
                    ON CONFLICT (clinic_id) DO UPDATE SET workstation = EXCLUDED.workstation, updated_at = now()
                    """, clinic.getId(), workstation.trim()));
        } catch (Exception e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Webhook settings need their table: run supabase-clinic-connection.sql in Supabase first.");
        }
    }

    private List<Webhook> trySetUpWebhooks(Clinic clinic) {
        try {
            return setUpWebhooks(clinic);
        } catch (Exception e) {
            log.warn("Webhook setup for {} failed: {}", clinic.getClinicCode(), e.getMessage());
            return List.of(new Webhook("all", "failed", e.getMessage()));
        }
    }

    /**
     * Makes Open Dental's subscriptions match what SmileOS needs. Returns each wanted
     * subscription's state afterwards.
     */
    public List<Webhook> setUpWebhooks(Clinic clinic) {
        String problem = notReady(clinic);
        if (problem != null) {
            return wanted().stream().map(w -> new Webhook(w.event(), "off", problem)).toList();
        }
        String named = workstation(clinic);
        List<JsonNode> existing = subscriptions(clinic);
        List<Webhook> result = new ArrayList<>();
        for (Wanted want : wanted()) {
            String url = publicUrl + WEBHOOK_PATH + want.path();
            // UI events (PatientSelected) come from whichever computer a user works at.
            String station = want.uiEvent() || named.isEmpty() ? "" : named;
            JsonNode found = existing.stream().filter(s -> isOurs(s) && want.matches(s)).findFirst().orElse(null);
            try {
                if (found == null) {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("EndPointUrl", url);
                    body.put("Workstation", station);
                    if (want.uiEvent()) {
                        body.put("UiEventType", want.event());
                    } else {
                        body.put("WatchTable", want.event());
                        body.put("PollingSeconds", pollingSeconds);
                    }
                    body.put("Note", "SmileOS");
                    JsonNode created = createSubscription(clinic, body);
                    long num = created == null ? 0 : created.path("SubscriptionNum").asLong(0);
                    log.info("Added webhook subscription {} for {} ({})", num == 0 ? "?" : "#" + num, want.event(), clinic.getClinicCode());
                    result.add(new Webhook(want.event(), "ok", "Added"));
                } else {
                    Map<String, Object> changes = new LinkedHashMap<>();
                    if (!url.equals(found.path("EndPointUrl").asText())) changes.put("EndPointUrl", url);
                    String current = found.path("Workstation").asText("");
                    boolean everyComputer = current.isEmpty() || current.equalsIgnoreCase(ALL_WORKSTATIONS);
                    if (station.isEmpty() ? !everyComputer : !station.equalsIgnoreCase(current)) changes.put("Workstation", station);
                    if (!want.uiEvent() && found.path("PollingSeconds").asInt() != pollingSeconds) changes.put("PollingSeconds", pollingSeconds);
                    if (!changes.isEmpty()) {
                        client.sendRaw(HttpMethod.PUT, "/subscriptions/" + found.path("SubscriptionNum").asLong(), changes,
                                clinic.getBaseUrl(), clinic.getApiKey());
                    }
                    result.add(stateOf(want.event(), found, !changes.isEmpty()));
                }
            } catch (HttpStatusCodeException e) {
                result.add(new Webhook(want.event(), "failed", explain(e)));
            } catch (Exception e) {
                result.add(new Webhook(want.event(), "failed", e.getMessage()));
            }
        }
        recordCheck(clinic, result);
        refreshMirror(clinic);
        return result;
    }

    /** POST, saying "every computer" the way this Open Dental version understands it. */
    private JsonNode createSubscription(Clinic clinic, Map<String, Object> body) {
        try {
            return client.sendRaw(HttpMethod.POST, "/subscriptions", body, clinic.getBaseUrl(), clinic.getApiKey());
        } catch (HttpClientErrorException.BadRequest e) {
            if (!"".equals(body.get("Workstation"))) throw e;
            Map<String, Object> retry = new LinkedHashMap<>(body);
            retry.put("Workstation", ALL_WORKSTATIONS); // Open Dental 26.2.1 and later
            return client.sendRaw(HttpMethod.POST, "/subscriptions", retry, clinic.getBaseUrl(), clinic.getApiKey());
        }
    }

    /** How the wanted subscriptions stand in Open Dental now, without changing anything. */
    public List<Webhook> webhookStatus(Clinic clinic) {
        String problem = notReady(clinic);
        if (problem != null) {
            return wanted().stream().map(w -> new Webhook(w.event(), "off", problem)).toList();
        }
        List<JsonNode> existing;
        try {
            existing = subscriptions(clinic);
        } catch (HttpStatusCodeException e) {
            return List.of(new Webhook("all", "failed", explain(e)));
        } catch (Exception e) {
            return List.of(new Webhook("all", "failed", e.getMessage()));
        }
        List<Webhook> result = new ArrayList<>();
        for (Wanted want : wanted()) {
            JsonNode found = existing.stream().filter(s -> isOurs(s) && want.matches(s)).findFirst().orElse(null);
            String url = publicUrl + WEBHOOK_PATH + want.path();
            if (found == null) {
                result.add(new Webhook(want.event(), "missing", "Not set up in Open Dental"));
            } else if (!url.equals(found.path("EndPointUrl").asText())) {
                result.add(new Webhook(want.event(), "missing", "Points at an old address: " + found.path("EndPointUrl").asText()));
            } else {
                result.add(stateOf(want.event(), found, false));
            }
        }
        return result;
    }

    /** Shortly after the API starts (once), so a connected practice gets its webhooks without a click. */
    @Scheduled(initialDelayString = "${opendental.webhook-startup-delay-ms:20000}", fixedDelay = Long.MAX_VALUE)
    public void checkAfterStart() {
        checkAll();
    }

    /** Nightly: put back anything removed or changed in Open Dental. */
    @Scheduled(cron = "${opendental.webhook-check-cron:0 30 2 * * *}")
    public void nightlyCheck() {
        checkAll();
    }

    private void checkAll() {
        if (publicUrl.isEmpty() || syncControl.paused()) return;
        for (Clinic clinic : clinics.findByIsActiveTrue()) {
            try {
                List<Webhook> result = setUpWebhooks(clinic);
                long bad = result.stream().filter(w -> !"ok".equals(w.state())).count();
                if (bad > 0) log.warn("{} webhook subscription(s) of {} need attention", bad, clinic.getClinicCode());
                else log.info("Webhook subscriptions of {} are in place ({})", clinic.getClinicCode(), result.size());
            } catch (Exception e) {
                log.warn("Nightly webhook check of {} failed: {}", clinic.getClinicCode(), e.getMessage());
            }
        }
    }

    /** Why webhooks can't be set up yet, or null. */
    String notReady(Clinic clinic) {
        if (publicUrl.isEmpty()) {
            return "Waiting for SmileOS's public address (PUBLIC_API_URL): Open Dental must be able to reach it over the internet.";
        }
        return null;
    }

    private Webhook stateOf(String event, JsonNode sub, boolean changed) {
        int failures = sub.path("SubsequentFailures").asInt(0);
        String reason = sub.path("FailureReason").asText("");
        if (failures > 0 && !reason.isBlank()) {
            return new Webhook(event, "failing", "Open Dental can't deliver: " + reason + " (" + failures + " failures in a row)");
        }
        return new Webhook(event, "ok", changed ? "Corrected" : "Active");
    }

    private List<JsonNode> subscriptions(Clinic clinic) {
        JsonNode list = client.getRaw("/subscriptions", Map.of(), clinic.getBaseUrl(), clinic.getApiKey());
        List<JsonNode> out = new ArrayList<>();
        if (list != null && list.isArray()) list.forEach(out::add);
        return out;
    }

    /** Ours: sent to SmileOS's webhook path (on any address, so an old one gets corrected). */
    static boolean isOurs(JsonNode sub) {
        return sub.path("EndPointUrl").asText("").contains(WEBHOOK_PATH);
    }

    record Wanted(String event, boolean uiEvent) {
        String path() {
            return event.toLowerCase(Locale.ROOT);
        }

        boolean matches(JsonNode sub) {
            return uiEvent ? event.equalsIgnoreCase(sub.path("UiEventType").asText(""))
                    : event.equalsIgnoreCase(sub.path("WatchTable").asText(""));
        }
    }

    static List<Wanted> wanted() {
        List<Wanted> all = new ArrayList<>();
        SubscriptionRules.WATCH_TABLES.forEach(t -> all.add(new Wanted(t, false)));
        SubscriptionRules.UI_EVENT_TYPES.forEach(t -> all.add(new Wanted(t, true)));
        return all;
    }

    private void recordCheck(Clinic clinic, List<Webhook> result) {
        long ok = result.stream().filter(w -> "ok".equals(w.state())).count();
        try {
            tx.executeWithoutResult(s -> jdbc.update("""
                    UPDATE clinic_webhooks SET checked_at = now(), check_result = ?, updated_at = now() WHERE clinic_id = ?
                    """, ok + " of " + result.size() + " active", clinic.getId()));
        } catch (Exception e) {
            log.debug("Could not record the webhook check: {}", e.getMessage());
        }
    }

    /** So the Subscription page (it reads SmileOS's copy) shows the change right away. */
    private void refreshMirror(Clinic clinic) {
        try {
            OdResourceCatalog.Resource subs = OdResourceCatalog.find("subscriptions");
            if (subs != null) mirror.sync(clinic, subs, Timestamp.from(Instant.now()));
        } catch (Exception e) {
            log.debug("Could not refresh the copy of subscriptions: {}", e.getMessage());
        }
    }

    private void startFirstSync() {
        try {
            if (!syncControl.paused()) fullSync.startFullSync("connect");
        } catch (Exception e) {
            log.warn("Could not start the first sync: {}", e.getMessage());
        }
    }

    private Clinic requireCurrent() {
        Clinic clinic = current();
        if (clinic == null) throw new ApiException(HttpStatus.NOT_FOUND, "Open Dental isn't connected yet.");
        return clinic;
    }

    private static String requireAddress(String baseUrl) {
        String address = trimUrl(baseUrl);
        if (address.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose where SmileOS reaches your Open Dental (its API address).");
        }
        if (!address.matches("(?i)^https?://\\S+$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The Open Dental address must start with http:// or https://");
        }
        return address;
    }

    static String codeFor(String name) {
        String code = name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (code.isEmpty()) code = "CLINIC";
        return code.length() > 24 ? code.substring(0, 24) : code;
    }

    private String uniqueCode(String name) {
        String base = codeFor(name);
        String code = base;
        for (int i = 2; clinics.findByClinicCode(code).isPresent(); i++) {
            code = base + "_" + i;
        }
        return code;
    }

    private static String trimUrl(String url) {
        return url == null ? "" : url.trim().replaceAll("/+$", "");
    }

}
