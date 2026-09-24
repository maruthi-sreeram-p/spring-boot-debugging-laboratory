package com.vantage.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductStockResponse {

    private Long productId;
    private String sku;
    private String productName;
    private Integer totalOnHand;
    private Integer totalAvailable;
    private List<StockLevelResponse> warehouses;
}
