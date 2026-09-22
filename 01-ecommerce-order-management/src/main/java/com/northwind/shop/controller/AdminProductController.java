package com.northwind.shop.controller;

import com.northwind.shop.dto.ProductResponse;
import com.northwind.shop.dto.UpdateProductRequest;
import com.northwind.shop.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {

    private final ProductService productService;

    public AdminProductController(ProductService productService) {
        this.productService = productService;
    }

    @PutMapping("/{productId}")
    public ProductResponse update(@PathVariable Long productId,
                                  @Valid @RequestBody UpdateProductRequest request) {
        return productService.updateProduct(productId, request);
    }
}
