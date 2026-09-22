package com.northwind.shop.mapper;

import com.northwind.shop.dto.CustomerResponse;
import com.northwind.shop.entity.Customer;
import com.northwind.shop.entity.Role;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class CustomerMapper {

    public CustomerResponse toResponse(Customer customer) {
        List<String> roles = customer.getRoles().stream()
                .map(Role::getName)
                .sorted(Comparator.naturalOrder())
                .toList();
        return new CustomerResponse(
                customer.getId(),
                customer.getEmail(),
                customer.getFullName(),
                customer.getPhone(),
                customer.getStatus().name(),
                roles);
    }
}
