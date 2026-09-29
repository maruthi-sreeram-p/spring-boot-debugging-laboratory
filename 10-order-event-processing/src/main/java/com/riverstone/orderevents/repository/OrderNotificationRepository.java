package com.riverstone.orderevents.repository;

import com.riverstone.orderevents.entity.OrderNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderNotificationRepository extends JpaRepository<OrderNotification, Long> {

    List<OrderNotification> findByOrderRefOrderByCreatedAtAsc(String orderRef);
}
