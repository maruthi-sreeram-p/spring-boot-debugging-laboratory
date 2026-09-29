package com.riverstone.orderevents.mapper;

import com.riverstone.orderevents.dto.EventLogResponse;
import com.riverstone.orderevents.dto.OrderResponse;
import com.riverstone.orderevents.entity.EventLogEntry;
import com.riverstone.orderevents.entity.OrderRecord;
import com.riverstone.orderevents.entity.OrderStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineMapperTest {

    private final PipelineMapper mapper = new PipelineMapper();

    @Test
    void mapsOrderForTheApi() {
        OrderRecord order = new OrderRecord();
        order.setId(1L);
        order.setOrderRef("RS-441023");
        order.setCustomerRef("CUST-9001");
        order.setSku("RS-WIDGET-01");
        order.setQuantity(4);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setCreatedAt(Instant.parse("2025-06-01T10:00:00Z"));
        order.setUpdatedAt(Instant.parse("2025-06-01T10:00:02Z"));

        OrderResponse response = mapper.toResponse(order);

        assertThat(response.getOrderRef()).isEqualTo("RS-441023");
        assertThat(response.getStatus()).isEqualTo("CONFIRMED");
        assertThat(response.getQuantity()).isEqualTo(4);
    }

    @Test
    void mapsEventLogWithItsCoordinates() {
        EventLogEntry entry = new EventLogEntry();
        entry.setId(7L);
        entry.setTopic("orders.created");
        entry.setPartitionNo(2);
        entry.setOffsetNo(41L);
        entry.setMessageKey("RS-441023");
        entry.setEventType("OrderCreated");
        entry.setOrderRef("RS-441023");
        entry.setConsumerGroup("inventory-service");
        entry.setHandledBy("InventoryConsumer");
        entry.setCreatedAt(Instant.parse("2025-06-01T10:00:01Z"));

        EventLogResponse response = mapper.toResponse(entry);

        assertThat(response.getTopic()).isEqualTo("orders.created");
        assertThat(response.getPartition()).isEqualTo(2);
        assertThat(response.getOffset()).isEqualTo(41L);
        assertThat(response.getHandledBy()).isEqualTo("InventoryConsumer");
    }
}
