package com.meridian.banking.mapper;

import com.meridian.banking.dto.CustomerResponse;
import com.meridian.banking.entity.Customer;
import com.meridian.banking.entity.Role;
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
                customer.getNationalId(),
                customer.getStatus().name(),
                roles);
    }
}
