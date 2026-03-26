package org.bring.freshmarkt.sync.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PaginationDTO(
    int currentPage,
    int pageSize,
    int totalCount,
    int totalPages
) {
}
