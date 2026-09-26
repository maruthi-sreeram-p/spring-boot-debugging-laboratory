package com.spicebox.ordering.controller;

import com.spicebox.ordering.dto.MenuItemResponse;
import com.spicebox.ordering.dto.RestaurantResponse;
import com.spicebox.ordering.service.CatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/restaurants")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public List<RestaurantResponse> restaurants() {
        return catalogService.restaurants();
    }

    @GetMapping("/{restaurantId}/menu")
    public List<MenuItemResponse> menu(@PathVariable Long restaurantId) {
        return catalogService.menu(restaurantId);
    }
}
