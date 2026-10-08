package com.clinic.opendental.controller;

import com.clinic.opendental.client.OpenDentalClient;
import com.clinic.opendental.exception.ApiException;
import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.repository.ClinicRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

import java.util.Map;

/**
 * Open Dental's Account Module views for a patient's family
 * (https://www.opendental.com/site/apiaccountmodules.html):
 *
 * <ul>
 *   <li>GET /api/accountmodules/{PatNum}/Aging: aged balances, insurance estimate, unearned</li>
 *   <li>GET /api/accountmodules/{PatNum}/PatientBalances: each family member's balance and the family total</li>
 *   <li>GET /api/accountmodules/{PatNum}/ServiceDateView?isFamily=: charges and credits by service date</li>
 * </ul>
 *
 * Open Dental computes these on request from the whole ledger, so they are read live and
 * not stored. Responses are passed through unchanged (Open Dental's field names).
 */
@RestController
@RequestMapping("/api/accountmodules")
@RequiredArgsConstructor
public class AccountModulesController {

    private final OpenDentalClient client;
    private final ClinicRepository clinicRepository;

    @GetMapping("/{patNum}/Aging")
    public JsonNode aging(@PathVariable long patNum) {
        return fetch(patNum, "Aging", Map.of());
    }

    @GetMapping("/{patNum}/PatientBalances")
    public JsonNode patientBalances(@PathVariable long patNum) {
        return fetch(patNum, "PatientBalances", Map.of());
    }

    @GetMapping("/{patNum}/ServiceDateView")
    public JsonNode serviceDateView(@PathVariable long patNum,
                                    @RequestParam(name = "isFamily", defaultValue = "false") boolean isFamily) {
        return fetch(patNum, "ServiceDateView", Map.of("isFamily", String.valueOf(isFamily)));
    }

    private JsonNode fetch(long patNum, String view, Map<String, String> params) {
        if (patNum <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a patient that is saved in Open Dental.");
        }
        Clinic clinic = clinicRepository.findByIsActiveTrue().stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No active clinic configured."));
        try {
            return client.getRaw("/accountmodules/" + patNum + "/" + view, params, clinic.getBaseUrl(), clinic.getApiKey());
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 404) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Patient " + patNum + " was not found in Open Dental.");
            }
            String detail = e.getResponseBodyAsString();
            throw new ApiException(e.getStatusCode().value() == 400 ? HttpStatus.BAD_REQUEST : HttpStatus.BAD_GATEWAY,
                    "Open Dental could not return the account: " + (detail.isBlank() ? e.getStatusText() : detail));
        } catch (ResourceAccessException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Open Dental is not reachable right now. Try again shortly.");
        }
    }
}
