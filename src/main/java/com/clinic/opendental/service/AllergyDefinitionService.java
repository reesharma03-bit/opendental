package com.clinic.opendental.service;

import java.util.List;
import java.util.Map;

public interface AllergyDefinitionService {

    List<Map<String, Object>> getAllergyDefinitions(Map<String, String> params);

    Map<String, Object> getAllergyDefinition(Long allergyDefNum);

    Map<String, Object> createAllergyDefinition(Map<String, Object> request);

    Map<String, Object> updateAllergyDefinition(Long allergyDefNum, Map<String, Object> request);
}