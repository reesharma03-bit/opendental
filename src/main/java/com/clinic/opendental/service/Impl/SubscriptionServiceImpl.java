package com.clinic.opendental.service.Impl;

import com.clinic.opendental.client.OpenDentalClient;
import com.clinic.opendental.dto.subscription.SubscriptionRequest;
import com.clinic.opendental.dto.subscription.SubscriptionResponse;
import com.clinic.opendental.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionServiceImpl implements SubscriptionService {

    private final OpenDentalClient client;

    @Override
    public List<SubscriptionResponse> getSubscriptions(String apiKey) {
        log.info("Fetching all subscriptions");
        return client.getSubscriptions(apiKey);
    }

    @Override
    public SubscriptionResponse createSubscription(String apiKey, SubscriptionRequest request) {
        SubscriptionRules.checkCreate(fields(request));
        log.info("Creating subscription: watchTable={}, uiEventType={}",
                request.getWatchTable(), request.getUiEventType());
        return client.createSubscription(request, apiKey);
    }

    @Override
    public SubscriptionResponse updateSubscription(String apiKey, Long subscriptionNum, SubscriptionRequest request) {
        SubscriptionRules.checkUpdate(fields(request), null);
        log.info("Updating subscription {}", subscriptionNum);
        return client.updateSubscription(subscriptionNum, request, apiKey);
    }

    /** The fields the caller filled in (unset ones are left out, as Open Dental expects). */
    private static Map<String, Object> fields(SubscriptionRequest request) {
        Map<String, Object> fields = new LinkedHashMap<>();
        put(fields, "EndPointUrl", request.getEndPointUrl());
        put(fields, "Workstation", request.getWorkstation());
        put(fields, "WatchTable", request.getWatchTable());
        put(fields, "PollingSeconds", request.getPollingSeconds());
        put(fields, "UiEventType", request.getUiEventType());
        put(fields, "DateTimeStart", request.getDateTimeStart());
        put(fields, "DateTimeStop", request.getDateTimeStop());
        put(fields, "Note", request.getNote());
        return fields;
    }

    private static void put(Map<String, Object> fields, String name, Object value) {
        if (value != null) {
            fields.put(name, value);
        }
    }

    @Override
    public void deleteSubscription(String apiKey, Long subscriptionNum) {
        log.info("Deleting subscription {}", subscriptionNum);
        client.deleteSubscription(subscriptionNum, apiKey);
    }
}
