package com.northwind.shop.security;

import com.northwind.shop.entity.Customer;
import com.northwind.shop.entity.CustomerStatus;
import com.northwind.shop.entity.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class AuthenticatedCustomer implements UserDetails {

    private final Long customerId;
    private final String email;
    private final String passwordHash;
    private final boolean enabled;
    private final List<GrantedAuthority> authorities;

    public AuthenticatedCustomer(Customer customer) {
        this.customerId = customer.getId();
        this.email = customer.getEmail();
        this.passwordHash = customer.getPasswordHash();
        this.enabled = customer.getStatus() == CustomerStatus.ACTIVE;
        this.authorities = customer.getRoles().stream()
                .map(Role::getName)
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    public Long getCustomerId() {
        return customerId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
