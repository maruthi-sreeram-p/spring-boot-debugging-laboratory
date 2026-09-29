package com.riverstone.orderevents.security;

import com.riverstone.orderevents.entity.AppUser;
import com.riverstone.orderevents.repository.AppUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PipelineUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    public PipelineUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("No Riverstone account for " + username));
        return new PipelineUser(user);
    }
}
