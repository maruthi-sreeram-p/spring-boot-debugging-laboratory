package com.lumen.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreateProductRequest {

    @NotBlank
    @Size(max = 40)
    private String sku;

    @NotBlank
    @Size(max = 180)
    private String name;

    @Size(max = 1200)
    private String description;

    @NotBlank
    @Size(max = 80)
    private String brand;

    @NotNull
    private Long categoryId;

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal price;
}
