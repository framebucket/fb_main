package com.framebucket.delivery;

import com.framebucket.customer.model.CustomerPrincipal;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Album designs waiting for the signed-in client's approval, so the portal can point them to it. */
@RestController
public class CustomerAlbumReviewController {
    public record PendingReview(UUID eventId, int version) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public CustomerAlbumReviewController(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/customer-portal/album-reviews")
    public List<PendingReview> pending(@AuthenticationPrincipal CustomerPrincipal customer) {
        List<UUID> ids = customer.getCustomerIds();
        if (ids == null || ids.isEmpty()) return List.of();
        return jdbc.query("""
                        SELECT p.event_id, p.version FROM fb_album_proof p
                        JOIN fb_event_booking e ON e.id = p.event_id
                        WHERE e.customer_id IN (:ids) AND p.status = 'SENT'
                        ORDER BY p.created_at DESC
                        """, new MapSqlParameterSource("ids", ids),
                (rs, n) -> new PendingReview(rs.getObject("event_id", UUID.class), rs.getInt("version")));
    }
}
