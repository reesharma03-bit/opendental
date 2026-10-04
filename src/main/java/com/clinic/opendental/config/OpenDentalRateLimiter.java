package com.clinic.opendental.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;

/**
 * Keeps calls to each Open Dental server within its rate limit.
 *
 * <p>Open Dental allows one request per second per API key (one per five seconds when the
 * key only has read access), and answers more with 429 and a Retry-After header. Every
 * call to a server waits for its turn here, so the sync, dashboard writes and lookups
 * share the allowance instead of tripping it. A 429 that still happens (another backend
 * instance using the same key) is waited out and retried.</p>
 */
@Slf4j
public class OpenDentalRateLimiter implements ClientHttpRequestInterceptor {

    static final int MAX_RETRIES = 5;
    private static final long DEFAULT_RETRY_AFTER_MS = 5_000;
    private static final long MAX_RETRY_AFTER_MS = 60_000;

    private final long minIntervalMs;
    private final LongConsumer sleeper;
    /** Per server (scheme://host:port): the earliest time the next call may start. */
    private final Map<String, Long> nextSlot = new ConcurrentHashMap<>();

    public OpenDentalRateLimiter(long minIntervalMs) {
        this(minIntervalMs, OpenDentalRateLimiter::sleep);
    }

    OpenDentalRateLimiter(long minIntervalMs, LongConsumer sleeper) {
        this.minIntervalMs = Math.max(0, minIntervalMs);
        this.sleeper = sleeper;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        String server = request.getURI().getScheme() + "://" + request.getURI().getAuthority();
        for (int attempt = 0; ; attempt++) {
            waitForTurn(server);
            ClientHttpResponse response = execution.execute(request, body);
            if (response.getStatusCode().value() != HttpStatus.TOO_MANY_REQUESTS.value() || attempt >= MAX_RETRIES) {
                return response;
            }
            long wait = retryAfterMs(response.getHeaders().getFirst("Retry-After"));
            response.close();
            log.info("Open Dental at {} is busy (429); retrying in {} ms", server, wait);
            // Push everyone else back too: the server asked this key to slow down.
            nextSlot.merge(server, System.currentTimeMillis() + wait, Math::max);
        }
    }

    /** Reserves the next free slot for the server and sleeps until it starts. */
    void waitForTurn(String server) {
        if (minIntervalMs == 0 && !nextSlot.containsKey(server)) {
            return;
        }
        long now = System.currentTimeMillis();
        long[] start = new long[1];
        nextSlot.compute(server, (key, next) -> {
            start[0] = next == null ? now : Math.max(now, next);
            return start[0] + minIntervalMs;
        });
        long wait = start[0] - now;
        if (wait > 0) {
            sleeper.accept(wait);
        }
    }

    static long retryAfterMs(String header) {
        if (header == null || header.isBlank()) {
            return DEFAULT_RETRY_AFTER_MS;
        }
        try {
            return Math.min(MAX_RETRY_AFTER_MS, Math.max(1_000, Long.parseLong(header.trim()) * 1_000));
        } catch (NumberFormatException e) {
            return DEFAULT_RETRY_AFTER_MS; // an HTTP date: not worth parsing for a short wait
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Open Dental's rate limit", e);
        }
    }
}
