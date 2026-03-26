package org.bring.freshmarkt.sync.client;

import org.bring.freshmarkt.sync.client.dto.PaginationDTO;
import org.bring.freshmarkt.sync.client.dto.ProductDTO;
import org.bring.freshmarkt.sync.client.dto.ProductListResponse;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FreshMarktClientStub implements FreshMarktClient {

    private final Map<Integer, ProductListResponse> responsesByPage = new HashMap<>();
    private final Map<Integer, RuntimeException> throwOnCall = new HashMap<>();
    private int callCount = 0;

    // --- configuration methods ---

    public void willReturnOnPage(int page, ProductListResponse response) {
        responsesByPage.put(page, response);
    }

    public void willReturn(ProductListResponse response) {
        responsesByPage.put(1, response);
    }

    public void willThrowOnCall(int callNumber, RuntimeException e) {
        throwOnCall.put(callNumber, e);
    }

    public void willThrow(RuntimeException e) {
        throwOnCall.put(1, e);
    }

    public int getCallCount() {
        return callCount;
    }

    public void reset() {
        responsesByPage.clear();
        throwOnCall.clear();
        callCount = 0;
    }

    // --- interface implementation ---

    @Override
    public ProductListResponse fetchProducts(int page, int pageSize, Instant updatedSince) {
        callCount++;

        RuntimeException toThrow = throwOnCall.get(callCount);
        if (toThrow != null) {
            throw toThrow;
        }

        return responsesByPage.getOrDefault(page, emptyPage(page));
    }

    // --- static factory helpers ---

    public static ProductListResponse singlePageResponse(ProductDTO... products) {
        return new ProductListResponse(
                List.of(products),
                new PaginationDTO(1, 100, products.length, 1)
        );
    }

    public static ProductListResponse pageResponse(int currentPage, int totalPages,
                                                   int totalCount, ProductDTO... products) {
        return new ProductListResponse(
                List.of(products),
                new PaginationDTO(currentPage, 100, totalCount, totalPages)
        );
    }

    public static ProductListResponse emptyResponse() {
        return new ProductListResponse(
                List.of(),
                new PaginationDTO(1, 100, 0, 1)
        );
    }

    private static ProductListResponse emptyPage(int page) {
        return new ProductListResponse(
                List.of(),
                new PaginationDTO(page, 100, 0, 1)
        );
    }

    // --- product dto factories ---

    public static ProductDTO validProduct(String productId, String storeId) {
        return new ProductDTO(
                productId,
                "Bio Vollmilch 1L",
                "7610200012345",
                "dairy",
                2.95,
                "CHF",
                "IN_STOCK",
                storeId,
                "2025-03-15T10:30:00Z"
        );
    }

    public static ProductDTO productWithStringPrice(String productId, String storeId) {
        return new ProductDTO(
                productId,
                "Freiland Eier 6er",
                "7610200012346",
                "dairy",
                "4.80",
                "CHF",
                "IN_STOCK",
                storeId,
                "2025-03-15T10:30:00Z"
        );
    }

    public static ProductDTO productWithNullAvailability(String productId, String storeId) {
        return new ProductDTO(
                productId,
                "Bio Vollmilch 1L",
                "7610200012345",
                "dairy",
                2.95,
                "CHF",
                null,
                storeId,
                "2025-03-15T10:30:00Z"
        );
    }

    public static ProductDTO productWithEuropeanDate(String productId, String storeId) {
        return new ProductDTO(
                productId,
                "Bio Vollmilch 1L",
                "7610200012345",
                "dairy",
                2.95,
                "CHF",
                "IN_STOCK",
                storeId,
                "15.03.2025"
        );
    }

    public static ProductDTO productWithNullPrice(String productId, String storeId) {
        return new ProductDTO(
                productId,
                "Bio Vollmilch 1L",
                "7610200012345",
                "dairy",
                null,
                "CHF",
                "IN_STOCK",
                storeId,
                "2025-03-15T10:30:00Z"
        );
    }
}
