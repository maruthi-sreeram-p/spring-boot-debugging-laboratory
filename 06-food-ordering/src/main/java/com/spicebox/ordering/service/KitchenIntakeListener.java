package com.spicebox.ordering.service;

import com.spicebox.ordering.entity.FoodOrder;
import com.spicebox.ordering.entity.OrderStatus;
import com.spicebox.ordering.exception.ResourceNotFoundException;
import com.spicebox.ordering.repository.FoodOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Puts a freshly placed order in front of the restaurant. Runs off the request thread so a
 * slow kitchen integration never holds up checkout.
 */
@Component
public class KitchenIntakeListener {

    private static final Logger log = LoggerFactory.getLogger(KitchenIntakeListener.class);

    private final FoodOrderRepository foodOrderRepository;

    public KitchenIntakeListener(FoodOrderRepository foodOrderRepository) {
        this.foodOrderRepository = foodOrderRepository;
    }

    @Async
    @EventListener
    @Transactional
    public void onOrderPlaced(OrderPlacedEvent event) {
        FoodOrder order = foodOrderRepository.findById(event.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", event.orderId()));

        if (order.getStatus() != OrderStatus.PLACED) {
            log.debug("Order {} is already {}, kitchen intake skipped", order.getOrderCode(), order.getStatus());
            return;
        }

        order.setStatus(OrderStatus.ACCEPTED);
        log.info("Kitchen accepted order {} for {}", order.getOrderCode(), order.getRestaurant().getName());
    }
}
