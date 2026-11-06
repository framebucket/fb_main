package com.framebucket.user.auth.service;

import com.framebucket.user.auth.model.MobileVerificationCode;
import com.framebucket.user.auth.model.PhotographerUser;
import com.framebucket.user.auth.repository.MobileVerificationCodeRepository;
import com.framebucket.user.auth.sms.MobileOtpSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class MobileVerificationService {
    private static final Duration CODE_LIFETIME = Duration.ofMinutes(10);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final MobileVerificationCodeRepository codeRepository;
    private final MobileOtpSender otpSender;
    private final byte[] hmacSecret;

    public MobileVerificationService(
            MobileVerificationCodeRepository codeRepository,
            MobileOtpSender otpSender,
            @Value("${app.verification.otp-hmac-secret}") String hmacSecret
    ) {
        this.codeRepository = codeRepository;
        this.otpSender = otpSender;
        this.hmacSecret = hmacSecret.getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public void sendCode(PhotographerUser user) {
        if (user.isMobileVerified() || user.getMobileNumber() == null) {
            return;
        }

        Instant now = Instant.now();
        boolean recentlySent = codeRepository
                .findFirstByUser_IdAndConsumedAtIsNullOrderByCreatedAtDesc(user.getId())
                .map(existing -> existing.getCreatedAt().isAfter(now.minus(RESEND_COOLDOWN)))
                .orElse(false);
        if (recentlySent) {
            return;
        }

        codeRepository.deleteAllByUser_IdAndConsumedAtIsNull(user.getId());
        String code = String.format(Locale.ROOT, "%06d", SECURE_RANDOM.nextInt(1_000_000));
        codeRepository.save(new MobileVerificationCode(user, hash(code), now.plus(CODE_LIFETIME)));
        otpSender.sendVerificationCode(user.getMobileNumber(), code);
    }

    @Transactional
    public boolean verifyCode(PhotographerUser user, String submittedCode) {
        if (user.isMobileVerified()) {
            return true;
        }

        Instant now = Instant.now();
        MobileVerificationCode code = codeRepository
                .findFirstByUser_IdAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(user.getId(), now)
                .orElse(null);
        if (code == null || code.getAttemptCount() >= 5) {
            return false;
        }

        if (!MessageDigest.isEqual(
                hash(submittedCode).getBytes(StandardCharsets.UTF_8),
                code.getCodeHash().getBytes(StandardCharsets.UTF_8)
        )) {
            code.recordFailedAttempt();
            return false;
        }

        user.verifyMobileNumber();
        code.consume(now);
        return true;
    }

    private String hash(String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(code.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable.", exception);
        }
    }
}
