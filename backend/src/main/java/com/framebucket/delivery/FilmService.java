package com.framebucket.delivery;

import com.framebucket.delivery.DeliveryDtos.FilmRequest;
import com.framebucket.delivery.DeliveryDtos.FilmView;
import com.framebucket.delivery.DeliveryDtos.VideoOption;
import com.framebucket.gallery.DataServiceClient;
import com.framebucket.studio.StudioEvents;
import com.framebucket.subscription.EntitlementService;
import com.framebucket.subscription.Feature;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Films delivered alongside photos: either a video uploaded to the event gallery (streamed with seeking support)
 * or a YouTube / Vimeo / Google Drive link.
 */
@Service
public class FilmService {
    private static final Pattern YOUTUBE = Pattern.compile("(?:youtube\\.com/(?:watch\\?(?:.*&)?v=|embed/|shorts/|live/)|youtu\\.be/)([A-Za-z0-9_-]{6,20})");
    private static final Pattern VIMEO = Pattern.compile("vimeo\\.com/(?:video/)?(\\d{5,12})");
    private static final Pattern DRIVE = Pattern.compile("drive\\.google\\.com/file/d/([A-Za-z0-9_-]{10,80})");

    record FilmRow(UUID id, UUID eventId, String title, String kind, String mediaId, String externalUrl, int sortOrder,
                   java.time.Instant createdAt) {
    }

    static final RowMapper<FilmRow> ROW = (rs, n) -> new FilmRow(rs.getObject("id", UUID.class), rs.getObject("event_id", UUID.class),
            rs.getString("title"), rs.getString("kind"), rs.getString("media_id"), rs.getString("external_url"), rs.getInt("sort_order"),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbc;
    private final StudioEvents events;
    private final EntitlementService entitlements;
    private final DataServiceClient dataService;
    private final DeliveryService delivery;

    public FilmService(JdbcTemplate jdbc, StudioEvents events, EntitlementService entitlements, DataServiceClient dataService,
                       DeliveryService delivery) {
        this.jdbc = jdbc;
        this.events = events;
        this.entitlements = entitlements;
        this.dataService = dataService;
        this.delivery = delivery;
    }

    public List<FilmView> forPhotographer(UUID photographerId, UUID eventId) {
        events.forPhotographer(eventId, photographerId);
        return list(eventId, false);
    }

    public List<FilmView> list(UUID eventId, boolean customerUrls) {
        return jdbc.query("SELECT * FROM fb_event_film WHERE event_id = ? ORDER BY sort_order, created_at", ROW, eventId)
                .stream().map(row -> view(row, customerUrls)).toList();
    }

    /** Videos already uploaded to the event gallery, for the "add film" picker. */
    public List<VideoOption> galleryVideos(UUID photographerId, UUID eventId) {
        StudioEvents.EventInfo event = events.forPhotographer(eventId, photographerId);
        return media(event).stream().filter(m -> "video".equals(m.get("media_type")))
                .map(m -> new VideoOption(String.valueOf(m.get("photo_id")), String.valueOf(m.get("original_filename")),
                        Objects.toString(m.get("folder_id"), null),
                        m.get("size_bytes") instanceof Number number ? number.longValue() : null))
                .toList();
    }

    @Transactional
    public List<FilmView> add(UUID photographerId, UUID eventId, FilmRequest request) {
        entitlements.require(photographerId, Feature.FILMS);
        StudioEvents.EventInfo event = events.forPhotographer(eventId, photographerId);
        boolean hasMedia = request.mediaId() != null && !request.mediaId().isBlank();
        boolean hasLink = request.externalUrl() != null && !request.externalUrl().isBlank();
        if (hasMedia == hasLink) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Choose an uploaded video or paste a video link.");
        }
        String url = null;
        if (hasMedia) {
            boolean found = media(event).stream().anyMatch(m -> request.mediaId().equals(String.valueOf(m.get("photo_id")))
                    && "video".equals(m.get("media_type")));
            if (!found) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "That video isn't in this event's gallery.");
        } else {
            url = validUrl(request.externalUrl().strip());
        }
        Integer max = jdbc.queryForObject("SELECT COALESCE(max(sort_order), 0) FROM fb_event_film WHERE event_id = ?", Integer.class, eventId);
        jdbc.update("""
                        INSERT INTO fb_event_film (id, photographer_id, event_id, title, kind, media_id, external_url, sort_order)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """, UUID.randomUUID(), photographerId, eventId, request.title().strip(), request.kind(),
                hasMedia ? request.mediaId().strip() : null, url,
                request.sortOrder() != null ? request.sortOrder() : (max == null ? 0 : max) + 10);
        String stepKind = switch (request.kind()) {
            case "TEASER" -> "TEASER";
            case "HIGHLIGHTS" -> "HIGHLIGHTS";
            case "FULL_FILM" -> "FULL_FILM";
            default -> null;
        };
        if (stepKind != null) delivery.markKind(eventId, stepKind, "DONE");
        if (request.notifyClient()) delivery.notifyClient(event, "Your " + request.title().strip() + " is ready to watch", null);
        return list(eventId, false);
    }

    @Transactional
    public List<FilmView> delete(UUID photographerId, UUID eventId, UUID filmId) {
        events.forPhotographer(eventId, photographerId);
        if (jdbc.update("DELETE FROM fb_event_film WHERE id = ? AND event_id = ?", filmId, eventId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Film not found.");
        }
        return list(eventId, false);
    }

    public FilmRow film(UUID eventId, UUID filmId) {
        return jdbc.query("SELECT * FROM fb_event_film WHERE id = ? AND event_id = ?", ROW, filmId, eventId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Film not found."));
    }

    private List<Map<String, Object>> media(StudioEvents.EventInfo event) {
        try {
            Object photos = dataService.listMedia(event.id().toString(), event.customerId().toString(), null, true).get("photos");
            if (photos instanceof List<?> list) {
                return list.stream().filter(Map.class::isInstance).map(item -> {
                    @SuppressWarnings("unchecked") Map<String, Object> map = (Map<String, Object>) item;
                    return map;
                }).toList();
            }
        } catch (RuntimeException exception) {
            // The gallery may not be provisioned yet.
        }
        return List.of();
    }

    private FilmView view(FilmRow row, boolean customer) {
        String source = row.mediaId() != null ? "UPLOAD" : "LINK";
        String embed = row.externalUrl() == null ? null : embedUrl(row.externalUrl());
        return new FilmView(row.id(), row.title(), row.kind(), source, row.mediaId(), row.externalUrl(), embed, row.sortOrder(),
                row.createdAt());
    }

    static String validUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) throw new IllegalArgumentException();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Paste a full https:// link to the video.");
        }
        return value;
    }

    static String embedUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        Matcher m = YOUTUBE.matcher(url);
        if (lower.contains("youtu") && m.find()) return "https://www.youtube-nocookie.com/embed/" + m.group(1);
        m = VIMEO.matcher(url);
        if (lower.contains("vimeo.com") && m.find()) return "https://player.vimeo.com/video/" + m.group(1);
        m = DRIVE.matcher(url);
        if (lower.contains("drive.google.com") && m.find()) return "https://drive.google.com/file/d/" + m.group(1) + "/preview";
        return null;
    }
}
