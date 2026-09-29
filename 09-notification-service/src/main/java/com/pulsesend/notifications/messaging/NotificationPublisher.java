package com.pulsesend.notifications.messaging;

import com.pulsesend.notifications.config.PulsesendProperties;
import com.pulsesend.notifications.entity.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final PulsesendProperties properties;

    public NotificationPublisher(RabbitTemplate rabbitTemplate, PulsesendProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    public void publish(Notification notification) {
        String routingKey = routingKeyFor(notification);

        NotificationMessage message = new NotificationMessage(
                notification.getNotificationRef(),
                notification.getRecipientRef(),
                notification.getDestination(),
                notification.getChannel().name(),
                notification.getTemplateCode(),
                notification.getPayload(),
                Instant.now());

        rabbitTemplate.convertAndSend(properties.getMessaging().getExchange(), routingKey, message);
        log.debug("Published {} with routing key {}", notification.getNotificationRef(), routingKey);
    }

    public String routingKeyFor(Notification notification) {
        return properties.getMessaging().getRoutingPrefix()
                + "." + notification.getChannel().routingSegment()
                + "." + notification.getTemplateCode();
    }
}
