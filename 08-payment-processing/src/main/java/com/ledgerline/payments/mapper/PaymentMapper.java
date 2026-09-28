package com.ledgerline.payments.mapper;

import com.ledgerline.payments.dto.AttemptResponse;
import com.ledgerline.payments.dto.OrderResponse;
import com.ledgerline.payments.dto.PaymentResponse;
import com.ledgerline.payments.entity.Payment;
import com.ledgerline.payments.entity.PaymentAttempt;
import com.ledgerline.payments.entity.PaymentOrder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PaymentMapper {

    public OrderResponse toResponse(PaymentOrder order) {
        return new OrderResponse(
                order.getId(),
                order.getOrderRef(),
                order.getMerchant().getCode(),
                order.getCustomerRef(),
                order.getAmount(),
                order.getCurrency(),
                order.getStatus().name(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    public PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getPaymentRef(),
                payment.getOrder().getOrderRef(),
                payment.getIdempotencyKey(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getInstrument(),
                payment.getStatus().name(),
                payment.getAttempts(),
                payment.getGatewayRef(),
                payment.getFailureReason(),
                payment.getCreatedAt(),
                payment.getUpdatedAt());
    }

    public List<PaymentResponse> toResponses(List<Payment> payments) {
        return payments.stream().map(this::toResponse).toList();
    }

    public AttemptResponse toResponse(PaymentAttempt attempt) {
        return new AttemptResponse(
                attempt.getId(),
                attempt.getAttemptNo(),
                attempt.getOutcome().name(),
                attempt.getGatewayRef(),
                attempt.getMessage(),
                attempt.getCreatedAt());
    }

    public List<AttemptResponse> toAttemptResponses(List<PaymentAttempt> attempts) {
        return attempts.stream().map(this::toResponse).toList();
    }
}
