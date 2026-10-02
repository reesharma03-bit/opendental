package com.clinic.opendental.service.Impl;

import com.clinic.opendental.client.OpenDentalClient;
import com.clinic.opendental.dto.subscription.SubscriptionRequest;
import com.clinic.opendental.dto.subscription.SubscriptionResponse;
import com.clinic.opendental.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

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
        log.info("Creating subscription: watchTable={}, endpoint={}",
                request.getWatchTable(), request.getEndPointUrl());
        return client.createSubscription(request, apiKey);
    }

    @Override
    public SubscriptionResponse updateSubscription(String apiKey, Long subscriptionNum, SubscriptionRequest request) {
        log.info("Updating subscription {}", subscriptionNum);
        return client.updateSubscription(subscriptionNum, request, apiKey);
    }

    @Override
    public void deleteSubscription(String apiKey, Long subscriptionNum) {
        log.info("Deleting subscription {}", subscriptionNum);
        client.deleteSubscription(subscriptionNum, apiKey);
    }
}
