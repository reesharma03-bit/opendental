package com.clinic.opendental.config;

import com.clinic.opendental.exception.ApiException;
import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.repository.ClinicRepository;
import com.clinic.opendental.security.CurrentClinic;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Where Open Dental calls go when the caller doesn't name a clinic: the current request's
 * clinic ({@link CurrentClinic}): the signed-in user's own, or the one an Admin chose. Its
 * base_url and api_key come from the clinics table; there is no global address or key.
 * Background jobs always name their clinic, so they never get here.
 */
@Component
public class OpenDentalConnection {

    private final ClinicRepository clinicRepository;

    public OpenDentalConnection(ClinicRepository clinicRepository) {
        this.clinicRepository = clinicRepository;
    }

    /** The current request's clinic while it is connected, or a clear error. */
    public Clinic clinic() {
        return CurrentClinic.requireConnected(clinicRepository);
    }

    /** The clinic's Open Dental API address, without a trailing slash. */
    public String baseUrl() {
        Clinic clinic = clinic();
        String url = clinic.getBaseUrl();
        if (url == null || url.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    clinic.getClinicName() + " isn't connected to Open Dental yet. Connect it on the Open Dental page.");
        }
        return url.trim().replaceAll("/+$", "");
    }

    /** The clinic's Open Dental key (the full Authorization value), or null. */
    public String apiKey() {
        String key = clinic().getApiKey();
        return key == null || key.isBlank() ? null : key.trim();
    }
}
