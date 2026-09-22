package com.northwind.shop.controller;

import com.northwind.shop.dto.PagedResponse;
import com.northwind.shop.dto.ProductResponse;
import com.northwind.shop.service.ProductService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public PagedResponse<ProductResponse> list(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false, name = "q") String term,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sort) {

        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), Sort.by(sort).ascending());
        return productService.listProducts(categoryId, term, pageable);
    }

    @GetMapping("/{productId}")
    public ProductResponse get(@PathVariable Long productId) {
        return productService.getProduct(productId);
    }
}
