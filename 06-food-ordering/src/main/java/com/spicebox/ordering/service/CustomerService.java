package com.spicebox.ordering.service;

import com.spicebox.ordering.dto.CustomerResponse;
import com.spicebox.ordering.dto.RegisterRequest;
import com.spicebox.ordering.entity.Customer;
import com.spicebox.ordering.exception.DuplicateEmailException;
import com.spicebox.ordering.exception.ResourceNotFoundException;
import com.spicebox.ordering.mapper.OrderingMapper;
import com.spicebox.ordering.repository.CustomerRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final OrderingMapper orderingMapper;

    public CustomerService(CustomerRepository customerRepository,
                           PasswordEncoder passwordEncoder,
                           OrderingMapper orderingMapper) {
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.orderingMapper = orderingMapper;
    }

    @Transactional
    public CustomerResponse register(RegisterRequest request) {
        String email = request.getEmail().trim();
        if (customerRepository.existsByEmail(email)) {
            throw new DuplicateEmailException(email);
        }

        Customer customer = new Customer();
        customer.setEmail(email);
        customer.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        customer.setFullName(request.getFullName().trim());
        customer.setPhone(request.getPhone());
        customer.setDefaultAddress(request.getDefaultAddress().trim());
        customer.setRoleName("ROLE_CUSTOMER");

        return orderingMapper.toResponse(customerRepository.save(customer));
    }

    @Transactional(readOnly = true)
    public CustomerResponse findByEmail(String email) {
        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", email));
        return orderingMapper.toResponse(customer);
    }
}
