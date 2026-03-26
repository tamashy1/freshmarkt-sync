package org.bring.freshmarkt.sync.sync;

import org.bring.freshmarkt.sync.client.dto.ProductDTO;
import org.bring.freshmarkt.sync.domain.Product;
import org.bring.freshmarkt.sync.domain.ProductKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
public class FreshMarktSyncProductMapperTest {

    private FreshMarktSyncProductMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new FreshMarktSyncProductMapper();
    }

    // --- composite key ---

    @Test
    void mapsCompositeKeyCorrectly() {
        Product product = mapper.toProduct(aProductWith(2.95, "IN_STOCK", "2025-03-15T10:30:00Z"));
        assertThat(product.getProductId()).isEqualTo(new ProductKey("fm-10042", "ZH-001"));
    }

    // --- basic fields ---

    @Test
    void mapsBasicFieldsCorrectly() {
        Product product = mapper.toProduct(aProductWith(2.95, "IN_STOCK", "2025-03-15T10:30:00Z"));
        assertThat(product.getName()).isEqualTo("Bio Vollmilch 1L");
        assertThat(product.getEan()).isEqualTo("7610200012345");
        assertThat(product.getCategory()).isEqualTo("dairy");
        assertThat(product.getCurrency()).isEqualTo("CHF");
    }

    // --- price parsing ---

    @Test
    void mapsNumericPriceCorrectly() {
        Product product = mapper.toProduct(aProductWith(2.95, "IN_STOCK", "2025-03-15T10:30:00Z"));
        assertThat(product.getPrice()).isEqualByComparingTo(new BigDecimal("2.95"));
    }

    @Test
    void mapsStringPriceCorrectly() {
        Product product = mapper.toProduct(aProductWith("4.80", "IN_STOCK", "2025-03-15T10:30:00Z"));
        assertThat(product.getPrice()).isEqualByComparingTo(new BigDecimal("4.80"));
    }

    @Test
    void returnsNullPriceWhenPriceIsNull() {
        Product product = mapper.toProduct(aProductWith(null, "IN_STOCK", "2025-03-15T10:30:00Z"));
        assertThat(product.getPrice()).isNull();
    }

    @Test
    void returnsNullPriceWhenPriceIsUnparseable() {
        Product product = mapper.toProduct(aProductWith("N/A", "IN_STOCK", "2025-03-15T10:30:00Z"));
        assertThat(product.getPrice()).isNull();
    }

    @Test
    void returnsNullPriceWhenPriceIsUnexpectedType() {
        Product product = mapper.toProduct(aProductWith(true, "IN_STOCK", "2025-03-15T10:30:00Z"));
        assertThat(product.getPrice()).isNull();
    }

    @Test
    void mapsStringPriceWithWhitespaceCorrectly() {
        Product product = mapper.toProduct(aProductWith("  4.80  ", "IN_STOCK", "2025-03-15T10:30:00Z"));
        assertThat(product.getPrice()).isEqualByComparingTo(new BigDecimal("4.80"));
    }

    // --- lastUpdated parsing ---

    @Test
    void parsesIso8601DateCorrectly() {
        Product product = mapper.toProduct(aProductWith(2.95, "IN_STOCK", "2025-03-15T10:30:00Z"));
        assertThat(product.getLastUpdated()).isEqualTo(Instant.parse("2025-03-15T10:30:00Z"));
    }

    @Test
    void parsesEuropeanDateFormatCorrectly() {
        Product product = mapper.toProduct(aProductWith(2.95, "IN_STOCK", "15.03.2025"));
        assertThat(product.getLastUpdated()).isEqualTo(Instant.parse("2025-03-15T00:00:00Z"));
    }

    @Test
    void returnsNullLastUpdatedWhenUnparseable() {
        Product product = mapper.toProduct(aProductWith(2.95, "IN_STOCK", "not-a-date"));
        assertThat(product.getLastUpdated()).isNull();
    }

    @Test
    void returnsNullLastUpdatedWhenNull() {
        Product product = mapper.toProduct(aProductWith(2.95, "IN_STOCK", null));
        assertThat(product.getLastUpdated()).isNull();
    }

    // --- availability ---

    @Test
    void mapsInStockAvailabilityCorrectly() {
        Product product = mapper.toProduct(aProductWith(2.95, "IN_STOCK", "2025-03-15T10:30:00Z"));
        assertThat(product.getAvailability()).isEqualTo("IN_STOCK");
    }

    @Test
    void mapsLowStockAvailabilityCorrectly() {
        Product product = mapper.toProduct(aProductWith(2.95, "LOW_STOCK", "2025-03-15T10:30:00Z"));
        assertThat(product.getAvailability()).isEqualTo("LOW_STOCK");
    }

    @Test
    void mapsNullAvailabilityToUnknown() {
        Product product = mapper.toProduct(aProductWith(2.95, null, "2025-03-15T10:30:00Z"));
        assertThat(product.getAvailability()).isEqualTo("UNKNOWN");
    }

    @Test
    void mapsUnexpectedAvailabilityToUnknown() {
        Product product = mapper.toProduct(aProductWith(2.95, "MAYBE", "2025-03-15T10:30:00Z"));
        assertThat(product.getAvailability()).isEqualTo("UNKNOWN");
    }

    // --- helper ---

    private ProductDTO aProductWith(Object price, String availability, String lastUpdated) {
        return new ProductDTO(
                "fm-10042",
                "Bio Vollmilch 1L",
                "7610200012345",
                "dairy",
                price,
                "CHF",
                availability,
                "ZH-001",
                lastUpdated
        );
    }
}
