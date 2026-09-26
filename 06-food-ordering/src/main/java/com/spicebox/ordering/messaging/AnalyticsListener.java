package com.spicebox.ordering.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Feeds the daily order volume dashboard. It only needs to count, so it does nothing
 * except record that the event arrived.
 */
@Component
public class AnalyticsListener {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsListener.class);

    @RabbitListener(queues = "${spicebox.messaging.analytics-queue}")
    public void onOrderPlaced(OrderEventMessage message) {
        log.info("Analytics recorded order {} from restaurant {} worth {}",
                message.getOrderCode(), message.getRestaurantName(), message.getTotalAmount());
    }
}
