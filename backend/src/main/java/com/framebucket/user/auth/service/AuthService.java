package com.framebucket.user.auth.service;

import com.framebucket.user.auth.api.AuthDtos.RegisterRequest;
import com.framebucket.user.auth.api.AuthDtos.UserResponse;
import com.framebucket.user.auth.model.PhotographerPrincipal;
import com.framebucket.user.auth.model.PhotographerUser;
import com.framebucket.user.auth.repository.PhotographerUserRepository;
import com.framebucket.user.subscription.model.SubscriptionPlan;
import com.framebucket.user.subscription.repository.SubscriptionPlanRepository;
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
    private final MobileVerificationService mobileVerificationService;
    private final SubscriptionPlanRepository subscriptionPlanRepository;

    public AuthService(
            PhotographerUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EmailVerificationService emailVerificationService,
            MobileVerificationService mobileVerificationService,
            SubscriptionPlanRepository subscriptionPlanRepository
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationService = emailVerificationService;
        this.mobileVerificationService = mobileVerificationService;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passwords do not match.");
        }
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
                request.mobileNumber(),
                passwordEncoder.encode(request.password())
        );
        user.startFreeTrial(trialPlan);

        user = userRepository.save(user);
        emailVerificationService.sendVerificationEmail(user);
        mobileVerificationService.sendCode(user);
        return toResponse(user);
    }

    @Transactional
    public String verifyEmail(String token) {
        return emailVerificationService.verifyToken(token);
    }

    @Transactional
    public boolean verifyMobile(String emailAddress, String code) {
        String email = emailAddress.trim().toLowerCase(Locale.ROOT);
        return userRepository.findByEmailIgnoreCase(email)
                .map(user -> mobileVerificationService.verifyCode(user, code))
                .orElse(false);
    }

    @Transactional
    public void resendMobileCode(String emailAddress) {
        String email = emailAddress.trim().toLowerCase(Locale.ROOT);
        userRepository.findByEmailIgnoreCase(email)
                .filter(user -> !user.isMobileVerified())
                .ifPresent(mobileVerificationService::sendCode);
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
                principal.getMobileNumber(),
                principal.isEmailVerified(),
                principal.isMobileVerified(),
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
                user.getMobileNumber(),
                user.isEmailVerified(),
                user.isMobileVerified(),
                user.getCreatedAt(),
                user.getSubscription() == null ? null : user.getSubscription().getId(),
                user.getSubscription() == null ? null : user.getSubscription().getPlanCode(),
                user.getSubscription() == null ? null : user.getSubscription().getPlanName(),
                user.getTrialEndsAt(),
                user.getSubscription() != null && user.getSubscription().isDataModuleEnabled()
        );
    }
}
