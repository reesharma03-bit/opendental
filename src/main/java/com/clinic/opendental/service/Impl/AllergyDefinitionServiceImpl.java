package com.clinic.opendental.service.Impl;

import com.clinic.opendental.client.OpenDentalClient;
import com.clinic.opendental.exception.ApiException;
import com.clinic.opendental.service.AllergyDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AllergyDefinitionServiceImpl implements AllergyDefinitionService {

    private final OpenDentalClient client;

    @Override
    public List<Map<String, Object>> getAllergyDefinitions(Map<String, String> params) {
        return callOpenDental(() -> client.getAllergyDefs(params));
    }

    @Override
    public Map<String, Object> getAllergyDefinition(Long allergyDefNum) {
        return callOpenDental(() -> client.getAllergyDef(allergyDefNum));
    }

    @Override
    public Map<String, Object> createAllergyDefinition(Map<String, Object> request) {
        return callOpenDental(() -> client.createAllergyDef(request));
    }

    @Override
    public Map<String, Object> updateAllergyDefinition(Long allergyDefNum, Map<String, Object> request) {
        return callOpenDental(() -> client.updateAllergyDef(allergyDefNum, request));
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