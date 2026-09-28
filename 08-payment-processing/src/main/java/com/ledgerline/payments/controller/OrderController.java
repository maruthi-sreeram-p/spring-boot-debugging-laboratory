package com.ledgerline.payments.controller;

import com.ledgerline.payments.dto.CreateOrderRequest;
import com.ledgerline.payments.dto.OrderResponse;
import com.ledgerline.payments.dto.PaymentResponse;
import com.ledgerline.payments.service.OrderService;
import com.ledgerline.payments.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final PaymentService paymentService;

    public OrderController(OrderService orderService, PaymentService paymentService) {
        this.orderService = orderService;
        this.paymentService = paymentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(request);
    }

    @GetMapping("/{orderRef}")
    public OrderResponse get(@PathVariable String orderRef) {
        return orderService.getOrder(orderRef);
    }

    @GetMapping("/{orderRef}/payments")
    public List<PaymentResponse> payments(@PathVariable String orderRef) {
        return paymentService.paymentsForOrder(orderRef);
    }
}
