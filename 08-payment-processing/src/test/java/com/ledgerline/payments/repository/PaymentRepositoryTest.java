package com.ledgerline.payments.repository;

import com.ledgerline.payments.entity.Merchant;
import com.ledgerline.payments.entity.Payment;
import com.ledgerline.payments.entity.PaymentOrder;
import com.ledgerline.payments.entity.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PaymentRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void findsStaleProcessingPayments() {
        Merchant merchant = new Merchant();
        merchant.setCode("MER-TEST");
        merchant.setName("Test Merchant");
        entityManager.persist(merchant);

        PaymentOrder order = new PaymentOrder();
        order.setOrderRef("ORD-T-1");
        order.setMerchant(merchant);
        order.setCustomerRef("CUST-T");
        order.setAmount(new BigDecimal("100.00"));
        entityManager.persist(order);

        Payment fresh = persistPayment(order, "PAY-T-1", "key-1", PaymentStatus.PROCESSING, Instant.now());
        Payment stale = persistPayment(order, "PAY-T-2", "key-2", PaymentStatus.PROCESSING,
                Instant.now().minus(30, ChronoUnit.MINUTES));
        persistPayment(order, "PAY-T-3", "key-3", PaymentStatus.SUCCEEDED,
                Instant.now().minus(30, ChronoUnit.MINUTES));
        entityManager.flush();

        List<Payment> found = paymentRepository.findByStatusAndUpdatedAtBefore(
                PaymentStatus.PROCESSING, Instant.now().minus(5, ChronoUnit.MINUTES));

        assertThat(found).extracting(Payment::getPaymentRef).containsExactly(stale.getPaymentRef());
        assertThat(fresh.getPaymentRef()).isEqualTo("PAY-T-1");
    }

    private Payment persistPayment(PaymentOrder order, String ref, String key,
                                   PaymentStatus status, Instant updatedAt) {
        Payment payment = new Payment();
        payment.setPaymentRef(ref);
        payment.setOrder(order);
        payment.setIdempotencyKey(key);
        payment.setAmount(new BigDecimal("100.00"));
        payment.setInstrument("CARD_VISA");
        payment.setStatus(status);
        payment.setAttempts(1);
        payment.setUpdatedAt(updatedAt);
        return entityManager.persist(payment);
    }
}
