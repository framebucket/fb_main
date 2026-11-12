package com.framebucket.delivery;

import com.framebucket.delivery.DeliveryDtos.BrandRequest;
import com.framebucket.delivery.DeliveryDtos.BrandResponse;
import com.framebucket.delivery.DeliveryDtos.BrandView;
import com.framebucket.delivery.DeliveryDtos.DomainResolution;
import com.framebucket.subscription.EntitlementService;
import com.framebucket.subscription.Feature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Studio branding (logo + colour), watermark settings and custom domains. */
@Service
public class BrandService {
    public record Settings(UUID photographerId, String brandColor, boolean hasLogo, Instant logoUpdatedAt, String watermarkMode,
                           String watermarkText, boolean watermarkUseLogo, String watermarkPosition, int watermarkOpacity,
                           String customDomain, Instant customDomainVerifiedAt, Instant updatedAt) {
        public long version() {
            return updatedAt == null ? 0 : updatedAt.toEpochMilli();
        }
    }

    public record Logo(byte[] bytes, String contentType, Instant updatedAt) {
    }

    private static final long MAX_LOGO_BYTES = 1_048_576;
    private static final java.util.regex.Pattern DOMAIN = java.util.regex.Pattern.compile(
            "^(?=.{4,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$");

    private final JdbcTemplate jdbc;
    private final EntitlementService entitlements;
    private final String appHost;
    private final String domainTarget;

    public BrandService(JdbcTemplate jdbc, EntitlementService entitlements,
                        @Value("${app.frontend.base-url}") String frontendBaseUrl,
                        @Value("${app.custom-domains.target:}") String domainTarget) {
        this.jdbc = jdbc;
        this.entitlements = entitlements;
        String host;
        try {
            host = URI.create(frontendBaseUrl).getHost();
        } catch (RuntimeException exception) {
            host = null;
        }
        this.appHost = host == null ? "" : host.toLowerCase(Locale.ROOT);
        this.domainTarget = domainTarget == null || domainTarget.isBlank() ? this.appHost : domainTarget.trim().toLowerCase(Locale.ROOT);
    }

    public Settings settings(UUID photographerId) {
        return jdbc.query("""
                        SELECT photographer_id, brand_color, logo IS NOT NULL AS has_logo, logo_updated_at, watermark_mode, watermark_text,
                               watermark_use_logo, watermark_position, watermark_opacity, custom_domain, custom_domain_verified_at, updated_at
                        FROM fb_studio_brand WHERE photographer_id = ?
                        """, (rs, n) -> new Settings(photographerId, rs.getString("brand_color"), rs.getBoolean("has_logo"),
                        ts(rs.getTimestamp("logo_updated_at")), rs.getString("watermark_mode"), rs.getString("watermark_text"),
                        rs.getBoolean("watermark_use_logo"), rs.getString("watermark_position"), rs.getInt("watermark_opacity"),
                        rs.getString("custom_domain"), ts(rs.getTimestamp("custom_domain_verified_at")), ts(rs.getTimestamp("updated_at"))),
                photographerId).stream().findFirst()
                .orElse(new Settings(photographerId, null, false, null, "OFF", null, false, "TILED", 30, null, null, null));
    }

    public BrandView brandView(UUID photographerId) {
        String name = studioName(photographerId);
        if (!entitlements.has(photographerId, Feature.CUSTOM_BRANDING)) return new BrandView(name, null, null, true);
        Settings settings = settings(photographerId);
        return new BrandView(name, settings.brandColor(), logoUrl(settings), false);
    }

    @Transactional(readOnly = true)
    public BrandResponse response(UUID photographerId) {
        Settings s = settings(photographerId);
        return new BrandResponse(studioName(photographerId), s.brandColor(), logoUrl(s), s.watermarkMode(), s.watermarkText(),
                s.watermarkUseLogo(), s.watermarkPosition(), s.watermarkOpacity(), s.customDomain(), s.customDomainVerifiedAt(),
                domainTarget, entitlements.has(photographerId, Feature.CUSTOM_BRANDING),
                entitlements.has(photographerId, Feature.WATERMARK), entitlements.has(photographerId, Feature.CUSTOM_DOMAIN));
    }

    @Transactional
    public BrandResponse update(UUID photographerId, BrandRequest request) {
        String color = request.brandColor() == null || request.brandColor().isBlank() ? null : request.brandColor().toUpperCase(Locale.ROOT);
        if (color != null) entitlements.require(photographerId, Feature.CUSTOM_BRANDING);
        if (!"OFF".equals(request.watermarkMode())) entitlements.require(photographerId, Feature.WATERMARK);
        ensureRow(photographerId);
        jdbc.update("""
                        UPDATE fb_studio_brand SET brand_color = ?, watermark_mode = ?, watermark_text = ?, watermark_use_logo = ?,
                            watermark_position = ?, watermark_opacity = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE photographer_id = ?
                        """, color, request.watermarkMode(), blankToNull(request.watermarkText()), request.watermarkUseLogo(),
                request.watermarkPosition(), request.watermarkOpacity(), photographerId);
        return response(photographerId);
    }

    @Transactional
    public BrandResponse uploadLogo(UUID photographerId, MultipartFile file) {
        if (!entitlements.has(photographerId, Feature.CUSTOM_BRANDING)) entitlements.require(photographerId, Feature.WATERMARK);
        if (file == null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a logo file.");
        if (file.getSize() > MAX_LOGO_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "The logo must be 1 MB or smaller.");
        byte[] bytes;
        try {
            bytes = file.getBytes();
            if (ImageIO.read(new ByteArrayInputStream(bytes)) == null) throw new IOException("unreadable");
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Upload a PNG or JPG logo.");
        }
        String type = bytes.length > 3 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' ? "image/png" : "image/jpeg";
        ensureRow(photographerId);
        jdbc.update("UPDATE fb_studio_brand SET logo = ?, logo_content_type = ?, logo_updated_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE photographer_id = ?",
                bytes, type, photographerId);
        return response(photographerId);
    }

    @Transactional
    public BrandResponse deleteLogo(UUID photographerId) {
        jdbc.update("UPDATE fb_studio_brand SET logo = NULL, logo_content_type = NULL, logo_updated_at = NULL, watermark_use_logo = FALSE, updated_at = CURRENT_TIMESTAMP WHERE photographer_id = ?",
                photographerId);
        return response(photographerId);
    }

    public Optional<Logo> logo(UUID photographerId) {
        return jdbc.query("SELECT logo, logo_content_type, logo_updated_at FROM fb_studio_brand WHERE photographer_id = ? AND logo IS NOT NULL",
                (rs, n) -> new Logo(rs.getBytes(1), rs.getString(2), ts(rs.getTimestamp(3))), photographerId).stream().findFirst();
    }

    // ---- custom domain ------------------------------------------------------------------------------

    @Transactional
    public BrandResponse setDomain(UUID photographerId, String rawDomain) {
        entitlements.require(photographerId, Feature.CUSTOM_DOMAIN);
        String domain = normalizeDomain(rawDomain);
        if (domain.equals(appHost) || domain.endsWith("." + appHost) && !appHost.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Use a domain you own, such as photos.yourstudio.com.");
        }
        String slug = jdbc.query("SELECT portfolio_slug FROM fb_studio_profile WHERE photographer_id = ?", (rs, n) -> rs.getString(1), photographerId)
                .stream().findFirst().orElse(null);
        if (slug == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Choose your portfolio address in Settings first; your domain will show that page.");
        }
        ensureRow(photographerId);
        try {
            jdbc.update("UPDATE fb_studio_brand SET custom_domain = ?, custom_domain_verified_at = NULL, updated_at = CURRENT_TIMESTAMP WHERE photographer_id = ?",
                    domain, photographerId);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That domain is already connected to another studio.");
        }
        return response(photographerId);
    }

    @Transactional
    public BrandResponse verifyDomain(UUID photographerId) {
        entitlements.require(photographerId, Feature.CUSTOM_DOMAIN);
        Settings settings = settings(photographerId);
        if (settings.customDomain() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Add your domain first.");
        Set<String> domainIps = resolve(settings.customDomain());
        Set<String> targetIps = resolve(domainTarget);
        if (domainIps.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "We can't find DNS records for " + settings.customDomain() + " yet. DNS changes can take up to an hour.");
        }
        if (targetIps.isEmpty() || domainIps.stream().noneMatch(targetIps::contains)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    settings.customDomain() + " doesn't point to FrameBucket yet. Add a CNAME record to " + domainTarget + " and try again.");
        }
        jdbc.update("UPDATE fb_studio_brand SET custom_domain_verified_at = CURRENT_TIMESTAMP WHERE photographer_id = ?", photographerId);
        return response(photographerId);
    }

    @Transactional
    public BrandResponse removeDomain(UUID photographerId) {
        jdbc.update("UPDATE fb_studio_brand SET custom_domain = NULL, custom_domain_verified_at = NULL, updated_at = CURRENT_TIMESTAMP WHERE photographer_id = ?",
                photographerId);
        return response(photographerId);
    }

    /** Which studio a verified custom domain belongs to (used for the site root and TLS issuance). */
    public Optional<DomainResolution> resolveHost(String host) {
        if (host == null || host.isBlank()) return Optional.empty();
        String domain = host.toLowerCase(Locale.ROOT).replaceFirst(":\\d+$", "");
        return jdbc.query("""
                        SELECT b.photographer_id, p.portfolio_slug, COALESCE(NULLIF(u.studio_name, ''), u.full_name)
                        FROM fb_studio_brand b
                        JOIN fb_user u ON u.id = b.photographer_id
                        JOIN fb_studio_profile p ON p.photographer_id = b.photographer_id
                        WHERE b.custom_domain = ? AND b.custom_domain_verified_at IS NOT NULL AND p.portfolio_slug IS NOT NULL
                        """, (rs, n) -> new Object[]{rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)}, domain)
                .stream().findFirst()
                .filter(row -> entitlements.has((UUID) row[0], Feature.CUSTOM_DOMAIN))
                .map(row -> new DomainResolution((String) row[1], (String) row[2]));
    }

    String studioName(UUID photographerId) {
        return jdbc.query("SELECT COALESCE(NULLIF(studio_name, ''), full_name) FROM fb_user WHERE id = ?",
                (rs, n) -> rs.getString(1), photographerId).stream().findFirst().orElse("");
    }

    private void ensureRow(UUID photographerId) {
        jdbc.update("INSERT INTO fb_studio_brand (photographer_id) VALUES (?) ON CONFLICT (photographer_id) DO NOTHING", photographerId);
    }

    private static String logoUrl(Settings settings) {
        return settings.hasLogo() ? "/api/public/brand/" + settings.photographerId() + "/logo?v="
                + (settings.logoUpdatedAt() == null ? 0 : settings.logoUpdatedAt().toEpochMilli()) : null;
    }

    static String normalizeDomain(String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT).replaceFirst("^[a-z]+://", "");
        int slash = value.indexOf('/');
        if (slash >= 0) value = value.substring(0, slash);
        value = value.replaceFirst(":\\d+$", "").replaceFirst("\\.$", "");
        if (!DOMAIN.matcher(value).matches()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Enter a domain like photos.yourstudio.com.");
        }
        return value;
    }

    private static Set<String> resolve(String host) {
        if (host == null || host.isBlank()) return Set.of();
        try {
            return Arrays.stream(InetAddress.getAllByName(host)).map(InetAddress::getHostAddress).collect(Collectors.toSet());
        } catch (UnknownHostException exception) {
            return Set.of();
        }
    }

    private static Instant ts(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
