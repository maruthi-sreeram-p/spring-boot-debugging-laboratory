package com.ledgerline.payments.controller;

import com.ledgerline.payments.dto.AttemptResponse;
import com.ledgerline.payments.dto.PaymentResponse;
import com.ledgerline.payments.dto.SubmitPaymentRequest;
import com.ledgerline.payments.mapper.PaymentMapper;
import com.ledgerline.payments.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentMapper paymentMapper;

    public PaymentController(PaymentService paymentService, PaymentMapper paymentMapper) {
        this.paymentService = paymentService;
        this.paymentMapper = paymentMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse submit(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                  @Valid @RequestBody SubmitPaymentRequest request) {
        return paymentService.submit(idempotencyKey, request);
    }

    @GetMapping("/{paymentRef}")
    public PaymentResponse get(@PathVariable String paymentRef) {
        return paymentService.getPayment(paymentRef);
    }

    @GetMapping("/{paymentRef}/attempts")
    public List<AttemptResponse> attempts(@PathVariable String paymentRef) {
        return paymentMapper.toAttemptResponses(paymentService.attemptsFor(paymentRef));
    }
}
