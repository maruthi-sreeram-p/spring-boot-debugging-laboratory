package com.athenaeum.lending.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReturnRequest {

    @NotBlank
    private String barcode;
}
