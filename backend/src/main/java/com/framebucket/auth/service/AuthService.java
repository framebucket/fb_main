package com.framebucket.auth.service;

import com.framebucket.auth.api.AuthDtos.RegisterRequest;
import com.framebucket.auth.api.AuthDtos.UserResponse;
import com.framebucket.auth.model.PhotographerPrincipal;
import com.framebucket.auth.model.PhotographerUser;
import com.framebucket.auth.repository.PhotographerUserRepository;
import com.framebucket.subscription.model.SubscriptionPlan;
import com.framebucket.subscription.repository.SubscriptionPlanRepository;
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
    private final EmailVerificationService emailVerificationService;
    private final SubscriptionPlanRepository subscriptionPlanRepository;

    public AuthService(
            PhotographerUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EmailVerificationService emailVerificationService,
            SubscriptionPlanRepository subscriptionPlanRepository
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationService = emailVerificationService;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
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

        SubscriptionPlan trialPlan = subscriptionPlanRepository.findByPlanCodeAndActiveTrue("FREE_TRIAL")
                .orElseThrow(() -> new IllegalStateException("The free trial subscription has not been configured."));

        PhotographerUser user = new PhotographerUser(
                request.fullName(),
                request.studioName(),
                email,
                passwordEncoder.encode(request.password())
        );
        user.startFreeTrial(trialPlan);

        user = userRepository.save(user);
        emailVerificationService.sendVerificationEmail(user);
        return toResponse(user);
    }

    @Transactional
    public void verifyEmail(String token) {
        emailVerificationService.verifyToken(token);
    }

    @Transactional
    public void resendVerification(String emailAddress) {
        String email = emailAddress.trim().toLowerCase(Locale.ROOT);
        userRepository.findByEmailIgnoreCase(email)
                .filter(user -> !user.isEmailVerified())
                .ifPresent(emailVerificationService::sendVerificationEmail);
    }

    public UserResponse toResponse(PhotographerPrincipal principal) {
        return new UserResponse(
                principal.getId(),
                principal.getFullName(),
                principal.getStudioName(),
                principal.getUsername(),
                principal.getCreatedAt(),
                principal.getSubscriptionId(),
                principal.getSubscriptionCode(),
                principal.getSubscriptionName(),
                principal.getTrialEndsAt(),
                principal.isDataModuleEnabled()
        );
    }

    private UserResponse toResponse(PhotographerUser user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getStudioName(),
                user.getEmail(),
                user.getCreatedAt(),
                user.getSubscription() == null ? null : user.getSubscription().getId(),
                user.getSubscription() == null ? null : user.getSubscription().getPlanCode(),
                user.getSubscription() == null ? null : user.getSubscription().getPlanName(),
                user.getTrialEndsAt(),
                user.getSubscription() != null && user.getSubscription().isDataModuleEnabled()
        );
    }
}
