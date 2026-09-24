package com.vantage.inventory.controller;

import com.vantage.inventory.dto.AdjustStockRequest;
import com.vantage.inventory.dto.PagedResponse;
import com.vantage.inventory.dto.ProductStockResponse;
import com.vantage.inventory.dto.StockLevelResponse;
import com.vantage.inventory.dto.StockMovementResponse;
import com.vantage.inventory.service.StockService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stock")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping("/products/{productId}")
    public ProductStockResponse acrossNetwork(@PathVariable Long productId) {
        return stockService.stockAcrossNetwork(productId);
    }

    @GetMapping("/products/{productId}/warehouses/{warehouseId}")
    public StockLevelResponse atWarehouse(@PathVariable Long productId, @PathVariable Long warehouseId) {
        return stockService.stockAt(productId, warehouseId);
    }

    @GetMapping("/warehouses/{warehouseId}")
    public List<StockLevelResponse> inWarehouse(@PathVariable Long warehouseId) {
        return stockService.stockInWarehouse(warehouseId);
    }

    @GetMapping("/movements")
    public PagedResponse<StockMovementResponse> movements(@RequestParam Long productId,
                                                          @RequestParam(required = false) Long warehouseId,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return stockService.movements(productId, warehouseId, pageable);
    }

    @PostMapping("/adjustments")
    @PreAuthorize("hasRole('CONTROLLER')")
    public StockLevelResponse adjust(@Valid @RequestBody AdjustStockRequest request) {
        return stockService.adjust(request);
    }
}
