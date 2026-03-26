package org.bring.freshmarkt.sync.sync;

import lombok.extern.slf4j.Slf4j;
import org.bring.freshmarkt.sync.client.dto.ProductDTO;
import org.bring.freshmarkt.sync.domain.Product;
import org.bring.freshmarkt.sync.domain.ProductKey;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Set;

@Slf4j
@Component
public class FreshMarktSyncProductMapper {
    private static final Set<String> KNOWN_STATUSES =
            Set.of("IN_STOCK", "LOW_STOCK", "OUT_OF_STOCK");
    private static final DateTimeFormatter EUROPEAN_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    public Product toProduct(ProductDTO productDTO) {
        return new Product(new ProductKey(productDTO.productId(), productDTO.storeId()),
                productDTO.name(),
                productDTO.ean(),
                productDTO.category(),
                parsePrice(productDTO.price()),
                productDTO.currency(),
                parseAvailability(productDTO.availability()),
                parseLastUpdated(productDTO.lastUpdated()));
    }

    private BigDecimal parsePrice(Object price) {
        switch (price) {
            case null -> {
                log.warn("Null Price encountered - defaulting to null");
                return null;
            }
            case Number number -> {
                return BigDecimal.valueOf(number.doubleValue());
            }
            case String string -> {
                try {
                    return new BigDecimal(string.trim());
                } catch (NumberFormatException ex) {
                    log.warn("Could not parse price value: '{}' - defaulting to null", string);
                    return null;
                }
            }
            default -> {
            }
        }
        log.warn("Unexpected price type: {} - defaulting to null", price.getClass().getName());
        return null;
    }

    private String parseAvailability(String availability) {
        if (availability == null) return "UNKNOWN";
        if (!KNOWN_STATUSES.contains(availability)) {
            log.warn("Unexpected availability value: {}", availability);
            return "UNKNOWN";
        }
        return availability;
    }

    private Instant parseLastUpdated(String lastUpdated) {
        if (lastUpdated == null || lastUpdated.isBlank()) {
            return null;
        }

        // try ISO format
        try {
            return Instant.parse(lastUpdated);
        } catch (DateTimeParseException ignored) {
        }
        // try European date format
        try {
            return LocalDate.parse(lastUpdated, EUROPEAN_FORMATTER)
                    .atStartOfDay()
                    .toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            log.warn("Failed to parse date: {}. Skipping timestamp.",lastUpdated);
            return null;
        }
    }
}
