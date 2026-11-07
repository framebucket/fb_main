package com.framebucket.user.auth.service;

import com.framebucket.user.auth.model.PhotographerPrincipal;
import com.framebucket.user.auth.repository.PhotographerUserRepository;
import com.framebucket.common.phone.IndianMobileNumber;
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
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String identifier = username.trim();
        if (IndianMobileNumber.isValidInput(identifier)) {
            var matchingUsers = userRepository.findAllByMobileNumber(IndianMobileNumber.normalize(identifier));
            if (matchingUsers.size() == 1) {
                return PhotographerPrincipal.from(matchingUsers.get(0));
            }
            throw new UsernameNotFoundException("Invalid email or mobile number or password.");
        }

        return userRepository.findByEmailIgnoreCase(identifier)
                .map(PhotographerPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or mobile number or password."));
    }
}
