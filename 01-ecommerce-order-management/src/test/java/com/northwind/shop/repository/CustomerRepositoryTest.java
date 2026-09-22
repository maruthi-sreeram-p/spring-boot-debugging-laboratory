package com.northwind.shop.repository;

import com.northwind.shop.entity.Customer;
import com.northwind.shop.entity.CustomerStatus;
import com.northwind.shop.entity.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class CustomerRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void findsCustomerWithRolesByEmail() {
        Role role = new Role();
        role.setName("ROLE_CUSTOMER");
        entityManager.persist(role);

        Customer customer = new Customer();
        customer.setEmail("nadia.khan@example.com");
        customer.setPasswordHash("$2a$10$notarealhash");
        customer.setFullName("Nadia Khan");
        customer.setStatus(CustomerStatus.ACTIVE);
        customer.getRoles().add(role);
        entityManager.persistAndFlush(customer);
        entityManager.clear();

        Optional<Customer> found = customerRepository.findByEmail("nadia.khan@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getFullName()).isEqualTo("Nadia Khan");
        assertThat(found.get().getRoles()).extracting(Role::getName).containsExactly("ROLE_CUSTOMER");
    }

    @Test
    void reportsWhetherAnEmailIsAlreadyRegistered() {
        Customer customer = new Customer();
        customer.setEmail("theo.baptiste@example.com");
        customer.setPasswordHash("$2a$10$notarealhash");
        customer.setFullName("Theo Baptiste");
        customer.setStatus(CustomerStatus.ACTIVE);
        entityManager.persistAndFlush(customer);

        assertThat(customerRepository.existsByEmail("theo.baptiste@example.com")).isTrue();
        assertThat(customerRepository.existsByEmail("nobody@example.com")).isFalse();
    }
}
