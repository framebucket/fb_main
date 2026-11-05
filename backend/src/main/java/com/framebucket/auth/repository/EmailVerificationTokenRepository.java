package com.framebucket.auth.repository;

import com.framebucket.auth.model.EmailVerificationToken;
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
