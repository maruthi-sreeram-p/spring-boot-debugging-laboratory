package com.vantage.inventory.controller;

import com.vantage.inventory.dto.PurchaseOrderResponse;
import com.vantage.inventory.dto.ReceiveGoodsRequest;
import com.vantage.inventory.service.ReceivingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {

    private final ReceivingService receivingService;

    public PurchaseOrderController(ReceivingService receivingService) {
        this.receivingService = receivingService;
    }

    @GetMapping
    public List<PurchaseOrderResponse> open() {
        return receivingService.openPurchaseOrders();
    }

    @GetMapping("/{purchaseOrderId}")
    public PurchaseOrderResponse get(@PathVariable Long purchaseOrderId) {
        return receivingService.getPurchaseOrder(purchaseOrderId);
    }

    @PostMapping("/{purchaseOrderId}/receipts")
    public PurchaseOrderResponse receive(@PathVariable Long purchaseOrderId,
                                         @Valid @RequestBody ReceiveGoodsRequest request) {
        return receivingService.receive(purchaseOrderId, request);
    }
}
