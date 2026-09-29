package com.lumen.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VariantResponse implements Serializable {

    private Long id;
    private String variantSku;
    private String label;
    private BigDecimal priceDelta;
    private BigDecimal effectivePrice;
}
