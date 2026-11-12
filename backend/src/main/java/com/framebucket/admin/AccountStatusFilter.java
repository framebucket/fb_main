package com.framebucket.admin;

import com.framebucket.user.auth.model.PhotographerPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Signs out photographers whose account an admin has suspended, even if they are already logged in.
 * The active flag is cached for 30 seconds per account to keep this cheap.
 */
@Component
public class AccountStatusFilter extends OncePerRequestFilter {
    private static final long CACHE_MILLIS = 30_000;

    private record Cached(boolean active, long checkedAt) {
    }

    private final JdbcTemplate jdbc;
    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();

    public AccountStatusFilter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof PhotographerPrincipal photographer
                && !isActive(photographer.getId())) {
            var session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":401,\"code\":\"ACCOUNT_SUSPENDED\",\"detail\":\"This account has been suspended. Contact FrameBucket support.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isActive(UUID id) {
        long now = System.currentTimeMillis();
        Cached cached = cache.get(id);
        if (cached != null && now - cached.checkedAt() < CACHE_MILLIS) {
            return cached.active();
        }
        Boolean active = jdbc.query("SELECT active FROM fb_user WHERE id = ?", (rs, n) -> rs.getBoolean(1), id)
                .stream().findFirst().orElse(false);
        cache.put(id, new Cached(active, now));
        return active;
    }

    /** Called after an admin suspends or reactivates an account so the change applies at once. */
    public void forget(UUID id) {
        cache.remove(id);
    }
}
