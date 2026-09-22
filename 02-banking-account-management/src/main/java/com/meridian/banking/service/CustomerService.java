package com.meridian.banking.service;

import com.meridian.banking.dto.CustomerResponse;
import com.meridian.banking.dto.RegisterRequest;
import com.meridian.banking.entity.Customer;
import com.meridian.banking.entity.CustomerStatus;
import com.meridian.banking.entity.Role;
import com.meridian.banking.exception.DuplicateCustomerException;
import com.meridian.banking.exception.ResourceNotFoundException;
import com.meridian.banking.mapper.CustomerMapper;
import com.meridian.banking.repository.CustomerRepository;
import com.meridian.banking.repository.RoleRepository;
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
            throw new DuplicateCustomerException("An account already exists for " + email);
        }
        if (customerRepository.existsByNationalId(request.getNationalId())) {
            throw new DuplicateCustomerException("An account already exists for national id " + request.getNationalId());
        }

        Role defaultRole = roleRepository.findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new IllegalStateException("Default role " + DEFAULT_ROLE + " is not provisioned"));

        Customer customer = new Customer();
        customer.setEmail(email);
        customer.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        customer.setFullName(request.getFullName().trim());
        customer.setNationalId(request.getNationalId());
        customer.setStatus(CustomerStatus.ACTIVE);
        customer.getRoles().add(defaultRole);

        Customer saved = customerRepository.save(customer);
        log.info("Onboarded customer {} ({})", saved.getId(), saved.getEmail());
        return customerMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public CustomerResponse findByEmail(String email) {
        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", email));
        return customerMapper.toResponse(customer);
    }
}
