package com.clinic.opendental.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Calls to Open Dental share its rate limit, and a 429 is waited out instead of failing. */
class OpenDentalRateLimiterTest {

    private final List<Long> waits = new ArrayList<>();

    @Test
    void callsToTheSameServerAreSpacedOut() {
        OpenDentalRateLimiter limiter = new OpenDentalRateLimiter(1_000, waits::add);

        limiter.waitForTurn("http://od-a");
        limiter.waitForTurn("http://od-a");
        limiter.waitForTurn("http://od-a");
        limiter.waitForTurn("http://od-b"); // another practice has its own allowance

        assertThat(waits).hasSize(2);
        assertThat(waits.get(0)).isBetween(900L, 1_000L);
        assertThat(waits.get(1)).isBetween(1_900L, 2_000L);
    }

    @Test
    void tooManyRequestsIsWaitedOutAndRetried() {
        RestTemplate template = new RestTemplate();
        template.getInterceptors().add(new OpenDentalRateLimiter(0, waits::add));
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(times(2), requestTo("http://od/api/v1/claims")).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "3"));
        server.expect(requestTo("http://od/api/v1/claims")).andRespond(withSuccess("[]", null));

        String body = template.getForObject("http://od/api/v1/claims", String.class);

        assertThat(body).isEqualTo("[]");
        assertThat(waits).hasSize(2).allSatisfy(w -> assertThat(w).isBetween(2_500L, 3_000L));
        server.verify();
    }

    @Test
    void retryAfterIsBoundedAndHasADefault() {
        assertThat(OpenDentalRateLimiter.retryAfterMs(null)).isEqualTo(5_000);
        assertThat(OpenDentalRateLimiter.retryAfterMs("0")).isEqualTo(1_000);
        assertThat(OpenDentalRateLimiter.retryAfterMs("3600")).isEqualTo(60_000);
        assertThat(OpenDentalRateLimiter.retryAfterMs("Wed, 21 Oct 2026 07:28:00 GMT")).isEqualTo(5_000);
    }
}
