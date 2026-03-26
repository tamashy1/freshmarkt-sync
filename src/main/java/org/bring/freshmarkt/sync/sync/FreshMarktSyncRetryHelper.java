package org.bring.freshmarkt.sync.sync;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bring.freshmarkt.sync.client.FreshMarktClient;
import org.bring.freshmarkt.sync.client.dto.ProductListResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class FreshMarktSyncRetryHelper {

    private final FreshMarktClient freshMarktClient;
    private static final int MAX_ATTEMPTS = 3;
    private static final int PAGE_SIZE = 100; // max allowed, fewer calls
    public ProductListResponse fetchWithRetry(int page, Instant updatedSince) {
        int attempts = 0;
        while (true) {
            try {
                return freshMarktClient.fetchProducts(page, PAGE_SIZE, updatedSince);
            } catch (HttpClientErrorException.TooManyRequests e) {
                attempts++;
                long retryAfter = extractRetryAfter(e);
                log.warn("Rate limited. Waiting {}s before retry {}/{}", retryAfter,attempts,MAX_ATTEMPTS);
                if (attempts >= MAX_ATTEMPTS) throw e;
                sleep(retryAfter);
            } catch (HttpClientErrorException.Unauthorized e) {
                // log and abort, retrying won't help
                log.error("Invalid FreshMarkt API key - aborting sync");
                throw e;
            } catch (HttpClientErrorException.BadRequest e) {
                // log and abort, retrying won't help
                log.error("Bad Request to FreshMarkt API - aborting sync: {}",
                        e.getResponseBodyAsString());
                throw e;
            } catch (HttpServerErrorException e) {
                attempts++;
                log.warn("FreshMarkt server error. Retry {}/{}", attempts,MAX_ATTEMPTS);
                if (attempts >= MAX_ATTEMPTS) throw e;
                sleep((5000L));
            } catch (Exception e) {
                log.error("Unexpected error during Freshmarkt sync on a page {}: {}", page, e.getMessage(), e);
                throw e;
            }
        }
    }

    private long extractRetryAfter(HttpClientErrorException.TooManyRequests e) {
        String retryAfter = e.getResponseHeaders() != null ? e.getResponseHeaders().getFirst("Retry-After") : null;
        if(retryAfter != null) {
            try {
                return Long.parseLong(retryAfter.trim()) * 1000L;
            } catch (NumberFormatException ex) {
                log.warn("Could not parse Retry-After header value: '{}'", retryAfter);
            }
        }
        return 60_000L; //default fallback
    }
    private void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during retry wait", ex);
        }
    }
}
