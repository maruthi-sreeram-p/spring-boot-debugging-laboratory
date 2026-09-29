package com.riverstone.orderevents.repository;

import com.riverstone.orderevents.entity.OrderRecord;
import com.riverstone.orderevents.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRecordRepository extends JpaRepository<OrderRecord, Long> {

    Optional<OrderRecord> findByOrderRef(String orderRef);

    Page<OrderRecord> findByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);

    boolean existsByOrderRef(String orderRef);

    long countByStatus(OrderStatus status);
}
