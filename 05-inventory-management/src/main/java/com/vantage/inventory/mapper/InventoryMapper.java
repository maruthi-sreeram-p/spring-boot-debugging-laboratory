package com.vantage.inventory.mapper;

import com.vantage.inventory.dto.ProductStockResponse;
import com.vantage.inventory.dto.PurchaseOrderLineResponse;
import com.vantage.inventory.dto.PurchaseOrderResponse;
import com.vantage.inventory.dto.StockLevelResponse;
import com.vantage.inventory.dto.StockMovementResponse;
import com.vantage.inventory.entity.Product;
import com.vantage.inventory.entity.PurchaseOrder;
import com.vantage.inventory.entity.PurchaseOrderLine;
import com.vantage.inventory.entity.StockLevel;
import com.vantage.inventory.entity.StockMovement;
import com.vantage.inventory.entity.Warehouse;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class InventoryMapper {

    public StockLevelResponse toResponse(StockLevel level) {
        return build(level.getProduct(), level.getWarehouse(),
                level.getQuantityOnHand(), level.getQuantityReserved(), level.getUpdatedAt());
    }

    public StockLevelResponse toResponse(Product product, Warehouse warehouse,
                                         int onHand, int reserved, Instant updatedAt) {
        return build(product, warehouse, onHand, reserved, updatedAt);
    }

    public List<StockLevelResponse> toStockResponses(List<StockLevel> levels) {
        return levels.stream().map(this::toResponse).toList();
    }

    public ProductStockResponse toProductStock(Product product, List<StockLevelResponse> perWarehouse) {
        int totalOnHand = perWarehouse.stream().mapToInt(StockLevelResponse::getQuantityOnHand).sum();
        int totalAvailable = perWarehouse.stream().mapToInt(StockLevelResponse::getQuantityAvailable).sum();
        return new ProductStockResponse(
                product.getId(), product.getSku(), product.getName(),
                totalOnHand, totalAvailable, perWarehouse);
    }

    public StockMovementResponse toResponse(StockMovement movement) {
        return new StockMovementResponse(
                movement.getId(),
                movement.getReference(),
                movement.getProduct().getId(),
                movement.getProduct().getSku(),
                movement.getWarehouse().getId(),
                movement.getWarehouse().getCode(),
                movement.getMovementType().name(),
                movement.getQuantity(),
                movement.getReason(),
                movement.getCreatedAt());
    }

    public List<StockMovementResponse> toMovementResponses(List<StockMovement> movements) {
        return movements.stream().map(this::toResponse).toList();
    }

    public PurchaseOrderResponse toResponse(PurchaseOrder order) {
        List<PurchaseOrderLineResponse> lines = order.getLines().stream()
                .map(this::toResponse)
                .toList();
        return new PurchaseOrderResponse(
                order.getId(),
                order.getPoNumber(),
                order.getSupplier().getName(),
                order.getWarehouse().getId(),
                order.getWarehouse().getCode(),
                order.getStatus().name(),
                order.getExpectedDate(),
                lines);
    }

    private PurchaseOrderLineResponse toResponse(PurchaseOrderLine line) {
        return new PurchaseOrderLineResponse(
                line.getId(),
                line.getProduct().getId(),
                line.getProduct().getSku(),
                line.getQuantityOrdered(),
                line.getQuantityReceived(),
                line.getUnitCost());
    }

    private StockLevelResponse build(Product product, Warehouse warehouse,
                                     int onHand, int reserved, Instant updatedAt) {
        StockLevelResponse response = new StockLevelResponse();
        response.setProductId(product.getId());
        response.setSku(product.getSku());
        response.setProductName(product.getName());
        response.setWarehouseId(warehouse.getId());
        response.setWarehouseCode(warehouse.getCode());
        response.setQuantityOnHand(onHand);
        response.setQuantityReserved(reserved);
        response.setQuantityAvailable(onHand);
        response.setBelowReorderLevel(onHand < product.getReorderLevel());
        response.setUpdatedAt(updatedAt);
        return response;
    }
}
