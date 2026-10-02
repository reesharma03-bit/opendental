package com.clinic.opendental.dto.subscription;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO from Open Dental subscription API.
 * Explicit @JsonProperty keeps Open Dental PascalCase JSON working even when
 * the app-wide Jackson naming strategy is SNAKE_CASE.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionResponse {

    @JsonProperty("SubscriptionNum")
    private Long SubscriptionNum;

    @JsonProperty("EndPointUrl")
    private String EndPointUrl;

    @JsonProperty("Workstation")
    private String Workstation;

    @JsonProperty("CustomerKey")
    private String CustomerKey;

    @JsonProperty("WatchTable")
    private String WatchTable;

    @JsonProperty("PollingSeconds")
    private Integer PollingSeconds;

    @JsonProperty("UiEventType")
    private String UiEventType;

    @JsonProperty("DateTimeStart")
    private String DateTimeStart;

    @JsonProperty("DateTimeStop")
    private String DateTimeStop;

    @JsonProperty("Note")
    private String Note;

    @JsonProperty("DateTimeLastFailure")
    private String DateTimeLastFailure;

    @JsonProperty("FailureReason")
    private String FailureReason;

    @JsonProperty("SubsequentFailures")
    private Integer SubsequentFailures;

    @JsonProperty("DateTimeNextRetry")
    private String DateTimeNextRetry;
}