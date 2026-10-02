package com.clinic.opendental.service.Impl;

import com.clinic.opendental.client.OpenDentalClient;
import com.clinic.opendental.exception.ApiException;
import com.clinic.opendental.service.AllergyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AllergyServiceImpl implements AllergyService {

    private final OpenDentalClient client;

    @Override
    public List<Map<String, Object>> getAllergies(Map<String, String> params) {
        return callOpenDental(() -> client.getAllergies(params));
    }

    @Override
    public Map<String, Object> getAllergy(Long allergyNum) {
        return callOpenDental(() -> client.getAllergy(allergyNum));
    }

    @Override
    public Map<String, Object> createAllergy(Map<String, Object> request) {
        return callOpenDental(() -> client.createAllergy(request));
    }

    @Override
    public Map<String, Object> updateAllergy(Long allergyNum, Map<String, Object> request) {
        return callOpenDental(() -> client.updateAllergy(allergyNum, request));
    }

    @Override
    public void deleteAllergy(Long allergyNum) {
        callOpenDental(() -> {
            client.deleteAllergy(allergyNum);
            return null;
        });
    }

    private <T> T callOpenDental(OpenDentalCall<T> call) {
        try {
            return call.execute();
        } catch (ResourceAccessException e) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "Unable to reach the Open Dental API. Check the service URL and connectivity.");
        }
    }

    @FunctionalInterface
    private interface OpenDentalCall<T> {
        T execute();
    }
}