package com.athenaeum.lending.security;

import com.athenaeum.lending.repository.AppUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class LendingUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    public LendingUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return appUserRepository.findByUsername(username)
                .map(LendingUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("No account for " + username));
    }
}
