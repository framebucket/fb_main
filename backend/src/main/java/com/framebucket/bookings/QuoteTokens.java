package com.framebucket.bookings;

import com.framebucket.user.auth.service.SubIdEncryptionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Quote links: the raw token is hashed for lookups (as before) and also kept encrypted so FrameBucket can
 * re-send the same link in follow-ups and contract emails.
 */
@Component
public class QuoteTokens {
    public record EnquiryRef(UUID enquiryId, UUID photographerId) {
    }

    private final JdbcTemplate jdbc;
    private final SubIdEncryptionService encryption;
    private final String frontendBaseUrl;

    public QuoteTokens(JdbcTemplate jdbc, SubIdEncryptionService encryption,
                       @Value("${app.frontend.base-url}") String frontendBaseUrl) {
        this.jdbc = jdbc;
        this.encryption = encryption;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    public static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    public void remember(UUID enquiryId, String rawToken) {
        jdbc.update("UPDATE fb_enquiries SET quote_token_enc = ? WHERE id = ?", encryption.encrypt(rawToken), enquiryId);
    }

    public Optional<String> link(UUID enquiryId) {
        return jdbc.query("SELECT quote_token_enc FROM fb_enquiries WHERE id = ?", (rs, n) -> rs.getString(1), enquiryId)
                .stream().findFirst()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> {
                    try {
                        return frontendBaseUrl + "/quote/" + encryption.decrypt(value);
                    } catch (RuntimeException exception) {
                        return null;
                    }
                });
    }

    public EnquiryRef resolve(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 200) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Quotation link not found.");
        }
        return jdbc.query("SELECT id, photographer_id FROM fb_enquiries WHERE quote_token_hash = ?",
                        (rs, n) -> new EnquiryRef(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)), hash(rawToken))
                .stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quotation link not found."));
    }

    public void markOpened(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 200) return;
        jdbc.update("UPDATE fb_enquiries SET quote_opened_at = CURRENT_TIMESTAMP WHERE quote_token_hash = ? AND quote_opened_at IS NULL",
                hash(rawToken));
    }

    public String baseUrl() {
        return frontendBaseUrl;
    }
}
