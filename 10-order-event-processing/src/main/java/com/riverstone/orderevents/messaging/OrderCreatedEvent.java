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
public class OrderCreatedEvent {

    private String orderRef;
    private String customerRef;
    private String sku;
    private Integer quantity;
    private Instant occurredAt;
}
