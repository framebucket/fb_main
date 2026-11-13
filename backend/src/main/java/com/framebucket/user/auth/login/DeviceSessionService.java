package com.framebucket.user.auth.login;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remembered sign-ins for the FrameBucket apps (Windows/Mac, Android, iOS). The apps add
 * {@code FrameBucketApp/<version>} (mobile) or {@code FrameBucket/<version>} (desktop) to their user agent. After a
 * code sign-in from an app we set a long-lived, HttpOnly {@code FB_DEVICE} cookie. Its token is tied to the app
 * version, so the user signs in again only after installing an update, or after signing out.
 * Browsers never get this cookie and keep the normal session lifetime.
 */
@Service
public class DeviceSessionService {
    public static final String COOKIE = "FB_DEVICE";
    // Phone apps send "FrameBucketApp/<version>" (builds before 1.1 send a bare "FrameBucketApp"); desktop sends "FrameBucket/<version>".
    private static final Pattern APP_AGENT = Pattern.compile(
            "\\b(?:(FrameBucketApp)(?:/([0-9][0-9A-Za-z.\\-]{0,38}))?|(FrameBucket)/([0-9][0-9A-Za-z.\\-]{0,38}))");
    private static final Duration COOKIE_LIFETIME = Duration.ofDays(3650);
    private static final SecureRandom RANDOM = new SecureRandom();

    public enum Kind { PHOTOGRAPHER, CUSTOMER }

    public record App(String name, String version) {
    }

    public record DeviceSession(UUID id, Kind kind, UUID userId, String customerMobile) {
    }

    private final JdbcTemplate jdbc;

    public DeviceSessionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The FrameBucket app making this request, or empty for an ordinary browser. */
    public static Optional<App> app(HttpServletRequest request) {
        String agent = request.getHeader("User-Agent");
        if (agent == null) return Optional.empty();
        Matcher matcher = APP_AGENT.matcher(agent);
        if (!matcher.find()) return Optional.empty();
        if (matcher.group(1) != null) {
            String name = agent.contains("iPhone") || agent.contains("iPad") ? "ios" : "android";
            return Optional.of(new App(name, matcher.group(2) == null ? "0" : matcher.group(2)));
        }
        return Optional.of(new App("desktop", matcher.group(4)));
    }

    /** Remembers this app sign-in; does nothing for browsers. */
    public void remember(HttpServletRequest request, HttpServletResponse response, Kind kind, UUID userId, String customerMobile) {
        Optional<App> app = app(request);
        if (app.isEmpty()) return;
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        jdbc.update("""
                INSERT INTO fb_device_session (id, token_hash, principal_kind, user_id, customer_mobile, app_name, app_version)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), hash(token), kind.name(), userId, customerMobile, app.get().name(), app.get().version());
        response.addHeader("Set-Cookie", ResponseCookie.from(COOKIE, token)
                .httpOnly(true).secure(request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto")))
                .sameSite("Lax").path("/").maxAge(COOKIE_LIFETIME).build().toString());
    }

    /**
     * The remembered sign-in behind this request's cookie. A token from an older app version is revoked here, so an
     * update means signing in once more.
     */
    public Optional<DeviceSession> resolve(HttpServletRequest request) {
        String token = token(request);
        if (token == null) return Optional.empty();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, principal_kind, user_id, customer_mobile, app_version FROM fb_device_session
                WHERE token_hash = ? AND revoked_at IS NULL
                """, hash(token));
        if (rows.isEmpty()) return Optional.empty();
        Map<String, Object> row = rows.get(0);
        UUID id = (UUID) row.get("id");
        String version = app(request).map(App::version).orElse(null);
        if (version == null || !version.equals(row.get("app_version"))) {
            revoke(id);
            return Optional.empty();
        }
        jdbc.update("UPDATE fb_device_session SET last_used_at = CURRENT_TIMESTAMP WHERE id = ?", id);
        return Optional.of(new DeviceSession(id, Kind.valueOf((String) row.get("principal_kind")),
                (UUID) row.get("user_id"), (String) row.get("customer_mobile")));
    }

    public void revoke(UUID id) {
        jdbc.update("UPDATE fb_device_session SET revoked_at = CURRENT_TIMESTAMP WHERE id = ? AND revoked_at IS NULL", id);
    }

    /** Signs this device out: revokes the token and clears the cookie. */
    public void forget(HttpServletRequest request, HttpServletResponse response) {
        String token = token(request);
        if (token != null) {
            jdbc.update("UPDATE fb_device_session SET revoked_at = CURRENT_TIMESTAMP WHERE token_hash = ? AND revoked_at IS NULL", hash(token));
        }
        clearCookie(response);
    }

    public void clearCookie(HttpServletResponse response) {
        response.addHeader("Set-Cookie", ResponseCookie.from(COOKIE, "").httpOnly(true)
                .sameSite("Lax").path("/").maxAge(0).build().toString());
    }

    static String token(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (COOKIE.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
