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
public class ProductResponse implements Serializable {

    private Long id;
    private String sku;
    private String name;
    private String description;
    private String brand;
    private Long categoryId;
    private String categoryName;
    private BigDecimal price;
    private String currency;
    private boolean active;
    private Instant updatedAt;
}
