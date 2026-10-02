package com.clinic.opendental.controller;

import com.clinic.opendental.dto.query.QueryRequest;
import com.clinic.opendental.dto.query.ShortQueryRequest;
import com.clinic.opendental.service.QueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/queries")
@RequiredArgsConstructor
public class QueriesController {

    private final QueryService queryService;

    /**
     * POST /queries - Run a custom query and save results to SFTP
     *
     * The results of the query are written to a file and saved to the SFTP site
     * specified in the JSON. Directory will be created if it does not exist,
     * and files already existing with the specified name will be overwritten.
     * Query results are written in comma-delimited CSV format.
     */
    @PostMapping
    public ResponseEntity<Void> runQuery(
            @Valid @RequestBody QueryRequest request) {

        queryService.runQuery(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * PUT /queries/ShortQuery - Run a short query and return results inline
     *
     * Returns at most 100 rows. The results of the query are returned as a
     * data table in the JSON. While pagination is supported for results that
     * return over 100 rows, it is recommended to instead use POST /queries
     * for longer results.
     */
    @PutMapping("/ShortQuery")
    public ResponseEntity<List<Map<String, Object>>> runShortQuery(
            @Valid @RequestBody ShortQueryRequest request,
            @RequestParam(required = false) Integer Offset) {

        return ResponseEntity.ok(
                queryService.runShortQuery(request, Offset));
    }
}