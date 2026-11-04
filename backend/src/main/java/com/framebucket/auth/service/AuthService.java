package com.framebucket.auth.service;

import com.framebucket.auth.api.AuthDtos.RegisterRequest;
import com.framebucket.auth.api.AuthDtos.UserResponse;
import com.framebucket.auth.model.PhotographerPrincipal;
import com.framebucket.auth.model.PhotographerUser;
import com.framebucket.auth.repository.PhotographerUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class AuthService {
    private final PhotographerUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(PhotographerUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password must be no more than 72 UTF-8 bytes."
            );
        }

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }

        PhotographerUser user = new PhotographerUser(
                request.fullName(),
                request.studioName(),
                email,
                passwordEncoder.encode(request.password())
        );

        return toResponse(userRepository.save(user));
    }

    public UserResponse toResponse(PhotographerPrincipal principal) {
        return new UserResponse(
                principal.getId(),
                principal.getFullName(),
                principal.getStudioName(),
                principal.getUsername(),
                principal.getCreatedAt()
        );
    }

    private UserResponse toResponse(PhotographerUser user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getStudioName(),
                user.getEmail(),
                user.getCreatedAt()
        );
    }
}
