package com.riverstone.orderevents.repository;

import com.riverstone.orderevents.entity.OrderRecord;
import com.riverstone.orderevents.entity.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class OrderRecordRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private OrderRecordRepository orderRepository;

    @Test
    void findsOrdersByReferenceAndStatus() {
        persist("RS-000001", OrderStatus.CREATED);
        persist("RS-000002", OrderStatus.CONFIRMED);
        persist("RS-000003", OrderStatus.CREATED);
        entityManager.flush();
        entityManager.clear();

        assertThat(orderRepository.findByOrderRef("RS-000002")).isPresent();
        assertThat(orderRepository.countByStatus(OrderStatus.CREATED)).isEqualTo(2);
        assertThat(orderRepository
                .findByStatusOrderByCreatedAtDesc(OrderStatus.CREATED, PageRequest.of(0, 10))
                .getTotalElements()).isEqualTo(2);
    }

    private void persist(String ref, OrderStatus status) {
        OrderRecord order = new OrderRecord();
        order.setOrderRef(ref);
        order.setCustomerRef("CUST-1");
        order.setSku("RS-WIDGET-01");
        order.setQuantity(1);
        order.setStatus(status);
        entityManager.persist(order);
    }
}
