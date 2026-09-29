package com.riverstone.orderevents.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateOrderRequest {

    @NotBlank
    @Size(max = 64)
    private String customerRef;

    @NotBlank
    @Size(max = 32)
    private String sku;

    @NotNull
    @Min(1)
    @Max(500)
    private Integer quantity;
}
