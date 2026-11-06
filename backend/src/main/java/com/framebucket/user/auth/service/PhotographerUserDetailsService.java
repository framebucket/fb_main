package com.framebucket.user.auth.service;

import com.framebucket.user.auth.model.PhotographerPrincipal;
import com.framebucket.user.auth.repository.PhotographerUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PhotographerUserDetailsService implements UserDetailsService {
    private final PhotographerUserRepository userRepository;

    public PhotographerUserDetailsService(PhotographerUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmailIgnoreCase(email.trim())
                .map(PhotographerPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password."));
    }
}
