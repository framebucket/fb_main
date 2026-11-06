package com.framebucket.user.auth.service;

import com.framebucket.user.auth.email.VerificationEmailSender;
import com.framebucket.user.auth.model.EmailVerificationToken;
import com.framebucket.user.auth.model.PhotographerUser;
import com.framebucket.user.auth.repository.EmailVerificationTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class EmailVerificationService {
    private static final Duration TOKEN_LIFETIME = Duration.ofHours(24);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final EmailVerificationTokenRepository tokenRepository;
    private final VerificationEmailSender emailSender;
    private final String frontendBaseUrl;

    public EmailVerificationService(
            EmailVerificationTokenRepository tokenRepository,
            VerificationEmailSender emailSender,
            @Value("${app.frontend.base-url}") String frontendBaseUrl
    ) {
        this.tokenRepository = tokenRepository;
        this.emailSender = emailSender;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public void sendVerificationEmail(PhotographerUser user) {
        tokenRepository.deleteAllByUser_IdAndConsumedAtIsNull(user.getId());

        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        Instant expiresAt = Instant.now().plus(TOKEN_LIFETIME);

        tokenRepository.save(new EmailVerificationToken(user, hash(rawToken), expiresAt));
        emailSender.sendVerificationEmail(
                user.getEmail(),
                frontendBaseUrl + "/verify-email?token=" + rawToken
        );
    }

    @Transactional
    public String verifyToken(String rawToken) {
        EmailVerificationToken verificationToken = tokenRepository
                .findByTokenHashAndConsumedAtIsNullAndExpiresAtAfter(hash(rawToken), Instant.now())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "This verification link is invalid or expired. Request a new link and try again."
                ));

        String email = verificationToken.getUser().getEmail();
        verificationToken.getUser().verifyEmail();
        verificationToken.consume(Instant.now());
        return email;
    }

    private static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
