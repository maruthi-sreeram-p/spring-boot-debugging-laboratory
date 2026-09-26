package com.spicebox.ordering.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PlaceOrderRequest {

    @NotEmpty
    @Valid
    private List<PlaceOrderLineRequest> items;

    @Size(max = 300)
    private String deliveryAddress;
}
