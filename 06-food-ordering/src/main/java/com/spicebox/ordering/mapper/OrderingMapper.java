package com.spicebox.ordering.mapper;

import com.spicebox.ordering.dto.CustomerResponse;
import com.spicebox.ordering.dto.MenuItemResponse;
import com.spicebox.ordering.dto.NotificationResponse;
import com.spicebox.ordering.dto.OrderItemResponse;
import com.spicebox.ordering.dto.OrderResponse;
import com.spicebox.ordering.dto.RestaurantResponse;
import com.spicebox.ordering.entity.Customer;
import com.spicebox.ordering.entity.FoodOrder;
import com.spicebox.ordering.entity.MenuItem;
import com.spicebox.ordering.entity.OrderItem;
import com.spicebox.ordering.entity.OrderNotification;
import com.spicebox.ordering.entity.Restaurant;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderingMapper {

    public RestaurantResponse toResponse(Restaurant restaurant) {
        return new RestaurantResponse(restaurant.getId(), restaurant.getName(), restaurant.getCuisine(),
                restaurant.getCity(), restaurant.getPrepMinutes(), restaurant.isActive());
    }

    public List<RestaurantResponse> toRestaurantResponses(List<Restaurant> restaurants) {
        return restaurants.stream().map(this::toResponse).toList();
    }

    public MenuItemResponse toResponse(MenuItem item) {
        return new MenuItemResponse(item.getId(), item.getRestaurant().getId(), item.getName(),
                item.getDescription(), item.getCategory(), item.getPrice(), item.isAvailable());
    }

    public List<MenuItemResponse> toMenuResponses(List<MenuItem> items) {
        return items.stream().map(this::toResponse).toList();
    }

    public OrderResponse toResponse(FoodOrder order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(this::toResponse)
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getOrderCode(),
                order.getRestaurant().getId(),
                order.getRestaurant().getName(),
                order.getStatus().name(),
                order.getSubtotal(),
                order.getDeliveryFee(),
                order.getTotalAmount(),
                order.getDeliveryAddress(),
                order.getPlacedAt(),
                order.getUpdatedAt(),
                items);
    }

    public List<OrderResponse> toOrderResponses(List<FoodOrder> orders) {
        return orders.stream().map(this::toResponse).toList();
    }

    public CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(customer.getId(), customer.getEmail(), customer.getFullName(),
                customer.getPhone(), customer.getDefaultAddress(), customer.getRoleName());
    }

    public NotificationResponse toResponse(OrderNotification notification) {
        return new NotificationResponse(notification.getId(), notification.getOrderId(),
                notification.getChannel(), notification.getRecipient(),
                notification.getSubject(), notification.getSentAt());
    }

    public List<NotificationResponse> toNotificationResponses(List<OrderNotification> notifications) {
        return notifications.stream().map(this::toResponse).toList();
    }

    private OrderItemResponse toResponse(OrderItem item) {
        return new OrderItemResponse(item.getMenuItem().getId(), item.getItemName(),
                item.getQuantity(), item.getUnitPrice(), item.getLineTotal());
    }
}
