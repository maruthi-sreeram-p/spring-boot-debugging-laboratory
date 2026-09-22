package com.northwind.shop.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class UpdateProductRequest {

    @NotBlank
    @Size(max = 40)
    private String sku;

    @NotBlank
    @Size(max = 160)
    private String name;

    @Size(max = 1000)
    private String description;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = true)
    private BigDecimal price;

    @NotNull
    @PositiveOrZero
    private Integer stockQuantity;

    private boolean active = true;
}
