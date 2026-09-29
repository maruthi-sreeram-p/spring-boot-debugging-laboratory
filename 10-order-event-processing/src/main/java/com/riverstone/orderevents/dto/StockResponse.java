package com.riverstone.orderevents.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StockResponse {

    private String sku;
    private String name;
    private Integer available;
    private Integer reserved;
}
