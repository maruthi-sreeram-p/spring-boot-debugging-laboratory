package com.northwind.shop.service;

import com.northwind.shop.dto.CustomerResponse;
import com.northwind.shop.dto.RegisterRequest;
import com.northwind.shop.entity.Customer;
import com.northwind.shop.entity.CustomerStatus;
import com.northwind.shop.entity.Role;
import com.northwind.shop.exception.DuplicateEmailException;
import com.northwind.shop.exception.ResourceNotFoundException;
import com.northwind.shop.mapper.CustomerMapper;
import com.northwind.shop.repository.CustomerRepository;
import com.northwind.shop.repository.RoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);
    private static final String DEFAULT_ROLE = "ROLE_CUSTOMER";

    private final CustomerRepository customerRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final CustomerMapper customerMapper;

    public CustomerService(CustomerRepository customerRepository,
                           RoleRepository roleRepository,
                           PasswordEncoder passwordEncoder,
                           CustomerMapper customerMapper) {
        this.customerRepository = customerRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.customerMapper = customerMapper;
    }

    @Transactional
    public CustomerResponse register(RegisterRequest request) {
        String email = request.getEmail().trim();
        if (customerRepository.existsByEmail(email)) {
            throw new DuplicateEmailException(email);
        }

        Role defaultRole = roleRepository.findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new IllegalStateException("Default role " + DEFAULT_ROLE + " is not provisioned"));

        Customer customer = new Customer();
        customer.setEmail(email);
        customer.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        customer.setFullName(request.getFullName().trim());
        customer.setPhone(request.getPhone());
        customer.setStatus(CustomerStatus.ACTIVE);
        customer.getRoles().add(defaultRole);

        Customer saved = customerRepository.save(customer);
        log.info("Registered customer {} ({})", saved.getId(), saved.getEmail());
        return customerMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public CustomerResponse findByEmail(String email) {
        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", email));
        return customerMapper.toResponse(customer);
    }
}
