package com.meridian.banking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class AmountRequest {

    @NotNull
    @DecimalMin(value = "0.01", message = "must be at least 0.01")
    @Digits(integer = 16, fraction = 2)
    private BigDecimal amount;

    @Size(max = 200)
    private String description;
}
