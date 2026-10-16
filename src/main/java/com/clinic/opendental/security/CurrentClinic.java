package com.clinic.opendental.security;

import com.clinic.opendental.exception.ApiException;
import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.repository.ClinicRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The clinic the current request works on. Many clinics share one SmileOS:
 * <ul>
 *   <li>an <b>Admin</b> (SmileOS administrator) works on any clinic: the one chosen with the
 *       clinic switcher ({@value #HEADER} header), or the first connected one;</li>
 *   <li>a <b>Dentist</b> works on a clinic they own (clinics.owner_user_id): the one chosen with
 *       the switcher, or their first (connected) one. Choosing a clinic they don't own is refused;</li>
 *   <li>everyone else works on their own clinic (app_users.clinic_id, carried in the session)
 *       and can't choose another: the header is ignored for them.</li>
 * </ul>
 * Without a signed-in user (background jobs, webhooks) there is no current clinic: that code
 * names its clinic itself, so nothing ever falls back to "some" clinic.
 */
public final class CurrentClinic {

    /** Sent by the dashboard's clinic switcher (Admins only): the clinic code. */
    public static final String HEADER = "X-SmileOS-Clinic";
    private static final String RESOLVED = CurrentClinic.class.getName();
    private static final Object NONE = new Object();

    private CurrentClinic() {
    }

    /** The current request's clinic, if it has one. */
    public static Optional<Clinic> find(ClinicRepository clinics) {
        HttpServletRequest request = request();
        Role role = CurrentUser.role();
        if (request == null || role == null) return Optional.empty();
        Object cached = request.getAttribute(RESOLVED);
        if (cached != null) return cached == NONE ? Optional.empty() : Optional.of((Clinic) cached);

        Clinic clinic;
        if (role == Role.ADMIN) {
            String code = request.getHeader(HEADER);
            clinic = code != null && !code.isBlank()
                    ? clinics.findByClinicCode(code.trim()).orElseThrow(() ->
                            new ApiException(HttpStatus.NOT_FOUND, "There is no clinic " + code.trim() + "."))
                    : clinics.findByIsActiveTrue().stream().findFirst().orElse(null);
        } else if (role == Role.DENTIST) {
            UUID me = CurrentUser.id();
            String code = request.getHeader(HEADER);
            if (code != null && !code.isBlank()) {
                clinic = clinics.findByClinicCode(code.trim())
                        .filter(c -> me != null && me.equals(c.getOwnerUserId()))
                        .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN,
                                "You don't have access to clinic " + code.trim() + "."));
            } else {
                List<Clinic> own = me == null ? List.of() : clinics.findByOwnerUserIdOrderByCreatedAtAsc(me);
                clinic = own.stream().filter(c -> Boolean.TRUE.equals(c.getIsActive())).findFirst()
                        .orElse(own.isEmpty() ? null : own.get(0));
            }
        } else {
            UUID id = CurrentUser.clinicId();
            clinic = id == null ? null : clinics.findById(id).orElse(null);
        }
        request.setAttribute(RESOLVED, clinic == null ? NONE : clinic);
        return Optional.ofNullable(clinic);
    }

    /** The current request's clinic, or a message saying what to do when there is none. */
    public static Clinic require(ClinicRepository clinics) {
        return find(clinics).orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, missingMessage()));
    }

    public static UUID requireId(ClinicRepository clinics) {
        return require(clinics).getId();
    }

    /**
     * The current clinic, for its data (patients, appointments, records, dashboard, assistant) and
     * for Open Dental calls: only while it is connected. A disconnected clinic's data stays stored
     * but is hidden until it is connected again on the Open Dental page.
     */
    public static Clinic requireConnected(ClinicRepository clinics) {
        Clinic clinic = require(clinics);
        if (!Boolean.TRUE.equals(clinic.getIsActive())) {
            Role role = CurrentUser.role();
            throw new ApiException(HttpStatus.CONFLICT, clinic.getClinicName() + " isn't connected to Open Dental, so its data isn't shown. "
                    + (role == Role.ADMIN || role == Role.DENTIST
                        ? "Connect it on the Open Dental page."
                        : "Ask your dentist or SmileOS admin to connect it."));
        }
        return clinic;
    }

    public static UUID requireConnectedId(ClinicRepository clinics) {
        return requireConnected(clinics).getId();
    }

    static String missingMessage() {
        Role role = CurrentUser.role();
        if (role == null) return "No clinic for this request.";
        return switch (role) {
            case ADMIN -> "No clinic is connected yet. Register one on the Open Dental page.";
            case DENTIST -> "Register your clinic on the Open Dental page first.";
            default -> "Your account isn't linked to a clinic yet. Ask your SmileOS admin.";
        };
    }

    private static HttpServletRequest request() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest() : null;
    }
}
