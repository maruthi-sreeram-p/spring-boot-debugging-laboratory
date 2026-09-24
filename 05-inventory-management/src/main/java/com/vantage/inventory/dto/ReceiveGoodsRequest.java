package com.vantage.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ReceiveGoodsRequest {

    @NotEmpty
    @Valid
    private List<ReceiveLineRequest> lines;

    @Size(max = 200)
    private String note;
}
