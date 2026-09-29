package com.pulsesend.notifications.security;

import com.pulsesend.notifications.entity.AppUser;
import com.pulsesend.notifications.repository.AppUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PulsesendUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    public PulsesendUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("No Pulsesend account for " + username));
        return new PulsesendUser(user);
    }
}
