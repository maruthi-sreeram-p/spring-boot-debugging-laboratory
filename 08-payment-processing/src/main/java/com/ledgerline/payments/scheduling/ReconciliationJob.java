package com.ledgerline.payments.scheduling;

import com.ledgerline.payments.config.PaymentProperties;
import com.ledgerline.payments.entity.OrderStatus;
import com.ledgerline.payments.entity.Payment;
import com.ledgerline.payments.entity.PaymentStatus;
import com.ledgerline.payments.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * A payment left in PROCESSING means we never learned the outcome — usually because the
 * acquirer accepted the request and the response was lost on the way back. This job closes
 * those off so that merchants are not left staring at a spinner, and so the daily settlement
 * file has something to match against.
 */
@Component
public class ReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationJob.class);

    private final PaymentRepository paymentRepository;
    private final PaymentProperties properties;

    public ReconciliationJob(PaymentRepository paymentRepository, PaymentProperties properties) {
        this.paymentRepository = paymentRepository;
        this.properties = properties;
    }

    @Scheduled(cron = "${payments.reconciliation.cron}")
    @Transactional
    public void scheduledSweep() {
        int closed = reconcile();
        if (closed > 0) {
            log.info("Reconciliation closed {} stale payment(s)", closed);
        }
    }

    @Transactional
    public int reconcile() {
        Instant cutoff = Instant.now()
                .minus(properties.getReconciliation().getStaleAfterMinutes(), ChronoUnit.MINUTES);

        List<Payment> stale = paymentRepository
                .findByStatusAndUpdatedAtBefore(PaymentStatus.PROCESSING, cutoff);

        int closed = 0;
        for (Payment payment : stale) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("No response from the acquirer within "
                    + properties.getReconciliation().getStaleAfterMinutes() + " minutes");
            payment.getOrder().setStatus(OrderStatus.PAYMENT_FAILED);
            closed++;
            log.debug("Reconciliation closed {} as failed", payment.getPaymentRef());
        }

        return closed;
    }
}
