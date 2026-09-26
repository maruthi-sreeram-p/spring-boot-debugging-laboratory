package com.spicebox.ordering.repository;

import com.spicebox.ordering.entity.FoodOrder;
import com.spicebox.ordering.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FoodOrderRepository extends JpaRepository<FoodOrder, Long> {

    Page<FoodOrder> findByCustomerIdOrderByPlacedAtDesc(Long customerId, Pageable pageable);

    List<FoodOrder> findByStatusOrderByPlacedAtAsc(OrderStatus status);

    List<FoodOrder> findByRestaurantIdAndStatusInOrderByPlacedAtAsc(Long restaurantId, List<OrderStatus> statuses);

    boolean existsByOrderCode(String orderCode);
}
