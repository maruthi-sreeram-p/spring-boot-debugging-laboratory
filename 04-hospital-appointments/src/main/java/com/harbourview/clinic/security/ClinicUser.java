package com.harbourview.clinic.security;

import com.harbourview.clinic.entity.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class ClinicUser implements UserDetails {

    private final String username;
    private final String passwordHash;
    private final String roleName;
    private final Long patientId;
    private final Long doctorId;
    private final boolean enabled;

    public ClinicUser(AppUser user) {
        this.username = user.getUsername();
        this.passwordHash = user.getPasswordHash();
        this.roleName = user.getRoleName();
        this.patientId = user.getPatientId();
        this.doctorId = user.getDoctorId();
        this.enabled = user.isEnabled();
    }

    public Long getPatientId() {
        return patientId;
    }

    public Long getDoctorId() {
        return doctorId;
    }

    public String getRoleName() {
        return roleName;
    }

    public boolean isPatient() {
        return "ROLE_PATIENT".equals(roleName);
    }

    public boolean isDoctor() {
        return "ROLE_DOCTOR".equals(roleName);
    }

    public boolean isSchedulingDesk() {
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
        return enabled;
    }
}
