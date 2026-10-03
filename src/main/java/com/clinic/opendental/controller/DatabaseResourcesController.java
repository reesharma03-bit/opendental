package com.clinic.opendental.controller;

import com.clinic.opendental.security.CurrentUser;
import com.clinic.opendental.security.Permission;
import com.clinic.opendental.security.PermissionResolver;
import com.clinic.opendental.service.Impl.DatabaseResourceService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Open Dental resources served from our database (od_resource_records).
 *
 * Reads never call Open Dental. Writes are saved here first and then sent to Open
 * Dental (right away when it is reachable, otherwise from the retry queue).
 *
 * GET    /api/database                  (resources and what each may change)
 * GET    /api/database/{resource}?PatNum=&Limit=&Offset=
 * GET    /api/database/{resource}/{key}
 * POST   /api/database/{resource}
 * PUT    /api/database/{resource}/{key}
 * DELETE /api/database/{resource}/{key}
 */
@RestController
@RequestMapping("/api/database")
@RequiredArgsConstructor
public class DatabaseResourcesController {

    private final DatabaseResourceService service;
    private final PermissionResolver permissions;

    /** Resources this user may read, with create/update/delete only where their role may change them. */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> resources() {
        java.util.Set<Permission> mine = CurrentUser.permissions();
        return ResponseEntity.ok(service.resources().stream()
                .filter(r -> permissions.allowed(mine, (String) r.get("resource"), false))
                .map(r -> {
                    boolean write = permissions.allowed(mine, (String) r.get("resource"), true);
                    Map<String, Object> out = new java.util.LinkedHashMap<>(r);
                    out.put("create", write && Boolean.TRUE.equals(r.get("create")));
                    out.put("update", write && Boolean.TRUE.equals(r.get("update")));
                    out.put("delete", write && Boolean.TRUE.equals(r.get("delete")));
                    return out;
                })
                .toList());
    }

    @GetMapping("/{resource}")
    public ResponseEntity<List<JsonNode>> list(@PathVariable String resource,
                                               @RequestParam(name = "PatNum", required = false) Long patNum,
                                               @RequestParam(name = "Limit", defaultValue = "1000") int limit,
                                               @RequestParam(name = "Offset", defaultValue = "0") int offset) {
        return ResponseEntity.ok(service.list(resource, patNum, limit, offset));
    }

    @GetMapping("/{resource}/{key}")
    public ResponseEntity<JsonNode> get(@PathVariable String resource, @PathVariable String key) {
        return ResponseEntity.ok(service.get(resource, key));
    }

    @PostMapping("/{resource}")
    public ResponseEntity<JsonNode> create(@PathVariable String resource, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(resource, body));
    }

    @PutMapping("/{resource}/{key}")
    public ResponseEntity<JsonNode> update(@PathVariable String resource, @PathVariable String key,
                                           @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(service.update(resource, key, body));
    }

    @DeleteMapping("/{resource}/{key}")
    public ResponseEntity<Void> delete(@PathVariable String resource, @PathVariable String key) {
        service.delete(resource, key);
        return ResponseEntity.noContent().build();
    }
}
