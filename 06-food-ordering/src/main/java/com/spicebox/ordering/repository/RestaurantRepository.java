package com.spicebox.ordering.repository;

import com.spicebox.ordering.entity.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    List<Restaurant> findByActiveTrueOrderByNameAsc();
}
