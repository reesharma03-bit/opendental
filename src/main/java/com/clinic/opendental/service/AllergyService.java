package com.clinic.opendental.service;

import java.util.List;
import java.util.Map;

public interface AllergyService {

    List<Map<String, Object>> getAllergies(Map<String, String> params);

    Map<String, Object> getAllergy(Long allergyNum);

    Map<String, Object> createAllergy(Map<String, Object> request);

    Map<String, Object> updateAllergy(Long allergyNum, Map<String, Object> request);

    void deleteAllergy(Long allergyNum);
}