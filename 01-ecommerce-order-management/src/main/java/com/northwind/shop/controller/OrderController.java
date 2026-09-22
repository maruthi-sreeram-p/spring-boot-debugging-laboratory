package com.northwind.shop.controller;

import com.northwind.shop.dto.OrderResponse;
import com.northwind.shop.dto.PagedResponse;
import com.northwind.shop.dto.PlaceOrderRequest;
import com.northwind.shop.security.AuthenticatedCustomer;
import com.northwind.shop.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse place(@AuthenticationPrincipal AuthenticatedCustomer principal,
                               @Valid @RequestBody PlaceOrderRequest request) {
        return orderService.placeOrder(principal.getCustomerId(), request);
    }

    @GetMapping
    public PagedResponse<OrderResponse> history(@AuthenticationPrincipal AuthenticatedCustomer principal,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return orderService.listOrders(principal.getCustomerId(), pageable);
    }

    @GetMapping("/{orderId}")
    public OrderResponse get(@AuthenticationPrincipal AuthenticatedCustomer principal,
                             @PathVariable Long orderId) {
        return orderService.getOrder(orderId);
    }
}
