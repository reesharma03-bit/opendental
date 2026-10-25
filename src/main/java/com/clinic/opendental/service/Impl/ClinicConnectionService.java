package com.clinic.opendental.service.Impl;

import com.clinic.opendental.client.OpenDentalClient;
import com.clinic.opendental.dto.clinic.ClinicResponse;
import com.clinic.opendental.exception.ApiException;
import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.repository.ClinicRepository;
import com.clinic.opendental.security.AppUser;
import com.clinic.opendental.security.AppUserRepository;
import com.clinic.opendental.security.CurrentClinic;
import com.clinic.opendental.security.CurrentUser;
import com.clinic.opendental.security.Role;
import com.clinic.opendental.service.WebhookClinics;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
 * <p>Setting them up takes one Open Dental call per subscription (about a second each), so it
 * runs in the background; the page shows the result of the last check, saved in
 * clinic_webhooks, instead of asking Open Dental every time it opens.</p>
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
    private final AppUserRepository users;
    private final ResourceMirrorService mirror;
    private final FullSyncService fullSync;
    private final SyncControl syncControl;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final String developerKey;
    private final String remoteUrl;
    private final String publicUrl;
    private final int pollingSeconds;
    private static final ObjectMapper JSON = new ObjectMapper();
    /** Clinics whose webhooks are being set up right now (shown on the page). */
    private final Set<UUID> settingUp = ConcurrentHashMap.newKeySet();
    /** Clinics whose webhooks are being removed from Open Dental after a disconnect. */
    private final Set<UUID> removingWebhooks = ConcurrentHashMap.newKeySet();
    /** Webhook setup and removal: one Open Dental call a second, so not while the page waits. */
    Executor background = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "od-webhook-removal");
        thread.setDaemon(true);
        return thread;
    });

    public ClinicConnectionService(ClinicRepository clinics, OpenDentalClient client, AppUserRepository users,
                                   ResourceMirrorService mirror, FullSyncService fullSync, SyncControl syncControl,
                                   JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                                   @Value("${opendental.developer-key:}") String developerKey,
                                   @Value("${opendental.remote-url:https://api.opendental.com/api/v1}") String remoteUrl,
                                   @Value("${public-api-url:}") String publicUrl,
                                   @Value("${opendental.webhook-polling-seconds:30}") int pollingSeconds) {
        this.clinics = clinics;
        this.client = client;
        this.users = users;
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

    /**
     * The clinic this request works on (connected or not): an Admin's chosen clinic, or the
     * signed-in user's own. Null when there is none yet (e.g. a Dentist who hasn't registered).
     */
    public Clinic current() {
        return CurrentClinic.find(clinics).orElse(null);
    }

    /** One row per clinic the user may see: every clinic for an Admin, their own for anyone else. */
    public List<Map<String, Object>> visibleClinics() {
        List<Clinic> visible;
        Role role = CurrentUser.role();
        if (role == Role.ADMIN) {
            visible = clinics.findAll();
        } else if (role == Role.DENTIST) {
            visible = CurrentUser.id() == null ? List.of() : clinics.findByOwnerUserIdOrderByCreatedAtAsc(CurrentUser.id());
        } else {
            Clinic own = current();
            visible = own == null ? List.of() : List.of(own);
        }
        visible = visible.stream()
                .sorted(java.util.Comparator.comparing(c -> c.getClinicName() == null ? "" : c.getClinicName().toLowerCase(Locale.ROOT)))
                .toList();
        Map<UUID, Map<String, Object>> stats = clinicStats();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Clinic c : visible) {
            Map<String, Object> row = describe(c);
            row.putAll(stats.getOrDefault(c.getId(), Map.of()));
            out.add(row);
        }
        return out;
    }

    /** A clinic as the Open Dental page shows it; the key only by its last four characters. */
    public Map<String, Object> describe(Clinic clinic) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("clinicCode", clinic.getClinicCode());
        c.put("clinicName", clinic.getClinicName());
        c.put("connected", Boolean.TRUE.equals(clinic.getIsActive()));
        c.put("baseUrl", clinic.getBaseUrl());
        c.put("key", clinic.getApiKey() == null || clinic.getApiKey().isBlank() ? "" : maskedKey(clinic.getApiKey()));
        c.put("workstation", workstation(clinic));
        c.put("createdAt", clinic.getCreatedAt() == null ? null : clinic.getCreatedAt().toString());
        AppUser owner = clinic.getOwnerUserId() == null ? null : users.findById(clinic.getOwnerUserId()).orElse(null);
        c.put("ownerUserId", owner == null ? null : owner.getId());
        c.put("ownerName", owner == null ? null : owner.getFullName());
        c.put("ownerEmail", owner == null ? null : owner.getEmail());
        return c;
    }

    /**
     * For an Admin registering a clinic: whether a user with this email exists and is a dentist.
     * {"exists": false} when nobody has it (the Admin can then create the dentist account).
     */
    public Map<String, Object> findDentist(String email) {
        requireAdmin();
        String clean = email == null ? "" : email.trim();
        if (!clean.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter the dentist's email address.");
        }
        AppUser user = users.findByEmail(clean).orElse(null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exists", user != null);
        if (user != null) {
            out.put("id", user.getId());
            out.put("fullName", user.getFullName());
            out.put("email", user.getEmail());
            out.put("role", user.getRole().name());
            out.put("roleLabel", user.getRole().label());
            out.put("active", user.isActive());
            out.put("isDentist", user.getRole() == Role.DENTIST);
        }
        return out;
    }

    /** The dentist a clinic is registered for: must exist, be active and have the DENTIST role. */
    private AppUser requireDentist(UUID userId) {
        if (userId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose the dentist this clinic belongs to (look them up by email, or create their account).");
        }
        AppUser dentist = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "That dentist account doesn't exist."));
        if (dentist.getRole() != Role.DENTIST) {
            throw new ApiException(HttpStatus.BAD_REQUEST, dentist.getEmail() + " is a " + dentist.getRole().label()
                    + " user, not a dentist. A clinic belongs to a dentist.");
        }
        if (!dentist.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, dentist.getEmail() + " is deactivated. Reactivate it on the Users page first.");
        }
        return dentist;
    }

    private static void requireAdmin() {
        if (CurrentUser.role() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only a SmileOS admin can do this.");
        }
    }

    /** Patients, users and the last sync per clinic, in three queries (missing tables are skipped). */
    private Map<UUID, Map<String, Object>> clinicStats() {
        Map<UUID, Map<String, Object>> stats = new java.util.HashMap<>();
        java.util.function.BiConsumer<String, String> add = (sql, key) -> {
            try {
                jdbc.query(sql, rs -> {
                    UUID id = rs.getObject(1, UUID.class);
                    if (id != null) stats.computeIfAbsent(id, k -> new LinkedHashMap<>()).put(key, rs.getObject(2));
                });
            } catch (Exception e) {
                log.debug("Clinic statistic {} unavailable: {}", key, e.getMessage());
            }
        };
        add.accept("SELECT clinic_id, count(*) FROM patients GROUP BY clinic_id", "patients");
        add.accept("SELECT clinic_id, count(*) FROM app_users WHERE active GROUP BY clinic_id", "users");
        add.accept("SELECT clinic_id, max(started_at)::text FROM sync_runs GROUP BY clinic_id", "lastSyncAt");
        return stats;
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
     * Registers a new clinic: tests the key, saves the clinic, sets up webhooks (when a public
     * address is set) and starts its first full sync. An Admin may register any number of
     * clinics; a Dentist registers one, and their account is then tied to it. A new clinic always
     * gets its own code, so nobody can attach themselves to an existing clinic by its name.
     */
    public Map<String, Object> register(String name, String keyInput, String baseUrl, String workstation) {
        return register(name, keyInput, baseUrl, workstation, null);
    }

    /**
     * @param dentistUserId the dentist the clinic belongs to: chosen by an Admin (required);
     *                      ignored for a Dentist, who always registers their own clinic
     */
    public Map<String, Object> register(String name, String keyInput, String baseUrl, String workstation, UUID dentistUserId) {
        Role role = CurrentUser.role();
        AppUser owner;
        if (role == Role.ADMIN) {
            owner = requireDentist(dentistUserId);
        } else if (role == Role.DENTIST) {
            owner = users.findById(CurrentUser.id())
                    .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Please sign in again."));
        } else {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only a dentist or a SmileOS admin can register a clinic.");
        }
        String clinicName = requireUniqueName(name, null);
        String apiKey = composeKey(keyInput);
        requireUnusedKey(apiKey, null);
        String address = requireAddress(baseUrl);
        Test test = check(apiKey, address);
        if (!test.ok()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, test.message());
        }
        Clinic clinic = new Clinic();
        clinic.setClinicName(clinicName);
        clinic.setClinicCode(uniqueCode(clinicName));
        clinic.setBaseUrl(address);
        clinic.setApiKey(apiKey);
        clinic.setIsActive(true);
        clinic.setOwnerUserId(owner.getId());
        clinic = saveClinic(clinic);
        saveWorkstation(clinic, workstation);
        // Webhooks first, then the first full sync, so the setup isn't queued behind the sync's calls.
        Clinic saved = clinic;
        setUpInBackground(clinic, () -> startFirstSync(saved));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", test.message());
        result.put("clinicCode", clinic.getClinicCode());
        result.put("clinicName", clinic.getClinicName());
        result.put("ownerEmail", owner.getEmail());
        return result;
    }

    /**
     * Edits the current clinic: its name, Open Dental address, office computer and (optionally) its
     * Customer Key. A disconnected clinic needs a key and is connected again. A new key or address
     * is tested before anything is saved; webhooks are set up again when the connection changed.
     */
    public Map<String, Object> update(String name, String keyInput, String baseUrl, String workstation) {
        return update(name, keyInput, baseUrl, workstation, null);
    }

    /** @param dentistUserId a new owner (Admins only; null keeps the current one) */
    public Map<String, Object> update(String name, String keyInput, String baseUrl, String workstation, UUID dentistUserId) {
        return update(null, name, keyInput, baseUrl, workstation, dentistUserId);
    }

    /** @param clinicCode the clinic to edit (checked: an Admin any, a Dentist their own); null = the current clinic */
    public Map<String, Object> update(String clinicCode, String name, String keyInput, String baseUrl, String workstation,
                                      UUID dentistUserId) {
        Clinic clinic = manageable(clinicCode);
        if (dentistUserId != null && !dentistUserId.equals(clinic.getOwnerUserId())) {
            requireAdmin();
            clinic.setOwnerUserId(requireDentist(dentistUserId).getId());
        }
        boolean connected = Boolean.TRUE.equals(clinic.getIsActive());
        String clinicName = requireUniqueName(name, clinic);
        String address = requireAddress(baseUrl);
        boolean newKey = keyInput != null && !keyInput.isBlank();
        if (!connected && !newKey) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    clinic.getClinicName() + " isn't connected: paste its Customer Key to connect it again.");
        }
        String apiKey = newKey ? composeKey(keyInput) : clinic.getApiKey();
        if (newKey || !connected) requireUnusedKey(apiKey, clinic);
        boolean connectionChanged = !connected || newKey || !address.equals(trimUrl(clinic.getBaseUrl()));
        String message = "Saved.";
        if (connectionChanged) {
            Test test = check(apiKey, address);
            if (!test.ok()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, test.message());
            }
            message = test.message();
        }
        String station = workstation == null ? "" : workstation.trim();
        boolean stationChanged = !station.isEmpty() && !station.equalsIgnoreCase(workstation(clinic));
        clinic.setClinicName(clinicName);
        clinic.setBaseUrl(address);
        clinic.setApiKey(apiKey);
        clinic.setIsActive(true);
        clinic = saveClinic(clinic);
        if (stationChanged) saveWorkstation(clinic, station);
        if (!connected) {
            Clinic saved = clinic;
            setUpInBackground(clinic, () -> startFirstSync(saved));
        } else if (connectionChanged || stationChanged) {
            setUpInBackground(clinic, null);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", connected ? message : message + " " + clinicName + " is connected again.");
        result.put("clinicCode", clinic.getClinicCode());
        result.put("clinicName", clinic.getClinicName());
        return result;
    }

    /**
     * Two connected clinics never share a Customer Key: they would be the same Open Dental
     * database, and its webhooks couldn't tell them apart. A disconnected clinic doesn't hold on
     * to its key (a new clinic may use it), but it can't be connected again while another
     * connected clinic uses it. A dentist with several clinics connects each with its own key.
     */
    private void requireUnusedKey(String apiKey, Clinic self) {
        String customerKey = WebhookClinics.customerKey(apiKey);
        if (customerKey.isEmpty()) return;
        Clinic other = clinics.findAll().stream()
                .filter(c -> self == null || !c.getId().equals(self.getId()))
                .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                .filter(c -> customerKey.equals(WebhookClinics.customerKey(c.getApiKey())))
                .findFirst().orElse(null);
        if (other == null) return;
        boolean admin = CurrentUser.role() == Role.ADMIN;
        boolean ownClinic = CurrentUser.id() != null && CurrentUser.id().equals(other.getOwnerUserId());
        throw keyInUse(admin || ownClinic ? "connected clinic \"" + other.getClinicName() + "\" (" + other.getClinicCode() + ")"
                : "another connected clinic");
    }

    private static ApiException keyInUse(String byWhom) {
        return new ApiException(HttpStatus.CONFLICT, "This Customer Key is already used by " + byWhom
                + ". Two connected clinics can't share a key: each connects with its own Customer Key from its own Open Dental"
                + " (or disconnect the other clinic first).");
    }

    /** Saves a clinic; the database's own rule (one Customer Key per connected clinic) answers in plain words too. */
    private Clinic saveClinic(Clinic clinic) {
        try {
            return clinics.saveAndFlush(clinic);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            String detail = String.valueOf(e.getMostSpecificCause().getMessage());
            if (detail.contains("customer_key_idx")) throw keyInUse("another connected clinic");
            throw e;
        }
    }

    /**
     * A clinic name nobody else uses (ignoring case). {@code self} may always keep its own name,
     * even if another clinic has it too (two clinics registered before names had to differ).
     */
    private String requireUniqueName(String name, Clinic self) {
        String clinicName = name == null ? "" : name.trim();
        if (clinicName.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Give the practice a name.");
        }
        if (self != null && self.getClinicName() != null && clinicName.equalsIgnoreCase(self.getClinicName().trim())) {
            return clinicName;
        }
        boolean taken = clinics.findByNameIgnoringCase(clinicName).stream()
                .anyMatch(other -> self == null || !other.getId().equals(self.getId()));
        if (taken) {
            throw new ApiException(HttpStatus.CONFLICT, "A clinic named \"" + clinicName + "\" already exists. "
                    + (CurrentUser.role() == Role.ADMIN
                        ? "Use Edit on that clinic to change or reconnect it, or choose another name."
                        : "Choose another name, or ask your SmileOS admin."));
        }
        return clinicName;
    }

    /**
     * Stops syncing the practice right away (a running full sync stops after the resource it is
     * on), then removes SmileOS's webhook subscriptions in Open Dental in the background.
     */
    public void disconnect() {
        disconnect(null);
    }

    /** @return the clinic that was disconnected */
    public Clinic disconnect(String clinicCode) {
        Clinic clinic = manageable(clinicCode);
        if (!Boolean.TRUE.equals(clinic.getIsActive())) {
            throw new ApiException(HttpStatus.CONFLICT, clinic.getClinicName() + " isn't connected.");
        }
        clinic.setIsActive(false);
        clinics.saveAndFlush(clinic);
        fullSync.stop(clinic.getId(), "Disconnected");
        removingWebhooks.add(clinic.getId());
        background.execute(() -> {
            try {
                removeWebhooks(clinic);
            } finally {
                removingWebhooks.remove(clinic.getId());
            }
        });
        return clinic;
    }

    /**
     * Removes a disconnected clinic for good from SmileOS's own database (Supabase): the clinic and
     * everything copied for it (patients, appointments, records, sync history; the database deletes
     * them with the clinic). Staff linked to it are unlinked, not deleted; the audit log keeps its
     * entries. Only a disconnected clinic can be removed, and the user confirms by typing its name.
     *
     * <p>Nothing is sent to Open Dental: its database is never changed by a removal. Changes still
     * queued for the clinic are dropped with it, so they never reach Open Dental either.</p>
     *
     * @return "Name (CODE)" of the removed clinic
     */
    public String remove(String clinicCode, String confirmName) {
        if (clinicCode == null || clinicCode.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose the clinic to remove.");
        }
        Clinic clinic = manageable(clinicCode);
        if (Boolean.TRUE.equals(clinic.getIsActive())) {
            throw new ApiException(HttpStatus.CONFLICT, clinic.getClinicName() + " is connected. Disconnect it first; only a disconnected clinic can be removed.");
        }
        if (removingWebhooks.contains(clinic.getId()) || settingUp.contains(clinic.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "SmileOS is still finishing the disconnect of " + clinic.getClinicName()
                    + " in Open Dental. Try again in a minute.");
        }
        String typed = confirmName == null ? "" : confirmName.trim();
        if (clinic.getClinicName() == null || !typed.equalsIgnoreCase(clinic.getClinicName().trim())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Type the clinic's name (" + clinic.getClinicName() + ") to confirm removing it.");
        }
        String label = clinic.getClinicName() + " (" + clinic.getClinicCode() + ")";
        fullSync.stop(clinic.getId(), "Removed");
        try {
            // The staging table has no link to clinics, so its rows go explicitly.
            tx.executeWithoutResult(s -> jdbc.update("DELETE FROM od_sync_staging WHERE clinic_id = ?", clinic.getId()));
        } catch (Exception e) {
            log.debug("Nothing to clear in od_sync_staging for {}: {}", label, e.getMessage());
        }
        clinics.delete(clinic);
        clinics.flush();
        settingUp.remove(clinic.getId());
        log.info("Removed clinic {} and its data", label);
        return label;
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

    public void saveWorkstationAndSetUp(String workstation) {
        saveWorkstationAndSetUp(null, workstation);
    }

    public void saveWorkstationAndSetUp(String clinicCode, String workstation) {
        Clinic clinic = manageable(clinicCode);
        String name = workstation == null ? "" : workstation.trim();
        if (name.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter the name of the office computer that runs the Open Dental API Service.");
        }
        saveWorkstation(clinic, name);
        setUpInBackground(clinic, null);
    }

    /** "Check and repair": runs in the background; the page shows progress. */
    public void repairInBackground() {
        repairInBackground(null);
    }

    public void repairInBackground(String clinicCode) {
        setUpInBackground(manageable(clinicCode), null);
    }

    public boolean isSettingUp(Clinic clinic) {
        return settingUp.contains(clinic.getId());
    }

    private void setUpInBackground(Clinic clinic, Runnable then) {
        if (!settingUp.add(clinic.getId())) return; // already running
        background.execute(() -> {
            try {
                trySetUpWebhooks(clinic);
            } finally {
                settingUp.remove(clinic.getId());
                if (then != null) then.run();
            }
        });
    }

    /** The result of the last check (saved), or why there is none; no call to Open Dental. */
    public List<Webhook> lastStatus(Clinic clinic) {
        String problem = notReady(clinic);
        if (problem != null) {
            return wanted().stream().map(w -> new Webhook(w.event(), "off", problem)).toList();
        }
        try {
            List<String> rows = jdbc.queryForList("SELECT check_result FROM clinic_webhooks WHERE clinic_id = ?",
                    String.class, clinic.getId());
            String saved = rows.isEmpty() ? null : rows.get(0);
            if (saved != null && saved.startsWith("[")) {
                return JSON.readValue(saved, new TypeReference<List<Webhook>>() { });
            }
        } catch (Exception e) {
            log.debug("No saved webhook check for {}: {}", clinic.getClinicCode(), e.getMessage());
        }
        return List.of();
    }

    /** When the webhooks were last checked, or null. */
    public java.sql.Timestamp lastChecked(Clinic clinic) {
        try {
            List<java.sql.Timestamp> rows = jdbc.queryForList("SELECT checked_at FROM clinic_webhooks WHERE clinic_id = ?",
                    java.sql.Timestamp.class, clinic.getId());
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            return null;
        }
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
        } catch (HttpStatusCodeException e) {
            return recordFailure(clinic, explain(e));
        } catch (Exception e) {
            return recordFailure(clinic, e.getMessage());
        }
    }

    private List<Webhook> recordFailure(Clinic clinic, String message) {
        log.warn("Webhook setup for {} failed: {}", clinic.getClinicCode(), message);
        List<Webhook> result = List.of(new Webhook("all", "failed", message));
        recordCheck(clinic, result);
        return result;
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
                result.add(unsupported(e)
                        ? new Webhook(want.event(), "unsupported", UNSUPPORTED)
                        : new Webhook(want.event(), "failed", explain(e)));
            } catch (Exception e) {
                result.add(new Webhook(want.event(), "failed", e.getMessage()));
            }
        }
        recordCheck(clinic, result);
        refreshMirror(clinic);
        return result;
    }

    static final String UNSUPPORTED = "This practice's Open Dental version doesn't offer this event yet. "
            + "SmileOS sets it up by itself after the practice updates Open Dental; until then these changes arrive through the regular sync.";

    /** Open Dental refuses a watch table or UI event its version doesn't have: 400 "WatchTable is invalid." */
    static boolean unsupported(HttpStatusCodeException e) {
        if (e.getStatusCode().value() != 400) return false;
        String body = e.getResponseBodyAsString();
        String lower = body == null ? "" : body.toLowerCase(Locale.ROOT);
        return lower.contains("watchtable is invalid") || lower.contains("uieventtype is invalid");
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
                long bad = result.stream().filter(w -> !"ok".equals(w.state()) && !"unsupported".equals(w.state())).count();
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
        try {
            String json = JSON.writeValueAsString(result);
            tx.executeWithoutResult(s -> jdbc.update("""
                    INSERT INTO clinic_webhooks (clinic_id, checked_at, check_result, updated_at) VALUES (?, now(), ?, now())
                    ON CONFLICT (clinic_id) DO UPDATE SET checked_at = now(), check_result = EXCLUDED.check_result, updated_at = now()
                    """, clinic.getId(), json));
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

    private void startFirstSync(Clinic clinic) {
        try {
            if (!syncControl.paused()) fullSync.startFullSync("connect", clinic.getId());
        } catch (Exception e) {
            log.warn("Could not start the first sync: {}", e.getMessage());
        }
    }

    /**
     * The clinic an action names by its code, after checking the user may manage it: an Admin any
     * clinic, a Dentist only one they own. No code: the current clinic (the switcher's choice).
     */
    public Clinic manageable(String clinicCode) {
        if (clinicCode == null || clinicCode.isBlank()) return requireCurrent();
        String code = clinicCode.trim();
        Clinic clinic = clinics.findByClinicCode(code)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "There is no clinic " + code + "."));
        Role role = CurrentUser.role();
        boolean allowed = role == Role.ADMIN
                || (role == Role.DENTIST && CurrentUser.id() != null && CurrentUser.id().equals(clinic.getOwnerUserId()));
        if (!allowed) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You don't have access to clinic " + code + ".");
        }
        return clinic;
    }

    private Clinic requireCurrent() {
        Clinic clinic = current();
        if (clinic == null) throw new ApiException(HttpStatus.NOT_FOUND, "There is no clinic yet. Register one first.");
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
