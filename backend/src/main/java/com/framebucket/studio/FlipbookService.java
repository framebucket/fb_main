package com.framebucket.studio;

import com.framebucket.gallery.DataServiceClient;
import com.framebucket.studio.StudioDtos.FlipbookLink;
import com.framebucket.studio.StudioDtos.FlipbookRequest;
import com.framebucket.studio.StudioDtos.FlipbookResponse;
import com.framebucket.studio.StudioDtos.PublicFlipbookResponse;
import com.framebucket.studio.StudioEvents.EventInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * E-album flipbooks: an ordered set of gallery photos shown as a page-turning album behind a
 * private, unguessable share link (like the guest check-in links).
 */
@Service
public class FlipbookService {
    // Plan features and limits (see com.framebucket.subscription.EntitlementService).
    @org.springframework.beans.factory.annotation.Autowired
    private com.framebucket.subscription.EntitlementService entitlements;

    private static final SecureRandom RANDOM = new SecureRandom();

    private record Row(UUID id, UUID photographerId, UUID eventId, String title, String token, List<String> mediaIds,
                       java.time.Instant createdAt) {
    }

    private static final RowMapper<Row> ROW = (rs, n) -> new Row(
            rs.getObject("id", UUID.class),
            rs.getObject("photographer_id", UUID.class),
            rs.getObject("event_id", UUID.class),
            rs.getString("title"),
            rs.getString("access_token"),
            splitIds(rs.getString("media_ids")),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbc;
    private final StudioEvents events;
    private final StudioMedia media;
    private final DataServiceClient dataService;
    private final String frontendBaseUrl;

    public FlipbookService(JdbcTemplate jdbc, StudioEvents events, StudioMedia media, DataServiceClient dataService,
                           @Value("${app.frontend.base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.jdbc = jdbc;
        this.events = events;
        this.media = media;
        this.dataService = dataService;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    @Transactional(readOnly = true)
    public List<FlipbookResponse> list(UUID photographerId, UUID eventId) {
        events.forPhotographer(eventId, photographerId);
        return jdbc.query("SELECT * FROM fb_flipbook WHERE event_id = ? AND photographer_id = ? ORDER BY created_at DESC",
                ROW, eventId, photographerId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public FlipbookResponse create(UUID photographerId, UUID eventId, FlipbookRequest request) {
        events.forPhotographer(eventId, photographerId);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO fb_flipbook (id, photographer_id, event_id, title, access_token, media_ids)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """, id, photographerId, eventId, request.title().trim(), newToken(), joinIds(request.mediaIds()));
        return toResponse(jdbc.queryForObject("SELECT * FROM fb_flipbook WHERE id = ?", ROW, id));
    }

    @Transactional
    public FlipbookResponse update(UUID photographerId, UUID eventId, UUID flipbookId, FlipbookRequest request) {
        int updated = jdbc.update("""
                        UPDATE fb_flipbook SET title = ?, media_ids = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE id = ? AND event_id = ? AND photographer_id = ?
                        """, request.title().trim(), joinIds(request.mediaIds()), flipbookId, eventId, photographerId);
        if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Flipbook not found.");
        return toResponse(jdbc.queryForObject("SELECT * FROM fb_flipbook WHERE id = ?", ROW, flipbookId));
    }

    @Transactional
    public void delete(UUID photographerId, UUID eventId, UUID flipbookId) {
        if (jdbc.update("DELETE FROM fb_flipbook WHERE id = ? AND event_id = ? AND photographer_id = ?",
                flipbookId, eventId, photographerId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Flipbook not found.");
        }
    }

    /** The photos the customer picked for album design, as a starting point for the flipbook. */
    @Transactional(readOnly = true)
    public List<String> customerSelection(UUID photographerId, UUID eventId) {
        EventInfo event = events.forPhotographer(eventId, photographerId);
        Object ids = dataService.albumSelection(eventId.toString(), photographerId.toString(), event.customerId().toString())
                .get("photo_ids");
        return ids instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    @Transactional(readOnly = true)
    public List<FlipbookLink> linksForEvent(UUID eventId) {
        return jdbc.query("SELECT * FROM fb_flipbook WHERE event_id = ? ORDER BY created_at DESC", ROW, eventId).stream()
                .map(row -> new FlipbookLink(row.title(), row.mediaIds().size(), shareUrl(row.token()))).toList();
    }

    @Transactional(readOnly = true)
    public PublicFlipbookResponse publicFlipbook(String token) {
        Row row = byToken(token);
        EventInfo event = events.byId(row.eventId());
        return new PublicFlipbookResponse(row.title(), event.studioDisplayName(), event.eventTypeName(),
                event.eventDate(), event.eventTime(), row.mediaIds().size(),
                !entitlements.has(event.photographerId(), com.framebucket.subscription.Feature.CUSTOM_BRANDING));
    }

    public ResponseEntity<StreamingResponseBody> page(String token, int pageIndex) {
        Row row = byToken(token);
        if (pageIndex < 0 || pageIndex >= row.mediaIds().size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Page not found.");
        }
        EventInfo event = events.byId(row.eventId());
        return media.preview(row.eventId(), event.customerId(), row.mediaIds().get(pageIndex));
    }

    private Row byToken(String token) {
        if (token == null || token.length() < 20 || token.length() > 64) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Flipbook not found.");
        }
        return jdbc.query("SELECT * FROM fb_flipbook WHERE access_token = ?", ROW, token).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flipbook not found."));
    }

    private FlipbookResponse toResponse(Row row) {
        return new FlipbookResponse(row.id(), row.eventId(), row.title(), row.mediaIds().size(), row.mediaIds(),
                shareUrl(row.token()), row.createdAt());
    }

    private String shareUrl(String token) {
        return frontendBaseUrl + "/flipbook/" + token;
    }

    private static String newToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String joinIds(List<String> ids) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String id : ids) {
            String trimmed = id.trim();
            if (!trimmed.matches("[A-Za-z0-9_-]{1,128}")) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "One of the selected photos is invalid.");
            }
            unique.add(trimmed);
        }
        return String.join(",", unique);
    }

    private static List<String> splitIds(String value) {
        return value == null || value.isBlank() ? List.of() : Arrays.asList(value.split(","));
    }
}
