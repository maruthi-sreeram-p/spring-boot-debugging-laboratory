package com.ledgerline.payments.repository;

import com.ledgerline.payments.entity.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {

    List<PaymentAttempt> findByPaymentIdOrderByAttemptNoAsc(Long paymentId);
}
