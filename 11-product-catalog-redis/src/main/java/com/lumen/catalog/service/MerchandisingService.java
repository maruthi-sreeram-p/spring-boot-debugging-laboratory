package com.lumen.catalog.service;

import com.lumen.catalog.dto.CreateProductRequest;
import com.lumen.catalog.dto.PriceHistoryResponse;
import com.lumen.catalog.dto.ProductResponse;
import com.lumen.catalog.dto.UpdateProductRequest;
import com.lumen.catalog.entity.Category;
import com.lumen.catalog.entity.PriceHistoryEntry;
import com.lumen.catalog.entity.Product;
import com.lumen.catalog.exception.DuplicateSkuException;
import com.lumen.catalog.exception.ResourceNotFoundException;
import com.lumen.catalog.mapper.CatalogMapper;
import com.lumen.catalog.repository.CategoryRepository;
import com.lumen.catalog.repository.PriceHistoryRepository;
import com.lumen.catalog.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * The back office. Merchandising edit products a few times a day; the storefront reads them
 * millions of times, so every write has to leave the caches in a state the storefront can
 * trust.
 */
@Service
public class MerchandisingService {

    private static final Logger log = LoggerFactory.getLogger(MerchandisingService.class);

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final CatalogMapper catalogMapper;

    public MerchandisingService(ProductRepository productRepository,
                                CategoryRepository categoryRepository,
                                PriceHistoryRepository priceHistoryRepository,
                                CatalogMapper catalogMapper) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.catalogMapper = catalogMapper;
    }

    @CacheEvict(cacheNames = {"products", "productsBySku"}, key = "#productId")
    @Transactional
    public ProductResponse updateProduct(Long productId, UpdateProductRequest request, String editor) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", request.getCategoryId()));

        BigDecimal previousPrice = product.getPrice();

        product.setName(request.getName().trim());
        product.setDescription(request.getDescription());
        product.setBrand(request.getBrand().trim());
        product.setCategory(category);
        product.setPrice(request.getPrice());
        product.setActive(request.isActive());

        if (previousPrice.compareTo(request.getPrice()) != 0) {
            PriceHistoryEntry entry = new PriceHistoryEntry();
            entry.setProductId(product.getId());
            entry.setOldPrice(previousPrice);
            entry.setNewPrice(request.getPrice());
            entry.setChangedBy(editor);
            priceHistoryRepository.save(entry);
        }

        log.info("Product {} updated by {}, price is now {}", product.getSku(), editor, product.getPrice());
        return catalogMapper.toResponse(product);
    }

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request, String editor) {
        if (productRepository.existsBySku(request.getSku())) {
            throw new DuplicateSkuException(request.getSku());
        }
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", request.getCategoryId()));

        Product product = new Product();
        product.setSku(request.getSku().trim());
        product.setName(request.getName().trim());
        product.setDescription(request.getDescription());
        product.setBrand(request.getBrand().trim());
        product.setCategory(category);
        product.setPrice(request.getPrice());
        product.setActive(true);

        Product saved = productRepository.save(product);
        log.info("Product {} created by {}", saved.getSku(), editor);
        return catalogMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<PriceHistoryResponse> priceHistory(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product", productId);
        }
        return catalogMapper.toPriceHistoryResponses(
                priceHistoryRepository.findByProductIdOrderByChangedAtDesc(productId));
    }
}
