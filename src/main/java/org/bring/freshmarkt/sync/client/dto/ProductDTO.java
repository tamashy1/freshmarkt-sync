package org.bring.freshmarkt.sync.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductDTO(
    String productId,
    String name,
    String ean,
    String category,
    Object price,
    String currency,
    String availability,
    String storeId,
    String lastUpdated
) {
}
