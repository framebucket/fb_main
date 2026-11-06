package com.framebucket.user.auth.repository;

import com.framebucket.user.auth.model.MobileVerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface MobileVerificationCodeRepository extends JpaRepository<MobileVerificationCode, UUID> {
    void deleteAllByUser_IdAndConsumedAtIsNull(UUID userId);

    Optional<MobileVerificationCode> findFirstByUser_IdAndConsumedAtIsNullOrderByCreatedAtDesc(UUID userId);

    Optional<MobileVerificationCode> findFirstByUser_IdAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            UUID userId,
            Instant now
    );
}
