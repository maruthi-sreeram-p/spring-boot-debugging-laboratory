package com.riverstone.orderevents.messaging;

import com.riverstone.orderevents.config.RiverstoneProperties;
import com.riverstone.orderevents.entity.OrderNotification;
import com.riverstone.orderevents.repository.OrderNotificationRepository;
import com.riverstone.orderevents.service.EventRecorder;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tells the customer what happened to their order.
 */
@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final OrderNotificationRepository notificationRepository;
    private final EventRecorder eventRecorder;
    private final RiverstoneProperties properties;

    public NotificationConsumer(OrderNotificationRepository notificationRepository,
                                EventRecorder eventRecorder,
                                RiverstoneProperties properties) {
        this.notificationRepository = notificationRepository;
        this.eventRecorder = eventRecorder;
        this.properties = properties;
    }

    @KafkaListener(
            topics = "${riverstone.topics.inventory-events}",
            groupId = "${riverstone.groups.downstream}")
    @Transactional
    public void onInventoryEvent(ConsumerRecord<String, InventoryEvent> record, Acknowledgment acknowledgment) {
        InventoryEvent event = record.value();

        eventRecorder.record(record.topic(), record.partition(), record.offset(), record.key(),
                "Inventory" + event.getOutcome(), event.getOrderRef(),
                properties.getGroups().getDownstream(), "NotificationConsumer");

        OrderNotification notification = new OrderNotification();
        notification.setOrderRef(event.getOrderRef());
        notification.setChannel("EMAIL");
        notification.setSubject("RESERVED".equals(event.getOutcome())
                ? "Your order " + event.getOrderRef() + " is confirmed"
                : "We could not fulfil order " + event.getOrderRef());
        notificationRepository.save(notification);

        log.info("Notification queued for {} ({})", event.getOrderRef(), event.getOutcome());
        acknowledgment.acknowledge();
    }
}
