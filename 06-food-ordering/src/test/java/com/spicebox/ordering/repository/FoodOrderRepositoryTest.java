package com.spicebox.ordering.repository;

import com.spicebox.ordering.entity.Customer;
import com.spicebox.ordering.entity.FoodOrder;
import com.spicebox.ordering.entity.OrderStatus;
import com.spicebox.ordering.entity.Restaurant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class FoodOrderRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private FoodOrderRepository foodOrderRepository;

    @Test
    void findsOrdersWaitingForKitchenIntake() {
        Customer customer = new Customer();
        customer.setEmail("test.diner@example.com");
        customer.setPasswordHash("$2a$10$notarealhash");
        customer.setFullName("Test Diner");
        customer.setDefaultAddress("1 Test Street");
        entityManager.persist(customer);

        Restaurant restaurant = new Restaurant();
        restaurant.setName("Test Kitchen");
        restaurant.setCuisine("Test");
        restaurant.setCity("Bengaluru");
        entityManager.persist(restaurant);

        persistOrder("SPX-T-0001", customer, restaurant, OrderStatus.PLACED);
        persistOrder("SPX-T-0002", customer, restaurant, OrderStatus.DELIVERED);
        persistOrder("SPX-T-0003", customer, restaurant, OrderStatus.PLACED);
        entityManager.flush();
        entityManager.clear();

        List<FoodOrder> waiting = foodOrderRepository.findByStatusOrderByPlacedAtAsc(OrderStatus.PLACED);

        assertThat(waiting).extracting(FoodOrder::getOrderCode)
                .containsExactlyInAnyOrder("SPX-T-0001", "SPX-T-0003");
    }

    private void persistOrder(String code, Customer customer, Restaurant restaurant, OrderStatus status) {
        FoodOrder order = new FoodOrder();
        order.setOrderCode(code);
        order.setCustomer(customer);
        order.setRestaurant(restaurant);
        order.setStatus(status);
        order.setSubtotal(new BigDecimal("200.00"));
        order.setDeliveryFee(new BigDecimal("39.00"));
        order.setTotalAmount(new BigDecimal("239.00"));
        order.setDeliveryAddress("1 Test Street");
        entityManager.persist(order);
    }
}
