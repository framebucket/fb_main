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
    private final com.framebucket.admin.AdminAccess adminAccess;

    public PhotographerUserDetailsService(PhotographerUserRepository userRepository,
                                          com.framebucket.admin.AdminAccess adminAccess) {
        this.userRepository = userRepository;
        this.adminAccess = adminAccess;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String identifier = username.trim();
        if (IndianMobileNumber.isValidInput(identifier)) {
            var matchingUsers = userRepository.findAllByMobileNumber(IndianMobileNumber.normalize(identifier));
            if (matchingUsers.size() == 1) {
                return PhotographerPrincipal.from(matchingUsers.get(0), adminAccess.isAdmin(matchingUsers.get(0).getEmail()));
            }
            throw new UsernameNotFoundException("Invalid email or mobile number or password.");
        }

        return userRepository.findByEmailIgnoreCase(identifier)
                .map(user -> PhotographerPrincipal.from(user, adminAccess.isAdmin(user.getEmail())))
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or mobile number or password."));
    }
}
