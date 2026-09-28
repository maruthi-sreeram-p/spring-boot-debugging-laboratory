package com.ledgerline.payments.security;

import com.ledgerline.payments.entity.AppUser;
import com.ledgerline.payments.repository.AppUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentsUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    public PaymentsUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("No Ledgerline account for " + username));
        return new PaymentsUser(user);
    }
}
