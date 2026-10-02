package com.clinic.opendental.controller;

import com.clinic.opendental.exception.ApiException;
import com.clinic.opendental.service.AllergyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/allergies")
@RequiredArgsConstructor
public class AllergiesController {

    private final AllergyService allergyService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllergies(
            @RequestParam Map<String, String> params) {
        return ResponseEntity.ok(allergyService.getAllergies(params));
    }

    @GetMapping("/{allergyNum}")
    public ResponseEntity<Map<String, Object>> getAllergy(@PathVariable Long allergyNum) {
        return ResponseEntity.ok(allergyService.getAllergy(allergyNum));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createAllergy(
            @RequestBody Map<String, Object> body) {
        Map<String, Object> request = canonicalCreateRequest(body);
        Map<String, Object> created = allergyService.createAllergy(request);
        Object allergyNum = value(created, "AllergyNum", "allergy_num", "allergyNum");
        if (allergyNum != null) {
            return ResponseEntity.created(URI.create("/api/allergies/" + allergyNum)).body(created);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{allergyNum}")
    public ResponseEntity<Map<String, Object>> updateAllergy(
            @PathVariable Long allergyNum,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> request = canonicalUpdateRequest(body);
        return ResponseEntity.ok(allergyService.updateAllergy(allergyNum, request));
    }

    @DeleteMapping("/{allergyNum}")
    public ResponseEntity<Void> deleteAllergy(@PathVariable Long allergyNum) {
        allergyService.deleteAllergy(allergyNum);
        return ResponseEntity.ok().build();
    }

    private Map<String, Object> canonicalCreateRequest(Map<String, Object> body) {
        Object patNum = value(body, "PatNum", "pat_num", "patNum");
        if (!isPositiveInteger(patNum)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PatNum must be a positive integer.");
        }
        Object definitionNum = value(body, "AllergyDefNum", "allergy_def_num", "allergyDefNum");
        Object description = value(body, "defDescription", "def_description");
        if (definitionNum == null && (description == null || description.toString().isBlank())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Provide AllergyDefNum or defDescription.");
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("PatNum", patNum);
        if (definitionNum != null) request.put("AllergyDefNum", definitionNum);
        if (description != null && !description.toString().isBlank()) request.put("defDescription", description);
        copyIfPresent(body, request, "Reaction", "reaction");
        copyIfPresent(body, request, "StatusIsActive", "status_is_active", "statusIsActive");
        copyIfPresent(body, request, "DateAdverseReaction", "date_adverse_reaction", "dateAdverseReaction");
        validateFields(request);
        return request;
    }

    private Map<String, Object> canonicalUpdateRequest(Map<String, Object> body) {
        Map<String, Object> request = new LinkedHashMap<>();
        copyIfPresent(body, request, "Reaction", "reaction");
        copyIfPresent(body, request, "DateAdverseReaction", "date_adverse_reaction", "dateAdverseReaction");
        copyIfPresent(body, request, "StatusIsActive", "status_is_active", "statusIsActive");
        if (request.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Provide at least one supported allergy field to update.");
        }
        validateFields(request);
        return request;
    }

    private void validateFields(Map<String, Object> request) {
        Object active = request.get("StatusIsActive");
        if (active != null && !List.of("true", "false").contains(active.toString().toLowerCase())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "StatusIsActive must be true or false.");
        }
        Object reactionDate = request.get("DateAdverseReaction");
        if (reactionDate != null && !reactionDate.toString().matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DateAdverseReaction must use yyyy-MM-dd format.");
        }
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

    private boolean isPositiveInteger(Object value) {
        if (value == null) return false;
        try {
            return Long.parseLong(value.toString()) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}