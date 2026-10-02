package com.clinic.opendental.dto.subscription;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for registering a webhook subscription with Open Dental.
 * Matches the POST /subscriptions request body from the Open Dental API spec.
 *
 * Explicit @JsonProperty keeps Open Dental PascalCase JSON working even when
 * the app-wide Jackson naming strategy is SNAKE_CASE.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionRequest {

    @JsonProperty("EndPointUrl")
    private String EndPointUrl;

    @JsonProperty("Workstation")
    private String Workstation;

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
}