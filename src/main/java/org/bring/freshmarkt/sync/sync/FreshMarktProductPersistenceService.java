package org.bring.freshmarkt.sync.sync;

import lombok.RequiredArgsConstructor;
import org.bring.freshmarkt.sync.client.dto.ProductDTO;
import org.bring.freshmarkt.sync.domain.Product;
import org.bring.freshmarkt.sync.domain.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FreshMarktProductPersistenceService {

    private final ProductRepository productRepository;
    private final FreshMarktSyncProductMapper mapper;

    @Transactional
    public void processPage(List<ProductDTO> products) {
        List<Product> mapped = products.stream()
                .map(mapper::toProduct)
                .toList();
        productRepository.saveAll(mapped);
    }
}
