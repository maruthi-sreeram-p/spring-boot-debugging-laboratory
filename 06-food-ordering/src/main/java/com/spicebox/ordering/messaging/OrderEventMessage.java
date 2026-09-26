package com.spicebox.ordering.messaging;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderEventMessage {

    private Long orderId;
    private String orderCode;
    private Long restaurantId;
    private String restaurantName;
    private String deliveryAddress;
    private BigDecimal totalAmount;
    private String status;
    private Instant occurredAt;
}
