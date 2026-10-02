package com.clinic.opendental.service;

import com.clinic.opendental.dto.query.QueryRequest;
import com.clinic.opendental.dto.query.ShortQueryRequest;

import java.util.List;
import java.util.Map;

public interface QueryService {

    /**
     * POST /queries - Run a custom query and save results to SFTP
     */
    void runQuery(QueryRequest request);

    /**
     * PUT /queries/ShortQuery - Run a short query and return results inline (max 100 rows)
     */
    List<Map<String, Object>> runShortQuery(ShortQueryRequest request, Integer offset);
}