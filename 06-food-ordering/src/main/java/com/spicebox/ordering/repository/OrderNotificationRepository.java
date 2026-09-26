package com.spicebox.ordering.repository;

import com.spicebox.ordering.entity.OrderNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderNotificationRepository extends JpaRepository<OrderNotification, Long> {

    List<OrderNotification> findByOrderIdOrderBySentAtAsc(Long orderId);
}
