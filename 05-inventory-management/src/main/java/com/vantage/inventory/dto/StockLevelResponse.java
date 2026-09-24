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
public class StockLevelResponse {

    private Long productId;
    private String sku;
    private String productName;
    private Long warehouseId;
    private String warehouseCode;
    private Integer quantityOnHand;
    private Integer quantityReserved;
    private Integer quantityAvailable;
    private boolean belowReorderLevel;
    private Instant updatedAt;
}
