package com.clinic.opendental.service.Impl;

import com.clinic.opendental.client.OpenDentalClient;
import com.clinic.opendental.dto.query.QueryRequest;
import com.clinic.opendental.dto.query.ShortQueryRequest;
import com.clinic.opendental.service.QueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class QueryServiceImpl implements QueryService {

    private final OpenDentalClient client;

    @Override
    public void runQuery(QueryRequest request) {
        client.runQuery(request);
    }

    @Override
    public List<Map<String, Object>> runShortQuery(ShortQueryRequest request, Integer offset) {
        return client.runShortQuery(request, offset);
    }
}