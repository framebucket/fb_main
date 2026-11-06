package com.framebucket.user.auth.repository;

import com.framebucket.user.auth.model.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {
    void deleteAllByUser_IdAndConsumedAtIsNull(UUID userId);

    Optional<EmailVerificationToken> findByTokenHashAndConsumedAtIsNullAndExpiresAtAfter(
            String tokenHash,
            Instant now
    );
}
