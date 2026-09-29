package com.riverstone.orderevents.messaging;

import com.riverstone.orderevents.config.RiverstoneProperties;
import com.riverstone.orderevents.entity.Reservation;
import com.riverstone.orderevents.entity.StockItem;
import com.riverstone.orderevents.repository.ReservationRepository;
import com.riverstone.orderevents.repository.StockItemRepository;
import com.riverstone.orderevents.service.EventRecorder;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Reserves stock for every accepted order and tells the rest of the pipeline what happened.
 */
@Component
public class InventoryConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryConsumer.class);

    private final StockItemRepository stockItemRepository;
    private final ReservationRepository reservationRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final EventRecorder eventRecorder;
    private final RiverstoneProperties properties;

    public InventoryConsumer(StockItemRepository stockItemRepository,
                             ReservationRepository reservationRepository,
                             KafkaTemplate<String, Object> kafkaTemplate,
                             EventRecorder eventRecorder,
                             RiverstoneProperties properties) {
        this.stockItemRepository = stockItemRepository;
        this.reservationRepository = reservationRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.eventRecorder = eventRecorder;
        this.properties = properties;
    }

    @KafkaListener(
            topics = "${riverstone.topics.orders-created}",
            groupId = "${riverstone.groups.inventory}")
    @Transactional
    public void onOrderCreated(ConsumerRecord<String, OrderCreatedEvent> record, Acknowledgment acknowledgment) {
        acknowledgment.acknowledge();

        OrderCreatedEvent event = record.value();
        eventRecorder.record(record.topic(), record.partition(), record.offset(), record.key(),
                "OrderCreated", event.getOrderRef(),
                properties.getGroups().getInventory(), "InventoryConsumer");

        StockItem item = stockItemRepository.findBySku(event.getSku())
                .orElseThrow(() -> new IllegalStateException("Unknown SKU " + event.getSku()));

        if (item.getSku().contains("POISON")) {
            throw new IllegalStateException("Discontinued line " + item.getSku() + " cannot be allocated");
        }

        if (item.getAvailable() < event.getQuantity()) {
            publish(new InventoryEvent(event.getOrderRef(), event.getSku(), event.getQuantity(),
                    "REJECTED", "Only " + item.getAvailable() + " available", Instant.now()));
            log.info("Rejected {}: only {} of {} available",
                    event.getOrderRef(), item.getAvailable(), event.getSku());
            return;
        }

        item.setAvailable(item.getAvailable() - event.getQuantity());
        item.setReserved(item.getReserved() + event.getQuantity());

        Reservation reservation = new Reservation();
        reservation.setOrderRef(event.getOrderRef());
        reservation.setSku(event.getSku());
        reservation.setQuantity(event.getQuantity());
        reservationRepository.save(reservation);

        publish(new InventoryEvent(event.getOrderRef(), event.getSku(), event.getQuantity(),
                "RESERVED", "Reserved " + event.getQuantity() + " of " + event.getSku(), Instant.now()));

        confirmAllocationPlan(item);

        log.info("Reserved {} x {} for {}", event.getQuantity(), event.getSku(), event.getOrderRef());
    }

    /**
     * Pre-release lines are held back until the assembly plan clears them, which is checked
     * once the allocation has been worked out.
     */
    private void confirmAllocationPlan(StockItem item) {
        if (item.getSku().contains("GHOST")) {
            throw new IllegalStateException("Allocation plan has not cleared " + item.getSku());
        }
    }

    private void publish(InventoryEvent event) {
        kafkaTemplate.send(properties.getTopics().getInventoryEvents(), event.getOrderRef(), event);
    }
}
