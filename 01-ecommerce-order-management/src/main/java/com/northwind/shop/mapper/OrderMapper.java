package com.northwind.shop.mapper;

import com.northwind.shop.dto.OrderItemResponse;
import com.northwind.shop.dto.OrderResponse;
import com.northwind.shop.entity.Order;
import com.northwind.shop.entity.OrderItem;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderMapper {

    public OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(this::toItemResponse)
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus().name(),
                order.getSubtotal(),
                order.getShippingFee(),
                order.getTotalAmount(),
                order.getShippingAddress(),
                order.getCreatedAt(),
                items);
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(
                item.getProduct().getId(),
                item.getProductName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getLineTotal());
    }
}
