package com.lumen.catalog.controller;

import com.lumen.catalog.dto.BrowseFilter;
import com.lumen.catalog.dto.CategoryResponse;
import com.lumen.catalog.dto.ProductResponse;
import com.lumen.catalog.dto.VariantResponse;
import com.lumen.catalog.exception.ResourceNotFoundException;
import com.lumen.catalog.service.CatalogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return catalogService.categories();
    }

    @GetMapping("/products/{productId}")
    public ProductResponse product(@PathVariable Long productId) {
        return catalogService.getProduct(productId);
    }

    @GetMapping("/products/sku/{sku}")
    public ProductResponse productBySku(@PathVariable String sku) {
        ProductResponse response = catalogService.getProductBySku(sku);
        if (response == null) {
            throw new ResourceNotFoundException("Product", sku);
        }
        return response;
    }

    @GetMapping("/products/{productId}/variants")
    public List<VariantResponse> variants(@PathVariable Long productId) {
        return catalogService.variantsOf(productId);
    }

    @GetMapping("/categories/{categoryId}/products")
    public List<ProductResponse> categoryListing(@PathVariable Long categoryId) {
        return catalogService.productsInCategory(categoryId);
    }

    @GetMapping("/search")
    public Page<ProductResponse> search(@RequestParam(required = false) String term,
                                        @RequestParam(required = false) String brand,
                                        @RequestParam(required = false) Long categoryId,
                                        @RequestParam(required = false) BigDecimal minPrice,
                                        @RequestParam(required = false) BigDecimal maxPrice,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return catalogService.search(term, brand, categoryId, minPrice, maxPrice, pageable);
    }

    @GetMapping("/browse")
    public List<ProductResponse> browse(@RequestParam(required = false) String brand,
                                        @RequestParam(required = false) Long categoryId) {
        return catalogService.browse(new BrowseFilter(brand, categoryId));
    }
}
