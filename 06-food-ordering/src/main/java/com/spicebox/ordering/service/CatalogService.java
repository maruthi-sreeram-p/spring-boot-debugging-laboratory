package com.spicebox.ordering.service;

import com.spicebox.ordering.dto.MenuItemResponse;
import com.spicebox.ordering.dto.RestaurantResponse;
import com.spicebox.ordering.exception.ResourceNotFoundException;
import com.spicebox.ordering.mapper.OrderingMapper;
import com.spicebox.ordering.repository.MenuItemRepository;
import com.spicebox.ordering.repository.RestaurantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CatalogService {

    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;
    private final OrderingMapper orderingMapper;

    public CatalogService(RestaurantRepository restaurantRepository,
                          MenuItemRepository menuItemRepository,
                          OrderingMapper orderingMapper) {
        this.restaurantRepository = restaurantRepository;
        this.menuItemRepository = menuItemRepository;
        this.orderingMapper = orderingMapper;
    }

    @Transactional(readOnly = true)
    public List<RestaurantResponse> restaurants() {
        return orderingMapper.toRestaurantResponses(restaurantRepository.findByActiveTrueOrderByNameAsc());
    }

    @Transactional(readOnly = true)
    public List<MenuItemResponse> menu(Long restaurantId) {
        if (!restaurantRepository.existsById(restaurantId)) {
            throw new ResourceNotFoundException("Restaurant", restaurantId);
        }
        return orderingMapper.toMenuResponses(
                menuItemRepository.findByRestaurantIdAndAvailableTrueOrderByCategoryAscNameAsc(restaurantId));
    }
}
