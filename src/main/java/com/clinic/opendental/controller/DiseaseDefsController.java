package com.clinic.opendental.controller;

import com.clinic.opendental.exception.ApiException;
import com.clinic.opendental.service.DiseaseDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/diseasedefs")
@RequiredArgsConstructor
public class DiseaseDefsController {
    private final DiseaseDefinitionService diseaseDefinitionService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getDiseaseDefinitions(
            @RequestParam Map<String, String> params) {
        return ResponseEntity.ok(diseaseDefinitionService.getDiseaseDefinitions(params));
    }

    @GetMapping("/{diseaseDefNum}")
    public ResponseEntity<Map<String, Object>> getDiseaseDefinition(@PathVariable Long diseaseDefNum) {
        if (diseaseDefNum <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DiseaseDefNum must be a positive integer.");
        }
        return ResponseEntity.ok(diseaseDefinitionService.getDiseaseDefinition(diseaseDefNum));
    }

    @PostMapping
    public ResponseEntity<Void> createDiseaseDefinition(@RequestBody Map<String, Object> body) {
        Object name = body.get("DiseaseName");
        if (name == null) name = body.get("diseaseName");
        if (name == null) name = body.get("disease_name");
        if (!(name instanceof String) || name.toString().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DiseaseName is required and must be a non-blank string.");
        }
        diseaseDefinitionService.createDiseaseDefinition(Map.of("DiseaseName", name.toString().trim()));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}