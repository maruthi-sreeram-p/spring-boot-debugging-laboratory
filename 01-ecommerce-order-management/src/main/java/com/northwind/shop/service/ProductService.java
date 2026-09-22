package com.northwind.shop.service;

import com.northwind.shop.dto.PagedResponse;
import com.northwind.shop.dto.ProductResponse;
import com.northwind.shop.dto.UpdateProductRequest;
import com.northwind.shop.entity.Product;
import com.northwind.shop.exception.ResourceNotFoundException;
import com.northwind.shop.mapper.ProductMapper;
import com.northwind.shop.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    public ProductService(ProductRepository productRepository, ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.productMapper = productMapper;
    }

    @Transactional(readOnly = true)
    public PagedResponse<ProductResponse> listProducts(Long categoryId, String term, Pageable pageable) {
        String normalizedTerm = (term == null || term.isBlank()) ? null : term.trim();
        Page<Product> page = productRepository.search(categoryId, normalizedTerm, pageable);
        return PagedResponse.of(page, productMapper.toResponses(page.getContent()));
    }

    @Cacheable(cacheNames = "products", key = "#productId")
    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        return productMapper.toResponse(product);
    }

    @CacheEvict(cacheNames = "products", key = "#request.sku")
    @Transactional
    public ProductResponse updateProduct(Long productId, UpdateProductRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));

        product.setSku(request.getSku());
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setActive(request.isActive());

        Product saved = productRepository.save(product);
        log.info("Product {} updated by back office, price is now {}", saved.getSku(), saved.getPrice());
        return productMapper.toResponse(saved);
    }
}
