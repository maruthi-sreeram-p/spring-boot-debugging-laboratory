package com.spicebox.ordering.messaging;

import com.spicebox.ordering.service.OrderStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Sends a rider once the kitchen marks an order ready. Moving the order to
 * OUT_FOR_DELIVERY is what makes the tracking screen start updating for the customer.
 */
@Component
public class DispatchListener {

    private static final Logger log = LoggerFactory.getLogger(DispatchListener.class);

    private final OrderStatusService orderStatusService;

    public DispatchListener(OrderStatusService orderStatusService) {
        this.orderStatusService = orderStatusService;
    }

    @RabbitListener(queues = "${spicebox.messaging.dispatch-queue}")
    public void onOrderReady(OrderEventMessage message) {
        log.info("Dispatch picked up order {} for {}", message.getOrderCode(), message.getDeliveryAddress());
        orderStatusService.markOutForDelivery(message.getOrderId());
    }
}
