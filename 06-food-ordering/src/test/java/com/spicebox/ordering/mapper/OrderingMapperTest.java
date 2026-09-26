package com.spicebox.ordering.mapper;

import com.spicebox.ordering.dto.OrderResponse;
import com.spicebox.ordering.entity.Customer;
import com.spicebox.ordering.entity.FoodOrder;
import com.spicebox.ordering.entity.MenuItem;
import com.spicebox.ordering.entity.OrderItem;
import com.spicebox.ordering.entity.OrderStatus;
import com.spicebox.ordering.entity.Restaurant;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OrderingMapperTest {

    private final OrderingMapper mapper = new OrderingMapper();

    @Test
    void mapsOrderWithItemsForTheCustomerApp() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Curry Leaf Kitchen");
        restaurant.setCuisine("South Indian");
        restaurant.setCity("Bengaluru");

        Customer customer = new Customer();
        customer.setId(1L);
        customer.setEmail("ayesha.khan@example.com");

        MenuItem dosa = new MenuItem();
        dosa.setId(1L);
        dosa.setRestaurant(restaurant);
        dosa.setName("Masala Dosa");
        dosa.setPrice(new BigDecimal("140.00"));

        OrderItem item = new OrderItem();
        item.setMenuItem(dosa);
        item.setItemName("Masala Dosa");
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("140.00"));
        item.setLineTotal(new BigDecimal("280.00"));

        FoodOrder order = new FoodOrder();
        order.setId(1L);
        order.setOrderCode("SPX-20250310-4471");
        order.setCustomer(customer);
        order.setRestaurant(restaurant);
        order.setStatus(OrderStatus.DELIVERED);
        order.setSubtotal(new BigDecimal("280.00"));
        order.setDeliveryFee(new BigDecimal("39.00"));
        order.setTotalAmount(new BigDecimal("319.00"));
        order.setDeliveryAddress("Flat 402, Palm Grove");
        order.setPlacedAt(Instant.parse("2025-03-10T13:12:00Z"));
        order.setUpdatedAt(Instant.parse("2025-03-10T14:02:00Z"));
        order.addItem(item);

        OrderResponse response = mapper.toResponse(order);

        assertThat(response.getOrderCode()).isEqualTo("SPX-20250310-4471");
        assertThat(response.getRestaurantName()).isEqualTo("Curry Leaf Kitchen");
        assertThat(response.getStatus()).isEqualTo("DELIVERED");
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getItemName()).isEqualTo("Masala Dosa");
        assertThat(response.getItems().get(0).getLineTotal()).isEqualByComparingTo("280.00");
    }
}
