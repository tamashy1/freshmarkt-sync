package org.bring.freshmarkt.sync.client;

import org.bring.freshmarkt.sync.client.dto.ProductListResponse;

import java.time.Instant;

public interface FreshMarktClient {

    /**
     *
     * @param page Page number
     * @param pageSize number of items per page, max 100
     * @param updatedSince if provided, only returns products updated after this time
     */
    ProductListResponse fetchProducts(int page, int pageSize, Instant updatedSince);
}
