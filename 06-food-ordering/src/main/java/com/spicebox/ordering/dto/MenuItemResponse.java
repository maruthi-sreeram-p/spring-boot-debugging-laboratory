package com.spicebox.ordering.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MenuItemResponse {

    private Long id;
    private Long restaurantId;
    private String name;
    private String description;
    private String category;
    private BigDecimal price;
    private boolean available;
}
