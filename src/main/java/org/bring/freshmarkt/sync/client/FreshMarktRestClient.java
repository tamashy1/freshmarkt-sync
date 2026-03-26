package org.bring.freshmarkt.sync.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bring.freshmarkt.sync.client.dto.ProductListResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class FreshMarktRestClient implements FreshMarktClient{

    private final RestClient freshMarktHttpClient;

    @Override
    public ProductListResponse fetchProducts(int page, int pageSize, Instant updatedSince) {
        log.debug("Fetching products from FreshMarkt: page={}, pageSize={}, updatedSince={}",
                page, pageSize, updatedSince);
        try {
            return freshMarktHttpClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder
                                .path("/products")
                                .queryParam("page",page)
                                .queryParam("pageSize", pageSize);
                        if (updatedSince != null) {
                            builder.queryParam("updatedSince", updatedSince.toString());
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .body(ProductListResponse.class);
        } catch (RestClientResponseException e) {
            log.error("FreshMarkt API error: status={}, body={}",
                e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
    }
}
