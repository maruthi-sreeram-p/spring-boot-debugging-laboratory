package com.vantage.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StockMovementResponse {

    private Long id;
    private String reference;
    private Long productId;
    private String sku;
    private Long warehouseId;
    private String warehouseCode;
    private String movementType;
    private Integer quantity;
    private String reason;
    private Instant createdAt;
}
