package com.riverstone.orderevents.messaging;

import com.riverstone.orderevents.config.RiverstoneProperties;
import com.riverstone.orderevents.entity.OrderRecord;
import com.riverstone.orderevents.entity.OrderStatus;
import com.riverstone.orderevents.repository.OrderRecordRepository;
import com.riverstone.orderevents.service.EventRecorder;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moves the order to its final status once inventory has decided.
 */
@Component
public class OrderStatusConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusConsumer.class);

    private final OrderRecordRepository orderRepository;
    private final EventRecorder eventRecorder;
    private final RiverstoneProperties properties;

    public OrderStatusConsumer(OrderRecordRepository orderRepository,
                               EventRecorder eventRecorder,
                               RiverstoneProperties properties) {
        this.orderRepository = orderRepository;
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
                properties.getGroups().getDownstream(), "OrderStatusConsumer");

        OrderRecord order = orderRepository.findByOrderRef(event.getOrderRef()).orElse(null);
        if (order == null) {
            log.warn("No order {} for inventory event, ignoring", event.getOrderRef());
            acknowledgment.acknowledge();
            return;
        }

        OrderStatus target = "RESERVED".equals(event.getOutcome())
                ? OrderStatus.CONFIRMED
                : OrderStatus.REJECTED;

        order.setStatus(target);
        log.info("Order {} moved to {} from partition {} offset {}",
                order.getOrderRef(), target, record.partition(), record.offset());

        acknowledgment.acknowledge();
    }
}
