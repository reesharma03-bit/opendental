package com.clinic.opendental.controller;

import com.clinic.opendental.exception.ApiException;
import com.clinic.opendental.service.AllergyDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/allergydefs")
@RequiredArgsConstructor
public class AllergyDefsController {

    private final AllergyDefinitionService allergyDefinitionService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllergyDefinitions(
            @RequestParam Map<String, String> params) {
        return ResponseEntity.ok(allergyDefinitionService.getAllergyDefinitions(params));
    }

    @GetMapping("/{allergyDefNum}")
    public ResponseEntity<Map<String, Object>> getAllergyDefinition(
            @PathVariable Long allergyDefNum) {
        return ResponseEntity.ok(allergyDefinitionService.getAllergyDefinition(allergyDefNum));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createAllergyDefinition(
            @RequestBody Map<String, Object> body) {
        Map<String, Object> created = allergyDefinitionService.createAllergyDefinition(
                canonicalCreateRequest(body));
        Object allergyDefNum = value(created, "AllergyDefNum", "allergy_def_num", "allergyDefNum");
        if (allergyDefNum != null) {
            return ResponseEntity.created(URI.create("/api/allergydefs/" + allergyDefNum)).body(created);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{allergyDefNum}")
    public ResponseEntity<Map<String, Object>> updateAllergyDefinition(
            @PathVariable Long allergyDefNum,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(allergyDefinitionService.updateAllergyDefinition(
                allergyDefNum,
                canonicalUpdateRequest(body)));
    }

    private Map<String, Object> canonicalCreateRequest(Map<String, Object> body) {
        Object description = value(body, "Description", "Description ", "description", "description_");
        if (description == null || description.toString().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Description is required.");
        }
        return Map.of("Description", description.toString().trim());
    }

    private Map<String, Object> canonicalUpdateRequest(Map<String, Object> body) {
        Map<String, Object> request = new LinkedHashMap<>();
        copyIfPresent(body, request, "Description", "Description ", "description", "description_");
        copyIfPresent(body, request, "IsHidden", "isHidden", "is_hidden");
        if (request.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Provide Description or IsHidden to update.");
        }

        Object description = request.get("Description");
        if (description != null && description.toString().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Description cannot be blank.");
        }
        Object isHidden = request.get("IsHidden");
        if (isHidden != null && !List.of("true", "false").contains(isHidden.toString().toLowerCase())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IsHidden must be true or false.");
        }
        if (description != null) request.put("Description", description.toString().trim());
        if (isHidden != null) request.put("IsHidden", isHidden.toString().toLowerCase());
        return request;
    }

    private void copyIfPresent(
            Map<String, Object> source,
            Map<String, Object> target,
            String canonicalKey,
            String... aliases) {
        Object field = source.containsKey(canonicalKey)
                ? source.get(canonicalKey)
                : value(source, aliases);
        if (field != null) target.put(canonicalKey, field);
    }

    private Object value(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            if (source.containsKey(key)) return source.get(key);
        }
        return null;
    }
}