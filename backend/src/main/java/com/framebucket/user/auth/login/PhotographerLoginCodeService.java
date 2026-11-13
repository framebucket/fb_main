package com.framebucket.user.auth.login;

import com.framebucket.common.phone.IndianMobileNumber;
import com.framebucket.customer.email.CustomerLoginOtpEmailSender;
import com.framebucket.admin.AdminAccess;
import com.framebucket.user.auth.model.PhotographerPrincipal;
import com.framebucket.user.auth.model.PhotographerUser;
import com.framebucket.user.auth.repository.PhotographerUserRepository;
import com.framebucket.user.auth.sms.MobileOtpSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Passwordless sign-in for photographers: a 6-digit code on WhatsApp (and a copy by email, in case WhatsApp is
 * slow). Codes last 10 minutes, allow 5 attempts and can be re-sent after 60 seconds. A correct code also proves
 * the mobile number, so it marks the number as verified.
 */
@Service
public class PhotographerLoginCodeService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PhotographerLoginCodeService.class);
    private static final Duration CODE_LIFETIME = Duration.ofMinutes(10);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    public enum Result { SENT, COOLDOWN, NOT_FOUND }

    public record CodeRequest(Result result, String maskedMobile) {
    }

    private final JdbcTemplate jdbc;
    private final PhotographerUserRepository users;
    private final MobileOtpSender whatsApp;
    private final CustomerLoginOtpEmailSender email;
    private final byte[] secret;
    private final AdminAccess adminAccess;

    public PhotographerLoginCodeService(JdbcTemplate jdbc, PhotographerUserRepository users, MobileOtpSender whatsApp,
                                        CustomerLoginOtpEmailSender email, AdminAccess adminAccess,
                                        @Value("${app.verification.otp-hmac-secret}") String secret) {
        this.jdbc = jdbc;
        this.users = users;
        this.whatsApp = whatsApp;
        this.email = email;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.adminAccess = adminAccess;
    }

    /** The account for a mobile number or email address, if exactly one matches. */
    public Optional<PhotographerUser> find(String identifier) {
        String value = identifier == null ? "" : identifier.trim();
        if (IndianMobileNumber.isValidInput(value)) {
            List<PhotographerUser> matches = users.findAllByMobileNumber(IndianMobileNumber.normalize(value));
            return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
        }
        return value.contains("@") ? users.findByEmailIgnoreCase(value) : Optional.empty();
    }

    @Transactional
    public CodeRequest requestCode(String identifier) {
        PhotographerUser user = find(identifier).orElse(null);
        if (user == null || user.getMobileNumber() == null) return new CodeRequest(Result.NOT_FOUND, null);
        return send(user);
    }

    @Transactional
    public CodeRequest send(PhotographerUser user) {
        Instant now = Instant.now();
        Timestamp latest = jdbc.query("""
                SELECT created_at FROM fb_photographer_login_otp
                WHERE user_id = ? AND consumed_at IS NULL ORDER BY created_at DESC LIMIT 1
                """, (rs, n) -> rs.getTimestamp(1), user.getId()).stream().findFirst().orElse(null);
        if (latest != null && latest.toInstant().isAfter(now.minus(RESEND_COOLDOWN))) {
            return new CodeRequest(Result.COOLDOWN, mask(user.getMobileNumber()));
        }
        jdbc.update("DELETE FROM fb_photographer_login_otp WHERE user_id = ? AND consumed_at IS NULL", user.getId());
        String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        jdbc.update("INSERT INTO fb_photographer_login_otp (id, user_id, code_hash, expires_at) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), user.getId(), hash(code), Timestamp.from(now.plus(CODE_LIFETIME)));
        whatsApp.sendVerificationCode(user.getMobileNumber(), code);
        try {
            email.sendLoginCode(user.getEmail(), code);
        } catch (RuntimeException exception) {
            // The WhatsApp code is what matters; the email copy is a convenience.
            LOGGER.warn("Could not email the sign-in code to {}: {}", user.getEmail(), exception.getMessage());
        }
        return new CodeRequest(Result.SENT, mask(user.getMobileNumber()));
    }

    /** The signed-in principal if the code is right, else empty. */
    @Transactional
    public Optional<PhotographerPrincipal> verifyPrincipal(String identifier, String code) {
        return verify(identifier, code).map(this::principalOf);
    }

    /** The principal for a remembered app sign-in; loaded in a transaction so the plan details are available. */
    @Transactional(readOnly = true)
    public Optional<PhotographerPrincipal> principal(UUID userId) {
        return users.findById(userId).map(this::principalOf);
    }

    private PhotographerPrincipal principalOf(PhotographerUser user) {
        return PhotographerPrincipal.from(user, adminAccess.isAdmin(user.getEmail()));
    }

    /** The signed-in account if the code is right, else empty. */
    @Transactional
    public Optional<PhotographerUser> verify(String identifier, String code) {
        PhotographerUser user = find(identifier).orElse(null);
        if (user == null || code == null || !code.matches("\\d{6}")) return Optional.empty();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, code_hash, attempt_count FROM fb_photographer_login_otp
                WHERE user_id = ? AND consumed_at IS NULL AND expires_at > CURRENT_TIMESTAMP
                ORDER BY created_at DESC LIMIT 1 FOR UPDATE
                """, user.getId());
        if (rows.isEmpty()) return Optional.empty();
        Map<String, Object> row = rows.get(0);
        UUID id = (UUID) row.get("id");
        if (((Number) row.get("attempt_count")).intValue() >= MAX_ATTEMPTS) return Optional.empty();
        if (!MessageDigest.isEqual(hash(code).getBytes(StandardCharsets.UTF_8),
                String.valueOf(row.get("code_hash")).getBytes(StandardCharsets.UTF_8))) {
            jdbc.update("UPDATE fb_photographer_login_otp SET attempt_count = attempt_count + 1 WHERE id = ?", id);
            return Optional.empty();
        }
        jdbc.update("UPDATE fb_photographer_login_otp SET consumed_at = CURRENT_TIMESTAMP WHERE id = ?", id);
        PhotographerUser managed = users.findById(user.getId()).orElseThrow();
        if (!managed.isMobileVerified()) managed.verifyMobileNumber();
        return Optional.of(managed);
    }

    static String mask(String mobile) {
        if (mobile == null || mobile.length() < 4) return mobile;
        return "+91 ••••• •" + mobile.substring(mobile.length() - 4);
    }

    private String hash(String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(("login:" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable.", exception);
        }
    }
}
