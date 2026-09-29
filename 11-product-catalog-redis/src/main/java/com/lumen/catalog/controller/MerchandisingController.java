package com.lumen.catalog.controller;

import com.lumen.catalog.dto.CreateProductRequest;
import com.lumen.catalog.dto.PriceHistoryResponse;
import com.lumen.catalog.dto.ProductResponse;
import com.lumen.catalog.dto.UpdateProductRequest;
import com.lumen.catalog.security.CatalogUser;
import com.lumen.catalog.service.MerchandisingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/merch")
public class MerchandisingController {

    private final MerchandisingService merchandisingService;

    public MerchandisingController(MerchandisingService merchandisingService) {
        this.merchandisingService = merchandisingService;
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@AuthenticationPrincipal CatalogUser principal,
                                  @Valid @RequestBody CreateProductRequest request) {
        return merchandisingService.createProduct(request, principal.getUsername());
    }

    @PutMapping("/products/{productId}")
    public ProductResponse update(@AuthenticationPrincipal CatalogUser principal,
                                  @PathVariable Long productId,
                                  @Valid @RequestBody UpdateProductRequest request) {
        return merchandisingService.updateProduct(productId, request, principal.getUsername());
    }

    @GetMapping("/products/{productId}/price-history")
    public List<PriceHistoryResponse> priceHistory(@PathVariable Long productId) {
        return merchandisingService.priceHistory(productId);
    }
}
