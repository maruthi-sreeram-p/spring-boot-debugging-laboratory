package com.athenaeum.lending.security;

import com.athenaeum.lending.entity.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class LendingUser implements UserDetails {

    private final String username;
    private final String passwordHash;
    private final String roleName;
    private final Long memberId;

    public LendingUser(AppUser user) {
        this.username = user.getUsername();
        this.passwordHash = user.getPasswordHash();
        this.roleName = user.getRoleName();
        this.memberId = user.getMemberId();
    }

    public Long getMemberId() {
        return memberId;
    }

    public boolean isLibrarian() {
        return "ROLE_LIBRARIAN".equals(roleName);
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
        return username;
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
