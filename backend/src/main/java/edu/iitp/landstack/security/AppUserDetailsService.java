package edu.iitp.landstack.security;

import edu.iitp.landstack.users.AppUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserDetailsService implements UserDetailsService {
    private final AppUserRepository users;

    public AppUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        String email = username == null ? "" : username.strip().toLowerCase();
        return users.findByEmail(email).map(AppUserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("not found"));
    }
}
