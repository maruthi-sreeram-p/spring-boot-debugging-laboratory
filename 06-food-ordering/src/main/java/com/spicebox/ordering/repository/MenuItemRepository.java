package com.spicebox.ordering.repository;

import com.spicebox.ordering.entity.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    List<MenuItem> findByRestaurantIdAndAvailableTrueOrderByCategoryAscNameAsc(Long restaurantId);

    List<MenuItem> findByIdIn(Iterable<Long> ids);
}
