package com.clinic.opendental.service;

import com.clinic.opendental.dto.subscription.SubscriptionRequest;
import com.clinic.opendental.dto.subscription.SubscriptionResponse;

import java.util.List;

public interface SubscriptionService {

    /**
     * GET /subscriptions - List all webhook subscriptions.
     * @param apiKey the Open Dental API key (identifies the customer)
     */
    List<SubscriptionResponse> getSubscriptions(String apiKey);

    /**
     * POST /subscriptions - Register a new webhook subscription with Open Dental.
     * @param apiKey the Open Dental API key (identifies the customer)
     */
    SubscriptionResponse createSubscription(String apiKey, SubscriptionRequest request);

    /**
     * PUT /subscriptions/{subscriptionNum} - Update an existing webhook subscription.
     * @param apiKey the Open Dental API key (identifies the customer)
     */
    SubscriptionResponse updateSubscription(String apiKey, Long subscriptionNum, SubscriptionRequest request);

    /**
     * DELETE /subscriptions/{subscriptionNum} - Delete a webhook subscription.
     * @param apiKey the Open Dental API key (identifies the customer)
     */
    void deleteSubscription(String apiKey, Long subscriptionNum);
}
