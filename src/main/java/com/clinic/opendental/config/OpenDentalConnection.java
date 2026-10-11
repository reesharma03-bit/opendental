package com.clinic.opendental.config;

import com.clinic.opendental.exception.ApiException;
import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.repository.ClinicRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Where Open Dental calls go when the caller doesn't name a clinic: the active clinic's
 * base_url and api_key from the clinics table. Each practice has its own Open Dental
 * database, so there is no global address or key in application.yaml. The clinic is
 * looked up again after a short while so edits to the clinics row are picked up.
 */
@Component
public class OpenDentalConnection {

    private static final long CACHE_MS = 30_000;

    private final ClinicRepository clinicRepository;
    private volatile Clinic cached;
    private volatile long cachedAt;

    public OpenDentalConnection(ClinicRepository clinicRepository) {
        this.clinicRepository = clinicRepository;
    }

    /** The active clinic, or a clear error when none is registered. */
    public Clinic clinic() {
        Clinic clinic = cached;
        if (clinic == null || System.currentTimeMillis() - cachedAt > CACHE_MS) {
            List<Clinic> active = clinicRepository.findByIsActiveTrue();
            if (active.isEmpty()) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                        "No active clinic. Add one to the clinics table with its Open Dental base_url and api_key.");
            }
            clinic = active.get(0);
            cached = clinic;
            cachedAt = System.currentTimeMillis();
        }
        return clinic;
    }

    /** Forget the cached clinic, after the clinics row was changed. */
    public void refresh() {
        cached = null;
    }

    /** The active clinic's Open Dental API address, without a trailing slash. */
    public String baseUrl() {
        Clinic clinic = clinic();
        String url = clinic.getBaseUrl();
        if (url == null || url.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Clinic " + clinic.getClinicCode() + " has no Open Dental base_url in the clinics table.");
        }
        return url.trim().replaceAll("/+$", "");
    }

    /** The active clinic's Open Dental key (the full Authorization value), or null. */
    public String apiKey() {
        String key = clinic().getApiKey();
        return key == null || key.isBlank() ? null : key.trim();
    }
}
