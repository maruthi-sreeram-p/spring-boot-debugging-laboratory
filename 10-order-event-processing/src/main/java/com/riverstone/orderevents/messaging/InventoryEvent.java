package com.riverstone.orderevents.messaging;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InventoryEvent {

    private String orderRef;
    private String sku;
    private Integer quantity;
    private String outcome;
    private String detail;
    private Instant occurredAt;
}
