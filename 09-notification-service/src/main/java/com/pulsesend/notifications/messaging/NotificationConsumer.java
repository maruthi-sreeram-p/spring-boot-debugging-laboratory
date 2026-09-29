package com.pulsesend.notifications.messaging;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * One listener per channel queue. They all do the same work; the split exists so that a
 * slow provider on one channel cannot hold up the others.
 */
@Component
public class NotificationConsumer {

    private final NotificationDeliveryHandler deliveryHandler;

    public NotificationConsumer(NotificationDeliveryHandler deliveryHandler) {
        this.deliveryHandler = deliveryHandler;
    }

    @RabbitListener(queues = "${pulsesend.messaging.email-queue}")
    public void onEmail(NotificationMessage message) {
        deliveryHandler.handle(message);
    }

    @RabbitListener(queues = "${pulsesend.messaging.sms-queue}")
    public void onSms(NotificationMessage message) {
        deliveryHandler.handle(message);
    }

    @RabbitListener(queues = "${pulsesend.messaging.in-app-queue}")
    public void onInApp(NotificationMessage message) {
        deliveryHandler.handle(message);
    }
}
