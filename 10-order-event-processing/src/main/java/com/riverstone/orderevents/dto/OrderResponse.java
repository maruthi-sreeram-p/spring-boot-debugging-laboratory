package com.riverstone.orderevents.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private Long id;
    private String orderRef;
    private String customerRef;
    private String sku;
    private Integer quantity;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
}
