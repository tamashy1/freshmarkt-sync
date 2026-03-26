package org.bring.freshmarkt.sync.sync;

import org.bring.freshmarkt.sync.client.FreshMarktClientStub;
import org.bring.freshmarkt.sync.client.dto.ProductListResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.bring.freshmarkt.sync.client.FreshMarktClientStub.singlePageResponse;
import static org.bring.freshmarkt.sync.client.FreshMarktClientStub.validProduct;

public class FreshMarktSyncRetryHelperTest {
    private FreshMarktClientStub clientStub;
    private FreshMarktSyncRetryHelper retryHelper;

    @BeforeEach
    void setUp() {
        clientStub = new FreshMarktClientStub();
        retryHelper = new FreshMarktSyncRetryHelper(clientStub);
    }

    // --- happy path ---

    @Test
    void returnsResponseOnFirstAttempt() {
        ProductListResponse expected = singlePageResponse(validProduct("fm-10042", "ZH-001"));
        clientStub.willReturn(expected);

        ProductListResponse result = retryHelper.fetchWithRetry(1, null);

        assertThat(result).isEqualTo(expected);
        assertThat(clientStub.getCallCount()).isEqualTo(1);
    }

    // --- 429 retry ---

    @Test
    void retriesOnRateLimitAndSucceeds() {
        clientStub.willThrowOnCall(1, rateLimitedException(30));
        clientStub.willReturnOnPage(1, singlePageResponse(validProduct("fm-10042", "ZH-001")));

        ProductListResponse result = retryHelper.fetchWithRetry(1, null);

        assertThat(result).isNotNull();
        assertThat(clientStub.getCallCount()).isEqualTo(2);
    }

    @Test
    void honorsRetryAfterHeaderOnRateLimit() {
        clientStub.willThrowOnCall(1, rateLimitedException(60));
        clientStub.willReturnOnPage(1, singlePageResponse(validProduct("fm-10042", "ZH-001")));

        retryHelper.fetchWithRetry(1, null);

        assertThat(clientStub.getCallCount()).isEqualTo(2);
    }

    @Test
    void throwsAfterMaxAttemptsOnRateLimit() {
        clientStub.willThrowOnCall(1, rateLimitedException(1));
        clientStub.willThrowOnCall(2, rateLimitedException(1));
        clientStub.willThrowOnCall(3, rateLimitedException(1));

        assertThatThrownBy(() -> retryHelper.fetchWithRetry(1, null))
                .isInstanceOf(HttpClientErrorException.class)
                .extracting(e -> ((HttpClientErrorException) e).getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        assertThat(clientStub.getCallCount()).isEqualTo(3);
    }

    // --- 500 retry ---

    @Test
    void retriesOnServerErrorAndSucceeds() {
        clientStub.willThrowOnCall(1, new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));
        clientStub.willReturnOnPage(1, singlePageResponse(validProduct("fm-10042", "ZH-001")));

        ProductListResponse result = retryHelper.fetchWithRetry(1, null);

        assertThat(result).isNotNull();
        assertThat(clientStub.getCallCount()).isEqualTo(2);
    }

    @Test
    void throwsAfterMaxAttemptsOnServerError() {
        clientStub.willThrowOnCall(1, new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));
        clientStub.willThrowOnCall(2, new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));
        clientStub.willThrowOnCall(3, new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> retryHelper.fetchWithRetry(1, null))
                .isInstanceOf(HttpServerErrorException.class)
                .extracting(e -> ((HttpServerErrorException) e).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        assertThat(clientStub.getCallCount()).isEqualTo(3);
    }

    // --- non retryable errors ---

    @Test
    void doesNotRetryOnUnauthorized() {
        clientStub.willThrowOnCall(1, new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> retryHelper.fetchWithRetry(1, null))
                .isInstanceOf(HttpClientErrorException.class)
                .extracting(e -> ((HttpClientErrorException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(clientStub.getCallCount()).isEqualTo(1); // no retry
    }

    @Test
    void doesNotRetryOnBadRequest() {
        clientStub.willThrowOnCall(1, new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> retryHelper.fetchWithRetry(1, null))
                .isInstanceOf(HttpClientErrorException.class)
                .extracting(e -> ((HttpClientErrorException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(clientStub.getCallCount()).isEqualTo(1); // no retry
    }

    @Test
    void doesNotRetryOnUnexpectedException() {
        clientStub.willThrowOnCall(1, new RuntimeException("Unexpected"));

        assertThatThrownBy(() -> retryHelper.fetchWithRetry(1, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Unexpected");

        assertThat(clientStub.getCallCount()).isEqualTo(1); // no retry
    }

    // --- updatedSince passthrough ---

    @Test
    void passesUpdatedSinceToClient() {
        Instant updatedSince = Instant.parse("2025-03-15T10:30:00Z");
        clientStub.willReturn(singlePageResponse(validProduct("fm-10042", "ZH-001")));

        retryHelper.fetchWithRetry(1, updatedSince);

        assertThat(clientStub.getCallCount()).isEqualTo(1);
    }

    // --- helpers ---

    private static HttpClientErrorException rateLimitedException(int retryAfterSeconds) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Retry-After", String.valueOf(retryAfterSeconds));
        return HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too Many Requests",
                headers,
                new byte[0],
                StandardCharsets.UTF_8
        );
    }
}
