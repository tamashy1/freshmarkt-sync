package org.bring.freshmarkt.sync.sync;

import org.bring.freshmarkt.sync.client.FreshMarktClientStub;
import org.bring.freshmarkt.sync.client.dto.ProductDTO;
import org.bring.freshmarkt.sync.config.FreshMarktTestConfig;
import org.bring.freshmarkt.sync.domain.Product;
import org.bring.freshmarkt.sync.domain.ProductKey;
import org.bring.freshmarkt.sync.domain.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.HttpServerErrorException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bring.freshmarkt.sync.client.FreshMarktClientStub.*;


@SpringBootTest
@ActiveProfiles("test")
@Import(FreshMarktTestConfig.class)
class FreshMarktSyncServiceTest {

    @Autowired
    private FreshMarktSyncService syncService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SyncStateRepository syncStateRepository;

    @Autowired
    private FreshMarktClientStub clientStub;

    @BeforeEach
    void setUp() {
        clientStub.reset();
        productRepository.deleteAll();
        syncStateRepository.deleteAll();
    }

    // --- happy path ---

    @Test
    void persistsProductsOnFirstRun() {
        clientStub.willReturn(singlePageResponse(
                validProduct("fm-10042", "ZH-001"),
                validProduct("fm-10043", "ZH-001")
        ));

        syncService.sync();

        assertThat(productRepository.count()).isEqualTo(2);
    }

    @Test
    void persistsProductWithCorrectFields() {
        clientStub.willReturn(singlePageResponse(validProduct("fm-10042", "ZH-001")));

        syncService.sync();

        Product saved = productRepository.findById(new ProductKey("fm-10042", "ZH-001")).orElseThrow();
        assertThat(saved.getName()).isEqualTo("Bio Vollmilch 1L");
        assertThat(saved.getPrice()).isEqualByComparingTo(new BigDecimal("2.95"));
        assertThat(saved.getAvailability()).isEqualTo("IN_STOCK");
        assertThat(saved.getCurrency()).isEqualTo("CHF");
    }

    @Test
    void updatesExistingProductOnSubsequentSync() {
        clientStub.willReturn(singlePageResponse(validProduct("fm-10042", "ZH-001")));
        syncService.sync();

        // price changed on second sync
        clientStub.reset();
        clientStub.willReturn(singlePageResponse(new ProductDTO(
                "fm-10042", "Bio Vollmilch 1L","7610200012345","dairy",3.10,
                "CHF","LOW_STOCK","ZH-001", "2025-03-15T14:22:00Z"
        )));
        syncService.sync();

        Product updated = productRepository.findById(new ProductKey("fm-10042", "ZH-001")).orElseThrow();
        assertThat(updated.getPrice()).isEqualByComparingTo(new BigDecimal("3.10"));
        assertThat(updated.getAvailability()).isEqualTo("LOW_STOCK");
        assertThat(productRepository.count()).isEqualTo(1); // no duplicate
    }

    @Test
    void advancesSyncStateAfterSuccessfulSync() {
        clientStub.willReturn(singlePageResponse(validProduct("fm-10042", "ZH-001")));

        syncService.sync();

        assertThat(syncStateRepository.findById(1L)).isPresent();
    }

    // --- pagination ---

    @Test
    void fetchesAllPagesOnFirstRun() {
        clientStub.willReturnOnPage(1, pageResponse(1, 3, 250,
                validProduct("fm-10001", "ZH-001")));
        clientStub.willReturnOnPage(2, pageResponse(2, 3, 250,
                validProduct("fm-10002", "ZH-001")));
        clientStub.willReturnOnPage(3, pageResponse(3, 3, 250,
                validProduct("fm-10003", "ZH-001")));

        syncService.sync();

        assertThat(productRepository.count()).isEqualTo(3);
        assertThat(clientStub.getCallCount()).isEqualTo(3);
    }

    @Test
    void handlesSameProductAcrossDifferentStores() {
        clientStub.willReturn(singlePageResponse(
                validProduct("fm-10042", "ZH-001"),
                validProduct("fm-10042", "BE-007") // same productId, different store
        ));

        syncService.sync();

        assertThat(productRepository.count()).isEqualTo(2);
        assertThat(productRepository.findById(new ProductKey("fm-10042", "ZH-001"))).isPresent();
        assertThat(productRepository.findById(new ProductKey("fm-10042", "BE-007"))).isPresent();
    }

    @Test
    void handlesEmptyResponseGracefully() {
        clientStub.willReturn(emptyResponse());

        syncService.sync();

        assertThat(productRepository.count()).isEqualTo(0);
        assertThat(syncStateRepository.findById(1L)).isPresent();

    }

    // --- incremental sync ---

    @Test
    void doesNotAdvanceSyncStateOnFailure() {
        clientStub.willThrowOnCall(1,new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));
        clientStub.willThrowOnCall(2, new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));
        clientStub.willThrowOnCall(3, new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));
        syncService.sync();

        assertThat(syncStateRepository.findById(1L)).isEmpty();
    }

    @Test
    void retainsExistingProductsWhenSyncFails() {
        clientStub.willReturn(singlePageResponse(validProduct("fm-10042", "ZH-001")));
        syncService.sync();

        clientStub.reset();
        clientStub.willThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));
        syncService.sync();

        assertThat(productRepository.count()).isEqualTo(1); // existing data untouched
    }

    // --- edge cases ---

    @Test
    void handlesProductWithNullAvailability() {
        clientStub.willReturn(singlePageResponse(productWithNullAvailability("fm-10042", "ZH-001")));

        syncService.sync();

        Product saved = productRepository.findById(new ProductKey("fm-10042", "ZH-001")).orElseThrow();
        assertThat(saved.getAvailability()).isEqualTo("UNKNOWN");
    }

    @Test
    void handlesProductWithStringPrice() {
        clientStub.willReturn(singlePageResponse(productWithStringPrice("fm-10042", "ZH-001")));

        syncService.sync();

        Product saved = productRepository.findById(new ProductKey("fm-10042", "ZH-001")).orElseThrow();
        assertThat(saved.getPrice()).isEqualByComparingTo(new BigDecimal("4.80"));
    }

    @Test
    void handlesProductWithNullPrice() {
        clientStub.willReturn(singlePageResponse(productWithNullPrice("fm-10042", "ZH-001")));

        syncService.sync();

        assertThat(productRepository.count()).isEqualTo(1);
        Product saved = productRepository.findById(new ProductKey("fm-10042", "ZH-001")).orElseThrow();
        assertThat(saved.getPrice()).isNull();
    }

    @Test
    void handlesProductWithEuropeanDateFormat() {
        clientStub.willReturn(singlePageResponse(productWithEuropeanDate("fm-10042", "ZH-001")));

        syncService.sync();

        assertThat(productRepository.count()).isEqualTo(1);
    }
}
