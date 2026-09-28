package com.ledgerline.payments.repository;

import com.ledgerline.payments.entity.OrderStatus;
import com.ledgerline.payments.entity.PaymentOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    Optional<PaymentOrder> findByOrderRef(String orderRef);

    List<PaymentOrder> findByStatusOrderByCreatedAtAsc(OrderStatus status);

    boolean existsByOrderRef(String orderRef);
}
