package com.ledgerline.payments.repository;

import com.ledgerline.payments.entity.Payment;
import com.ledgerline.payments.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentRef(String paymentRef);

    Optional<Payment> findByIdempotencyKeyAndStatus(String idempotencyKey, PaymentStatus status);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findByOrderIdOrderByCreatedAtAsc(Long orderId);

    List<Payment> findByStatusAndUpdatedAtBefore(PaymentStatus status, Instant cutoff);

    boolean existsByPaymentRef(String paymentRef);
}
