package com.spicebox.ordering.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantResponse {

    private Long id;
    private String name;
    private String cuisine;
    private String city;
    private Integer prepMinutes;
    private boolean active;
}
