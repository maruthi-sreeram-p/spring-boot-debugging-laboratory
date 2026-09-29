package com.riverstone.orderevents.mapper;

import com.riverstone.orderevents.dto.EventLogResponse;
import com.riverstone.orderevents.dto.OrderResponse;
import com.riverstone.orderevents.dto.StockResponse;
import com.riverstone.orderevents.entity.EventLogEntry;
import com.riverstone.orderevents.entity.OrderRecord;
import com.riverstone.orderevents.entity.StockItem;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PipelineMapper {

    public OrderResponse toResponse(OrderRecord order) {
        return new OrderResponse(
                order.getId(),
                order.getOrderRef(),
                order.getCustomerRef(),
                order.getSku(),
                order.getQuantity(),
                order.getStatus().name(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    public List<OrderResponse> toResponses(List<OrderRecord> orders) {
        return orders.stream().map(this::toResponse).toList();
    }

    public StockResponse toResponse(StockItem item) {
        return new StockResponse(item.getSku(), item.getName(), item.getAvailable(), item.getReserved());
    }

    public List<StockResponse> toStockResponses(List<StockItem> items) {
        return items.stream().map(this::toResponse).toList();
    }

    public EventLogResponse toResponse(EventLogEntry entry) {
        return new EventLogResponse(
                entry.getId(),
                entry.getTopic(),
                entry.getPartitionNo(),
                entry.getOffsetNo(),
                entry.getMessageKey(),
                entry.getEventType(),
                entry.getOrderRef(),
                entry.getConsumerGroup(),
                entry.getHandledBy(),
                entry.getCreatedAt());
    }

    public List<EventLogResponse> toEventResponses(List<EventLogEntry> entries) {
        return entries.stream().map(this::toResponse).toList();
    }
}
