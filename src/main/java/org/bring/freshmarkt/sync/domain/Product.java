package org.bring.freshmarkt.sync.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "products")
public class Product {

    @EmbeddedId
    private ProductKey productId;
    private String name;
    private String ean;
    private String category;
    private BigDecimal price;
    private String currency;
    private String availability;
    private Instant lastUpdated;
}
