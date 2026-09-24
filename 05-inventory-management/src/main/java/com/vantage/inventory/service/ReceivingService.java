package com.vantage.inventory.service;

import com.vantage.inventory.cache.StockCache;
import com.vantage.inventory.dto.PurchaseOrderResponse;
import com.vantage.inventory.dto.ReceiveGoodsRequest;
import com.vantage.inventory.dto.ReceiveLineRequest;
import com.vantage.inventory.entity.MovementType;
import com.vantage.inventory.entity.PurchaseOrder;
import com.vantage.inventory.entity.PurchaseOrderLine;
import com.vantage.inventory.entity.PurchaseOrderStatus;
import com.vantage.inventory.entity.StockLevel;
import com.vantage.inventory.entity.Warehouse;
import com.vantage.inventory.exception.ReceiptRuleException;
import com.vantage.inventory.exception.ResourceNotFoundException;
import com.vantage.inventory.mapper.InventoryMapper;
import com.vantage.inventory.repository.PurchaseOrderRepository;
import com.vantage.inventory.repository.StockLevelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Goods receipt. A delivery arrives against a purchase order, the warehouse counts what is
 * in the pallet and posts it; stock goes up, a movement is written for the audit trail and
 * the cached figure is refreshed so the picking screens see the new number immediately.
 */
@Service
public class ReceivingService {

    private static final Logger log = LoggerFactory.getLogger(ReceivingService.class);

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final StockLevelRepository stockLevelRepository;
    private final StockService stockService;
    private final InventoryMapper inventoryMapper;
    private final StockCache stockCache;

    public ReceivingService(PurchaseOrderRepository purchaseOrderRepository,
                            StockLevelRepository stockLevelRepository,
                            StockService stockService,
                            InventoryMapper inventoryMapper,
                            StockCache stockCache) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.stockLevelRepository = stockLevelRepository;
        this.stockService = stockService;
        this.inventoryMapper = inventoryMapper;
        this.stockCache = stockCache;
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse getPurchaseOrder(Long purchaseOrderId) {
        PurchaseOrder order = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase order", purchaseOrderId));
        return inventoryMapper.toResponse(order);
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderResponse> openPurchaseOrders() {
        return purchaseOrderRepository.findByStatusOrderByExpectedDateAsc(PurchaseOrderStatus.OPEN)
                .stream()
                .map(inventoryMapper::toResponse)
                .toList();
    }

    @Transactional
    public PurchaseOrderResponse receive(Long purchaseOrderId, ReceiveGoodsRequest request) {
        PurchaseOrder order = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase order", purchaseOrderId));
        Warehouse warehouse = order.getWarehouse();

        for (ReceiveLineRequest received : request.getLines()) {
            PurchaseOrderLine line = order.getLines().stream()
                    .filter(candidate -> candidate.getProduct().getId().equals(received.getProductId()))
                    .findFirst()
                    .orElseThrow(() -> new ReceiptRuleException("Product " + received.getProductId()
                            + " is not on " + order.getPoNumber()));

            if (received.getQuantity() > line.getQuantityOrdered()) {
                throw new ReceiptRuleException("Cannot receive " + received.getQuantity() + " of "
                        + line.getProduct().getSku() + " against " + order.getPoNumber()
                        + "; only " + line.getQuantityOrdered() + " were ordered");
            }

            line.setQuantityReceived(line.getQuantityReceived() + received.getQuantity());

            StockLevel level = stockLevelRepository
                    .findByProductIdAndWarehouseId(line.getProduct().getId(), warehouse.getId())
                    .orElseGet(() -> openStockLevel(line, warehouse));

            level.setQuantityOnHand(level.getQuantityOnHand() + received.getQuantity());
            level.setUpdatedAt(Instant.now());

            stockService.recordMovement(line.getProduct(), warehouse, MovementType.RECEIPT,
                    received.getQuantity(), order.getPoNumber() + " goods receipt", "GRN");

            stockCache.write(line.getProduct().getId(), warehouse.getId(),
                    level.getQuantityOnHand(), level.getQuantityReserved());

            log.info("Received {} x {} into {} against {}", received.getQuantity(),
                    line.getProduct().getSku(), warehouse.getCode(), order.getPoNumber());
        }

        order.setStatus(deriveStatus(order));
        return inventoryMapper.toResponse(order);
    }

    private StockLevel openStockLevel(PurchaseOrderLine line, Warehouse warehouse) {
        StockLevel level = new StockLevel();
        level.setProduct(line.getProduct());
        level.setWarehouse(warehouse);
        level.setQuantityOnHand(0);
        level.setQuantityReserved(0);
        level.setUpdatedAt(Instant.now());
        return stockLevelRepository.save(level);
    }

    private PurchaseOrderStatus deriveStatus(PurchaseOrder order) {
        boolean anythingReceived = order.getLines().stream().anyMatch(line -> line.getQuantityReceived() > 0);
        boolean everythingReceived = order.getLines().stream()
                .allMatch(line -> line.getQuantityReceived() >= line.getQuantityOrdered());
        if (everythingReceived) {
            return PurchaseOrderStatus.RECEIVED;
        }
        return anythingReceived ? PurchaseOrderStatus.PARTIALLY_RECEIVED : order.getStatus();
    }
}
