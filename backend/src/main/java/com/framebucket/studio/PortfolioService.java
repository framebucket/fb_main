package com.framebucket.studio;

import com.framebucket.studio.StudioDtos.PortfolioItemRequest;
import com.framebucket.studio.StudioDtos.PortfolioItemResponse;
import com.framebucket.studio.StudioDtos.PortfolioItemUpdateRequest;
import com.framebucket.studio.StudioDtos.PublicPortfolioItem;
import com.framebucket.studio.StudioDtos.PublicPortfolioResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PortfolioService {
    // Plan features and limits (see com.framebucket.subscription.EntitlementService).
    @org.springframework.beans.factory.annotation.Autowired
    private com.framebucket.subscription.EntitlementService entitlements;

    private final JdbcTemplate jdbc;
    private final StudioEvents events;
    private final StudioMedia media;

    public PortfolioService(JdbcTemplate jdbc, StudioEvents events, StudioMedia media) {
        this.jdbc = jdbc;
        this.events = events;
        this.media = media;
    }

    @Transactional(readOnly = true)
    public List<PortfolioItemResponse> items(UUID photographerId) {
        return jdbc.query("""
                        SELECT p.*, e.event_code FROM fb_portfolio_item p JOIN fb_event_booking e ON e.id = p.event_id
                        WHERE p.photographer_id = ? ORDER BY p.sort_order, p.created_at
                        """, (rs, n) -> {
                    UUID eventId = rs.getObject("event_id", UUID.class);
                    String mediaId = rs.getString("media_id");
                    return new PortfolioItemResponse(rs.getObject("id", UUID.class), eventId, rs.getString("event_code"),
                            mediaId, rs.getString("caption"), rs.getInt("sort_order"),
                            "/api/events/" + eventId + "/gallery/media/" + mediaId + "/preview");
                }, photographerId);
    }

    @Transactional
    public List<PortfolioItemResponse> add(UUID photographerId, PortfolioItemRequest request) {
        events.forPhotographer(request.eventId(), photographerId);
        Integer next = jdbc.queryForObject(
                "SELECT COALESCE(MAX(sort_order), 0) + 1 FROM fb_portfolio_item WHERE photographer_id = ?",
                Integer.class, photographerId);
        int order = next == null ? 1 : next;
        for (String mediaId : request.mediaIds()) {
            if (!mediaId.matches("[A-Za-z0-9_-]{1,128}")) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "One of the selected photos is invalid.");
            }
            jdbc.update("""
                    INSERT INTO fb_portfolio_item (id, photographer_id, event_id, media_id, caption, sort_order)
                    VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (photographer_id, event_id, media_id) DO NOTHING
                    """, UUID.randomUUID(), photographerId, request.eventId(), mediaId,
                    StudioProfileService.blankToNull(request.caption()), order++);
        }
        return items(photographerId);
    }

    @Transactional
    public List<PortfolioItemResponse> update(UUID photographerId, UUID itemId, PortfolioItemUpdateRequest request) {
        if (jdbc.update("UPDATE fb_portfolio_item SET caption = ?, sort_order = ? WHERE id = ? AND photographer_id = ?",
                StudioProfileService.blankToNull(request.caption()), request.sortOrder(), itemId, photographerId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio photo not found.");
        }
        return items(photographerId);
    }

    @Transactional
    public void remove(UUID photographerId, UUID itemId) {
        if (jdbc.update("DELETE FROM fb_portfolio_item WHERE id = ? AND photographer_id = ?", itemId, photographerId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio photo not found.");
        }
    }

    @Transactional(readOnly = true)
    public PublicPortfolioResponse publicPortfolio(String slug) {
        Map<String, Object> studio = publishedStudio(slug);
        UUID photographerId = (UUID) studio.get("photographer_id");
        boolean poweredBy = !entitlements.has(photographerId, com.framebucket.subscription.Feature.CUSTOM_BRANDING);
        List<PublicPortfolioItem> items = jdbc.query("""
                        SELECT id, caption FROM fb_portfolio_item WHERE photographer_id = ? ORDER BY sort_order, created_at
                        """, (rs, n) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    return new PublicPortfolioItem(id, rs.getString("caption"),
                            "/api/public/portfolio/" + slug + "/items/" + id + "/image");
                }, photographerId);
        return new PublicPortfolioResponse((String) studio.get("studio_name"), (String) studio.get("portfolio_headline"),
                (String) studio.get("portfolio_bio"), (String) studio.get("email"), (String) studio.get("mobile_number"),
                items, poweredBy);
    }

    public ResponseEntity<StreamingResponseBody> publicImage(String slug, UUID itemId) {
        Map<String, Object> studio = publishedStudio(slug);
        Map<String, Object> item = jdbc.queryForList("""
                        SELECT p.event_id, p.media_id, e.customer_id FROM fb_portfolio_item p
                        JOIN fb_event_booking e ON e.id = p.event_id
                        WHERE p.id = ? AND p.photographer_id = ?
                        """, itemId, studio.get("photographer_id")).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found."));
        return media.preview((UUID) item.get("event_id"), (UUID) item.get("customer_id"), (String) item.get("media_id"));
    }

    private Map<String, Object> publishedStudio(String slug) {
        if (slug == null || !slug.matches(StudioDtos.SLUG_PATTERN)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found.");
        }
        return jdbc.queryForList("""
                        SELECT s.photographer_id, s.portfolio_headline, s.portfolio_bio, u.email, u.mobile_number,
                               COALESCE(NULLIF(u.studio_name, ''), u.full_name) AS studio_name
                        FROM fb_studio_profile s JOIN fb_user u ON u.id = s.photographer_id
                        WHERE s.portfolio_slug = ? AND s.portfolio_public AND u.active
                        """, slug).stream().findFirst()
                .filter(studio -> entitlements.has((UUID) studio.get("photographer_id"), com.framebucket.subscription.Feature.PORTFOLIO))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found."));
    }
}
