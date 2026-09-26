package com.spicebox.ordering.controller;

import com.spicebox.ordering.dto.NotificationResponse;
import com.spicebox.ordering.dto.OrderResponse;
import com.spicebox.ordering.dto.PagedResponse;
import com.spicebox.ordering.dto.PlaceOrderRequest;
import com.spicebox.ordering.security.SpiceboxUser;
import com.spicebox.ordering.service.NotificationService;
import com.spicebox.ordering.service.OrderService;
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

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final NotificationService notificationService;

    public OrderController(OrderService orderService, NotificationService notificationService) {
        this.orderService = orderService;
        this.notificationService = notificationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse place(@AuthenticationPrincipal SpiceboxUser principal,
                               @Valid @RequestBody PlaceOrderRequest request) {
        return orderService.placeOrder(principal, request);
    }

    @GetMapping
    public PagedResponse<OrderResponse> mine(@AuthenticationPrincipal SpiceboxUser principal,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return orderService.myOrders(principal, pageable);
    }

    @GetMapping("/{orderId}")
    public OrderResponse get(@AuthenticationPrincipal SpiceboxUser principal,
                             @PathVariable Long orderId) {
        return orderService.getOrder(orderId, principal);
    }

    @GetMapping("/{orderId}/notifications")
    public List<NotificationResponse> notifications(@AuthenticationPrincipal SpiceboxUser principal,
                                                    @PathVariable Long orderId) {
        orderService.getOrder(orderId, principal);
        return notificationService.notificationsFor(orderId);
    }
}
