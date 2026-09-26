package com.spicebox.ordering.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private Long id;
    private String orderCode;
    private Long restaurantId;
    private String restaurantName;
    private String status;
    private BigDecimal subtotal;
    private BigDecimal deliveryFee;
    private BigDecimal totalAmount;
    private String deliveryAddress;
    private Instant placedAt;
    private Instant updatedAt;
    private List<OrderItemResponse> items;
}
