package com.vantage.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderResponse {

    private Long id;
    private String poNumber;
    private String supplierName;
    private Long warehouseId;
    private String warehouseCode;
    private String status;
    private LocalDate expectedDate;
    private List<PurchaseOrderLineResponse> lines;
}
