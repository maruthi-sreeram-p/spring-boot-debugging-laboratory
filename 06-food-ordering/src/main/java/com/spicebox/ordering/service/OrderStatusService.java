package com.spicebox.ordering.service;

import com.spicebox.ordering.dto.OrderResponse;
import com.spicebox.ordering.entity.FoodOrder;
import com.spicebox.ordering.entity.OrderStatus;
import com.spicebox.ordering.exception.IllegalStatusChangeException;
import com.spicebox.ordering.exception.ResourceNotFoundException;
import com.spicebox.ordering.mapper.OrderingMapper;
import com.spicebox.ordering.messaging.OrderEventPublisher;
import com.spicebox.ordering.repository.FoodOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The kitchen and the dispatch service both move orders along the same track:
 * PLACED, ACCEPTED, PREPARING, READY_FOR_PICKUP, OUT_FOR_DELIVERY, DELIVERED.
 */
@Service
public class OrderStatusService {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusService.class);

    private final FoodOrderRepository foodOrderRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final OrderingMapper orderingMapper;

    public OrderStatusService(FoodOrderRepository foodOrderRepository,
                              OrderEventPublisher orderEventPublisher,
                              OrderingMapper orderingMapper) {
        this.foodOrderRepository = foodOrderRepository;
        this.orderEventPublisher = orderEventPublisher;
        this.orderingMapper = orderingMapper;
    }

    @Transactional
    public OrderResponse changeStatus(Long orderId, String requestedStatus) {
        FoodOrder order = foodOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        OrderStatus target;
        try {
            target = OrderStatus.valueOf(requestedStatus.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(requestedStatus + " is not a known order status");
        }

        OrderStatus previous = order.getStatus();
        order.setStatus(target);

        if (target == OrderStatus.READY_FOR_PICKUP) {
            orderEventPublisher.publishOrderReadyForPickup(order);
        }

        log.info("Order {} moved from {} to {}", order.getOrderCode(), previous, target);
        return orderingMapper.toResponse(order);
    }

    @Transactional
    public void markOutForDelivery(Long orderId) {
        FoodOrder order = foodOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        if (order.getStatus() != OrderStatus.READY_FOR_PICKUP) {
            throw new IllegalStatusChangeException(order.getStatus().name(), "OUT_FOR_DELIVERY");
        }
        order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
        log.info("Order {} is out for delivery", order.getOrderCode());
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> kitchenQueue(Long restaurantId) {
        List<FoodOrder> orders = foodOrderRepository.findByRestaurantIdAndStatusInOrderByPlacedAtAsc(
                restaurantId, List.of(OrderStatus.PLACED, OrderStatus.ACCEPTED, OrderStatus.PREPARING));
        return orderingMapper.toOrderResponses(orders);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> ordersAwaitingIntake() {
        return orderingMapper.toOrderResponses(
                foodOrderRepository.findByStatusOrderByPlacedAtAsc(OrderStatus.PLACED));
    }
}
