package org.bring.freshmarkt.sync.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductListResponse(
        List<ProductDTO> data,
        PaginationDTO pagination
) {
}
