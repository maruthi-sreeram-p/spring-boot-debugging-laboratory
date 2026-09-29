package com.lumen.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PriceHistoryResponse implements Serializable {

    private Long id;
    private BigDecimal oldPrice;
    private BigDecimal newPrice;
    private String changedBy;
    private Instant changedAt;
}
