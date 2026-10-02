package com.clinic.opendental.service.Impl;

import com.clinic.opendental.client.OpenDentalClient;
import com.clinic.opendental.exception.ApiException;
import com.clinic.opendental.service.DiseaseDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class DiseaseDefinitionServiceImpl implements DiseaseDefinitionService {
    private final OpenDentalClient client;

    @Override
    public List<Map<String, Object>> getDiseaseDefinitions(Map<String, String> params) {
        return callOpenDental(() -> client.getDiseaseDefs(params));
    }

    @Override
    public Map<String, Object> getDiseaseDefinition(Long diseaseDefNum) {
        return callOpenDental(() -> client.getDiseaseDef(diseaseDefNum));
    }

    @Override
    public void createDiseaseDefinition(Map<String, Object> request) {
        callOpenDental(() -> {
            client.createDiseaseDef(request);
            return null;
        });
    }

    private <T> T callOpenDental(Supplier<T> call) {
        try {
            return call.get();
        } catch (ResourceAccessException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Unable to reach the Open Dental API. Check the service URL and connectivity.");
        } catch (HttpServerErrorException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Open Dental returned a server error (" + e.getStatusCode().value() + "). Please try again.");
        }
    }
}