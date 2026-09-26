package com.spicebox.ordering.controller;

import com.spicebox.ordering.dto.OrderResponse;
import com.spicebox.ordering.dto.UpdateOrderStatusRequest;
import com.spicebox.ordering.service.OrderStatusService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ops")
public class OpsController {

    private final OrderStatusService orderStatusService;

    public OpsController(OrderStatusService orderStatusService) {
        this.orderStatusService = orderStatusService;
    }

    @GetMapping("/intake-queue")
    public List<OrderResponse> intakeQueue() {
        return orderStatusService.ordersAwaitingIntake();
    }

    @GetMapping("/restaurants/{restaurantId}/queue")
    public List<OrderResponse> kitchenQueue(@PathVariable Long restaurantId) {
        return orderStatusService.kitchenQueue(restaurantId);
    }

    @PostMapping("/orders/{orderId}/status")
    public OrderResponse changeStatus(@PathVariable Long orderId,
                                      @Valid @RequestBody UpdateOrderStatusRequest request) {
        return orderStatusService.changeStatus(orderId, request.getStatus());
    }
}
