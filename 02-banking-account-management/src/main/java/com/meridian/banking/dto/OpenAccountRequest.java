package com.meridian.banking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class OpenAccountRequest {

    @NotBlank
    @Pattern(regexp = "CHECKING|SAVINGS")
    private String accountType;

    @NotBlank
    @Pattern(regexp = "[A-Z]{3}")
    private String currency;

    @NotNull
    @PositiveOrZero
    private BigDecimal openingBalance;
}
