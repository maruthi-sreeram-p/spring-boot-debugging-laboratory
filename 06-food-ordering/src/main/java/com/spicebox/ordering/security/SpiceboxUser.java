package com.spicebox.ordering.security;

import com.spicebox.ordering.entity.Customer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class SpiceboxUser implements UserDetails {

    private final Long customerId;
    private final String email;
    private final String passwordHash;
    private final String roleName;

    public SpiceboxUser(Customer customer) {
        this.customerId = customer.getId();
        this.email = customer.getEmail();
        this.passwordHash = customer.getPasswordHash();
        this.roleName = customer.getRoleName();
    }

    public Long getCustomerId() {
        return customerId;
    }

    public String getRoleName() {
        return roleName;
    }

    public boolean isOperations() {
        return "ROLE_OPS".equals(roleName);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(roleName));
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
        return true;
    }
}
