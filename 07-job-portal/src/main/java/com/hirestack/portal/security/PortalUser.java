package com.hirestack.portal.security;

import com.hirestack.portal.entity.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class PortalUser implements UserDetails {

    private final Long userId;
    private final String email;
    private final String passwordHash;
    private final String roleName;
    private final Long companyId;
    private final Long candidateId;
    private final boolean enabled;

    public PortalUser(AppUser user) {
        this.userId = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.roleName = user.getRoleName();
        this.companyId = user.getCompanyId();
        this.candidateId = user.getCandidateId();
        this.enabled = user.isEnabled();
    }

    public Long getUserId() {
        return userId;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public Long getCandidateId() {
        return candidateId;
    }

    public String getRoleName() {
        return roleName;
    }

    public boolean isRecruiter() {
        return "ROLE_RECRUITER".equals(roleName);
    }

    public boolean isAdmin() {
        return "ROLE_ADMIN".equals(roleName);
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
        return enabled;
    }
}
