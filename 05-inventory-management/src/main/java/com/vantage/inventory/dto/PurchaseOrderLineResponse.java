package com.vantage.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderLineResponse {

    private Long lineId;
    private Long productId;
    private String sku;
    private Integer quantityOrdered;
    private Integer quantityReceived;
    private BigDecimal unitCost;
}
