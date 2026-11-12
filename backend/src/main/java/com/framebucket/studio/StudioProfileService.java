package com.framebucket.studio;

import com.framebucket.studio.StudioDtos.StudioProfileRequest;
import com.framebucket.studio.StudioDtos.StudioProfileResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.UUID;

@Service
public class StudioProfileService {
    public record Profile(
            UUID photographerId,
            String upiId,
            String upiPayeeName,
            String businessAddress,
            String gstin,
            String receiptFooter,
            String portfolioSlug,
            String portfolioHeadline,
            String portfolioBio,
            boolean portfolioPublic,
            boolean remindersEnabled
    ) {
        public boolean upiConfigured() {
            return upiId != null && !upiId.isBlank();
        }
    }

    private final JdbcTemplate jdbc;

    public StudioProfileService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Profile profile(UUID photographerId) {
        return jdbc.query("SELECT * FROM fb_studio_profile WHERE photographer_id = ?", (rs, n) -> new Profile(
                        photographerId,
                        rs.getString("upi_id"),
                        rs.getString("upi_payee_name"),
                        rs.getString("business_address"),
                        rs.getString("gstin"),
                        rs.getString("receipt_footer"),
                        rs.getString("portfolio_slug"),
                        rs.getString("portfolio_headline"),
                        rs.getString("portfolio_bio"),
                        rs.getBoolean("portfolio_public"),
                        rs.getBoolean("reminders_enabled")
                ), photographerId)
                .stream().findFirst()
                .orElse(new Profile(photographerId, null, null, null, null, null, null, null, null, false, true));
    }

    @Transactional(readOnly = true)
    public StudioProfileResponse response(UUID photographerId) {
        return toResponse(profile(photographerId), studioName(photographerId));
    }

    @Transactional
    public StudioProfileResponse update(UUID photographerId, StudioProfileRequest request) {
        String slug = blankToNull(request.portfolioSlug());
        if (request.portfolioPublic() && slug == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Choose a portfolio address before publishing your portfolio.");
        }
        try {
            jdbc.update("""
                            INSERT INTO fb_studio_profile (photographer_id, upi_id, upi_payee_name, business_address, gstin,
                                receipt_footer, portfolio_slug, portfolio_headline, portfolio_bio, portfolio_public,
                                reminders_enabled, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                            ON CONFLICT (photographer_id) DO UPDATE SET
                                upi_id = EXCLUDED.upi_id, upi_payee_name = EXCLUDED.upi_payee_name,
                                business_address = EXCLUDED.business_address, gstin = EXCLUDED.gstin,
                                receipt_footer = EXCLUDED.receipt_footer, portfolio_slug = EXCLUDED.portfolio_slug,
                                portfolio_headline = EXCLUDED.portfolio_headline, portfolio_bio = EXCLUDED.portfolio_bio,
                                portfolio_public = EXCLUDED.portfolio_public, reminders_enabled = EXCLUDED.reminders_enabled,
                                updated_at = CURRENT_TIMESTAMP
                            """,
                    photographerId,
                    blankToNull(request.upiId()),
                    blankToNull(request.upiPayeeName()),
                    blankToNull(request.businessAddress()),
                    request.gstin() == null || request.gstin().isBlank() ? null : request.gstin().toUpperCase(Locale.ROOT),
                    blankToNull(request.receiptFooter()),
                    slug,
                    blankToNull(request.portfolioHeadline()),
                    blankToNull(request.portfolioBio()),
                    request.portfolioPublic(),
                    request.remindersEnabled());
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That portfolio address is already taken. Try another one.");
        }
        return response(photographerId);
    }

    /** Reserves the next receipt number for this photographer (safe under concurrent requests). */
    @Transactional
    public String nextReceiptNumber(UUID photographerId) {
        jdbc.update("INSERT INTO fb_studio_profile (photographer_id) VALUES (?) ON CONFLICT (photographer_id) DO NOTHING",
                photographerId);
        Integer sequence = jdbc.queryForObject("""
                UPDATE fb_studio_profile SET receipt_sequence = receipt_sequence + 1
                WHERE photographer_id = ? RETURNING receipt_sequence - 1
                """, Integer.class, photographerId);
        return String.format(Locale.ROOT, "RCPT-%05d", sequence == null ? 1 : sequence);
    }

    String studioName(UUID photographerId) {
        return jdbc.query("SELECT COALESCE(NULLIF(studio_name, ''), full_name) FROM fb_user WHERE id = ?",
                (rs, n) -> rs.getString(1), photographerId).stream().findFirst().orElse("");
    }

    private static StudioProfileResponse toResponse(Profile profile, String studioName) {
        return new StudioProfileResponse(profile.upiId(), profile.upiPayeeName(), profile.businessAddress(), profile.gstin(),
                profile.receiptFooter(), profile.portfolioSlug(), profile.portfolioHeadline(), profile.portfolioBio(),
                profile.portfolioPublic(), profile.remindersEnabled(), studioName);
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
