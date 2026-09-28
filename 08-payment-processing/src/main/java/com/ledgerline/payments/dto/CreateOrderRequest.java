package com.ledgerline.payments.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreateOrderRequest {

    @NotBlank
    @Size(max = 24)
    private String merchantCode;

    @NotBlank
    @Size(max = 64)
    private String customerRef;

    @NotNull
    @DecimalMin(value = "0.01")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal amount;

    @Pattern(regexp = "[A-Z]{3}")
    private String currency;
}
