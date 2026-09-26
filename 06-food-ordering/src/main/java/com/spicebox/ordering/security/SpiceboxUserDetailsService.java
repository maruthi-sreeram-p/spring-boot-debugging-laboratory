package com.spicebox.ordering.security;

import com.spicebox.ordering.entity.Customer;
import com.spicebox.ordering.repository.CustomerRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SpiceboxUserDetailsService implements UserDetailsService {

    private final CustomerRepository customerRepository;

    public SpiceboxUserDetailsService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No Spicebox account for " + email));
        return new SpiceboxUser(customer);
    }
}
