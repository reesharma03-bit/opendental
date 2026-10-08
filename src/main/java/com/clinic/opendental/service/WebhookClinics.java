package com.clinic.opendental.service;

import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.repository.ClinicRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * Which practice an Open Dental webhook came from, and whether to trust it. Open Dental
 * sends the practice's API key in the Authorization header of every event
 * (https://www.opendental.com/site/apievents.html). It must match the customer key of an
 * active clinic (its api_key, or opendental.api-key when the clinic has none). Only when
 * no key is configured at all (a single local test clinic) are events accepted without one.
 */
@Component
public class WebhookClinics {

    /** The caller isn't a practice we know: the webhook is refused with 401. */
    public static class UnknownPractice extends RuntimeException {
        public UnknownPractice(String message) {
            super(message);
        }
    }

    private static final String RESOLVED = WebhookClinics.class.getName() + ".clinic";

    private final ClinicRepository clinicRepository;
    private final String defaultApiKey;

    public WebhookClinics(ClinicRepository clinicRepository, @Value("${opendental.api-key:}") String defaultApiKey) {
        this.clinicRepository = clinicRepository;
        this.defaultApiKey = defaultApiKey;
    }

    /** The clinic of the webhook being handled (resolved once per request). */
    public Clinic clinic() {
        HttpServletRequest request = currentRequest();
        if (request != null && request.getAttribute(RESOLVED) instanceof Clinic clinic) {
            return clinic;
        }
        Clinic clinic = resolve(request == null ? null : request.getHeader("Authorization"));
        if (request != null) {
            request.setAttribute(RESOLVED, clinic);
        }
        return clinic;
    }

    public String clinicCode() {
        return clinic().getClinicCode();
    }

    Clinic resolve(String authorization) {
        List<Clinic> active = clinicRepository.findByIsActiveTrue();
        if (active.isEmpty()) {
            throw new UnknownPractice("No active clinic to save the webhook under");
        }
        String sentKey = customerKey(authorization);
        boolean anyKeyConfigured = false;
        for (Clinic clinic : active) {
            String key = customerKey(keyOf(clinic));
            anyKeyConfigured |= !key.isEmpty();
            if (!key.isEmpty() && key.equals(sentKey)) {
                return clinic;
            }
        }
        if (!anyKeyConfigured && active.size() == 1) {
            return active.get(0); // local testing: nothing to check the caller against
        }
        throw new UnknownPractice(sentKey.isEmpty()
                ? "Webhook without an Open Dental API key"
                : "Webhook from an unknown practice: no active clinic has the API key it was sent with");
    }

    private String keyOf(Clinic clinic) {
        return clinic.getApiKey() == null || clinic.getApiKey().isBlank() ? defaultApiKey : clinic.getApiKey();
    }

    /**
     * The customer key part of an Open Dental key, whatever form it is written in:
     * "ODFHIR {DeveloperKey}/{CustomerKey}", "{DeveloperKey}/{CustomerKey}" or just the customer key.
     */
    static String customerKey(String key) {
        if (key == null) return "";
        String k = key.trim();
        if (k.regionMatches(true, 0, "ODFHIR ", 0, 7)) k = k.substring(7).trim();
        int slash = k.lastIndexOf('/');
        return slash >= 0 ? k.substring(slash + 1).trim() : k;
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest() : null;
    }
}
