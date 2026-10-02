package com.clinic.opendental.service;

import java.util.List;
import java.util.Map;

public interface DiseaseDefinitionService {
    List<Map<String, Object>> getDiseaseDefinitions(Map<String, String> params);
    Map<String, Object> getDiseaseDefinition(Long diseaseDefNum);
    void createDiseaseDefinition(Map<String, Object> request);
}