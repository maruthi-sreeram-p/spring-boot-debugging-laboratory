package com.northwind.shop.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CartLineResponse {

    private Long productId;
    private String sku;
    private String name;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal lineTotal;
    private boolean available;
}
