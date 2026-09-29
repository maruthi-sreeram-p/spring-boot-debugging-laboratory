package com.lumen.catalog.service;

import com.lumen.catalog.dto.BrowseFilter;
import com.lumen.catalog.dto.CategoryResponse;
import com.lumen.catalog.dto.ProductResponse;
import com.lumen.catalog.dto.VariantResponse;
import com.lumen.catalog.entity.Product;
import com.lumen.catalog.exception.ResourceNotFoundException;
import com.lumen.catalog.mapper.CatalogMapper;
import com.lumen.catalog.repository.CategoryRepository;
import com.lumen.catalog.repository.ProductRepository;
import com.lumen.catalog.repository.ProductVariantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Every read path the storefront uses. All of them are cached, because the catalogue is
 * read constantly and changes a handful of times a day.
 */
@Service
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final CategoryRepository categoryRepository;
    private final CatalogMapper catalogMapper;

    public CatalogService(ProductRepository productRepository,
                          ProductVariantRepository variantRepository,
                          CategoryRepository categoryRepository,
                          CatalogMapper catalogMapper) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.categoryRepository = categoryRepository;
        this.catalogMapper = catalogMapper;
    }

    @Cacheable(cacheNames = "products", key = "#productId")
    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long productId) {
        log.debug("Loading product {} from the database", productId);
        Product product = productRepository.findById(productId)
                .filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        return catalogMapper.toResponse(product);
    }

    /**
     * Used by the storefront deep links, which address products by SKU rather than id.
     */
    @Cacheable(cacheNames = "productsBySku", key = "#sku")
    @Transactional(readOnly = true)
    public ProductResponse getProductBySku(String sku) {
        log.debug("Loading product {} from the database", sku);
        return productRepository.findBySku(sku)
                .filter(Product::isActive)
                .map(catalogMapper::toResponse)
                .orElse(null);
    }

    @Cacheable(cacheNames = "categoryListing", key = "#categoryId")
    @Transactional(readOnly = true)
    public List<ProductResponse> productsInCategory(Long categoryId) {
        log.debug("Loading category {} listing from the database", categoryId);
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("Category", categoryId);
        }
        return catalogMapper.toResponses(
                productRepository.findByCategoryIdAndActiveTrueOrderByNameAsc(categoryId));
    }

    @Cacheable(cacheNames = "productSearch",
            key = "#term + '|' + #brand + '|' + #categoryId + '|' + #pageable.pageNumber + '|' + #pageable.pageSize")
    @Transactional(readOnly = true)
    public Page<ProductResponse> search(String term, String brand, Long categoryId,
                                        BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {
        log.debug("Running catalogue search from the database");
        Page<Product> page = productRepository.search(term, brand, categoryId, minPrice, maxPrice, pageable);
        return page.map(catalogMapper::toResponse);
    }

    /**
     * Backs the browse rails on the home page, which are driven by a saved filter.
     */
    @Cacheable(cacheNames = "productBrowse", key = "#filter")
    @Transactional(readOnly = true)
    public List<ProductResponse> browse(BrowseFilter filter) {
        log.debug("Running browse from the database for {} / {}", filter.getBrand(), filter.getCategoryId());
        return catalogMapper.toResponses(
                productRepository.browse(filter.getBrand(), filter.getCategoryId()));
    }

    /**
     * The variant picker on a product page. The effective price of a variant is the product
     * price plus the variant delta, so the product has to be read as well.
     */
    @Cacheable(cacheNames = "variants", key = "#productId")
    @Transactional(readOnly = true)
    public List<VariantResponse> variantsOf(Long productId) {
        log.debug("Loading variants for product {} from the database", productId);
        ProductResponse product = getProduct(productId);
        return catalogMapper.toVariantResponses(
                variantRepository.findByProductIdAndActiveTrueOrderByLabelAsc(productId),
                product.getPrice());
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> categories() {
        return catalogMapper.toCategoryResponses(categoryRepository.findByActiveTrueOrderByPositionAsc());
    }
}
