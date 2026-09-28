package com.ledgerline.payments.service;

import com.ledgerline.payments.config.PaymentProperties;
import com.ledgerline.payments.dto.PaymentResponse;
import com.ledgerline.payments.dto.SubmitPaymentRequest;
import com.ledgerline.payments.entity.AttemptOutcome;
import com.ledgerline.payments.entity.OrderStatus;
import com.ledgerline.payments.entity.Payment;
import com.ledgerline.payments.entity.PaymentAttempt;
import com.ledgerline.payments.entity.PaymentOrder;
import com.ledgerline.payments.entity.PaymentStatus;
import com.ledgerline.payments.exception.PaymentRuleException;
import com.ledgerline.payments.exception.ResourceNotFoundException;
import com.ledgerline.payments.gateway.GatewayResult;
import com.ledgerline.payments.gateway.GatewayTimeoutException;
import com.ledgerline.payments.gateway.SimulatedPaymentGateway;
import com.ledgerline.payments.mapper.PaymentMapper;
import com.ledgerline.payments.repository.PaymentAttemptRepository;
import com.ledgerline.payments.repository.PaymentOrderRepository;
import com.ledgerline.payments.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final SimulatedPaymentGateway gateway;
    private final PaymentMapper paymentMapper;
    private final PaymentProperties properties;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentAttemptRepository paymentAttemptRepository,
                          PaymentOrderRepository paymentOrderRepository,
                          SimulatedPaymentGateway gateway,
                          PaymentMapper paymentMapper,
                          PaymentProperties properties) {
        this.paymentRepository = paymentRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.paymentOrderRepository = paymentOrderRepository;
        this.gateway = gateway;
        this.paymentMapper = paymentMapper;
        this.properties = properties;
    }

    /**
     * Takes a payment for an order. Callers send an Idempotency-Key header so that a retry
     * after a dropped connection returns the original payment instead of taking the money
     * a second time.
     *
     * The acquirer occasionally drops the response rather than the request, so the call is
     * retried a few times before the payment is left for reconciliation.
     */
    @Retryable(retryFor = GatewayTimeoutException.class,
            maxAttemptsExpression = "${payments.retry.max-attempts}",
            backoff = @Backoff(delayExpression = "${payments.retry.backoff-millis}"))
    @Transactional
    public PaymentResponse submit(String idempotencyKey, SubmitPaymentRequest request) {
        Optional<Payment> alreadyTaken =
                paymentRepository.findByIdempotencyKeyAndStatus(idempotencyKey, PaymentStatus.SUCCEEDED);
        if (alreadyTaken.isPresent()) {
            log.info("Idempotency key {} already settled as {}", idempotencyKey,
                    alreadyTaken.get().getPaymentRef());
            return paymentMapper.toResponse(alreadyTaken.get());
        }

        PaymentOrder order = paymentOrderRepository.findByOrderRef(request.getOrderRef())
                .orElseThrow(() -> new ResourceNotFoundException("Order", request.getOrderRef()));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new PaymentRuleException("Order " + order.getOrderRef() + " has been cancelled");
        }
        if (!order.getMerchant().isActive()) {
            throw new PaymentRuleException("Merchant " + order.getMerchant().getCode() + " is not active");
        }

        Payment payment = new Payment();
        payment.setPaymentRef(nextPaymentRef());
        payment.setOrder(order);
        payment.setIdempotencyKey(idempotencyKey);
        payment.setAmount(request.getAmount());
        payment.setCurrency(order.getCurrency());
        payment.setInstrument(request.getInstrument());
        payment.setStatus(PaymentStatus.PROCESSING);
        payment.setAttempts(0);
        Payment saved = paymentRepository.save(payment);

        order.setStatus(OrderStatus.PAID);

        saved.setAttempts(saved.getAttempts() + 1);
        GatewayResult result = gateway.capture(saved.getPaymentRef(), saved.getAmount(), saved.getInstrument());

        if (result.approved()) {
            saved.setStatus(PaymentStatus.SUCCEEDED);
            saved.setGatewayRef(result.gatewayRef());
            recordAttempt(saved, AttemptOutcome.APPROVED, result.gatewayRef(), result.message());
            log.info("Payment {} succeeded for order {}", saved.getPaymentRef(), order.getOrderRef());
        } else {
            saved.setStatus(PaymentStatus.FAILED);
            saved.setFailureReason(result.message());
            recordAttempt(saved, AttemptOutcome.DECLINED, null, result.message());
            log.info("Payment {} declined for order {}: {}",
                    saved.getPaymentRef(), order.getOrderRef(), result.message());
        }

        return paymentMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(String paymentRef) {
        Payment payment = paymentRepository.findByPaymentRef(paymentRef)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentRef));
        return paymentMapper.toResponse(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> paymentsForOrder(String orderRef) {
        PaymentOrder order = paymentOrderRepository.findByOrderRef(orderRef)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderRef));
        return paymentMapper.toResponses(paymentRepository.findByOrderIdOrderByCreatedAtAsc(order.getId()));
    }

    @Transactional(readOnly = true)
    public List<PaymentAttempt> attemptsFor(String paymentRef) {
        Payment payment = paymentRepository.findByPaymentRef(paymentRef)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentRef));
        return paymentAttemptRepository.findByPaymentIdOrderByAttemptNoAsc(payment.getId());
    }

    private void recordAttempt(Payment payment, AttemptOutcome outcome, String gatewayRef, String message) {
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setPaymentId(payment.getId());
        attempt.setAttemptNo(payment.getAttempts());
        attempt.setOutcome(outcome);
        attempt.setGatewayRef(gatewayRef);
        attempt.setMessage(message);
        paymentAttemptRepository.save(attempt);
    }

    private String nextPaymentRef() {
        String candidate;
        do {
            candidate = "PAY-" + ThreadLocalRandom.current().nextInt(70_000, 999_999);
        } while (paymentRepository.existsByPaymentRef(candidate));
        return candidate;
    }
}
