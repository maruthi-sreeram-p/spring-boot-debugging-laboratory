package com.spicebox.ordering.messaging;

import com.spicebox.ordering.config.SpiceboxProperties;
import com.spicebox.ordering.entity.FoodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final SpiceboxProperties properties;

    public OrderEventPublisher(RabbitTemplate rabbitTemplate, SpiceboxProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    public void publishOrderPlaced(FoodOrder order) {
        publish(properties.getMessaging().getPlacedRoutingKey(), order);
    }

    public void publishOrderReadyForPickup(FoodOrder order) {
        publish(properties.getMessaging().getReadyRoutingKey(), order);
    }

    private void publish(String routingKey, FoodOrder order) {
        OrderEventMessage message = new OrderEventMessage(
                order.getId(),
                order.getOrderCode(),
                order.getRestaurant().getId(),
                order.getRestaurant().getName(),
                order.getDeliveryAddress(),
                order.getTotalAmount(),
                order.getStatus().name(),
                Instant.now());

        rabbitTemplate.convertAndSend(properties.getMessaging().getExchange(), routingKey, message);
        log.debug("Published {} to {} with routing key {}",
                order.getOrderCode(), properties.getMessaging().getExchange(), routingKey);
    }
}
