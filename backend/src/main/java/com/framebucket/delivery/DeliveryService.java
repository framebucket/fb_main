package com.framebucket.delivery;

import com.framebucket.common.notify.Notifier;
import com.framebucket.delivery.DeliveryDtos.StepRequest;
import com.framebucket.delivery.DeliveryDtos.StepView;
import com.framebucket.studio.StudioEvents;
import com.framebucket.subscription.EntitlementService;
import com.framebucket.subscription.Feature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Date;
import java.util.List;
import java.util.UUID;

/** The per-event delivery checklist (teaser, highlights, gallery, album design, printed album) clients can follow. */
@Service
public class DeliveryService {
    private static final List<String[]> DEFAULT_STEPS = List.of(
            new String[]{"Teaser", "TEASER"},
            new String[]{"Highlights film", "HIGHLIGHTS"},
            new String[]{"Full photo gallery", "GALLERY"},
            new String[]{"Album design", "ALBUM_DESIGN"},
            new String[]{"Printed album delivered", "PRINTED_ALBUM"});

    static final RowMapper<StepView> STEP = (rs, n) -> new StepView(rs.getObject("id", UUID.class), rs.getString("title"),
            rs.getString("kind"), rs.getString("status"), rs.getDate("due_date") == null ? null : rs.getDate("due_date").toLocalDate(),
            rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toInstant(), rs.getString("note"),
            rs.getInt("sort_order"));

    private final JdbcTemplate jdbc;
    private final StudioEvents events;
    private final EntitlementService entitlements;
    private final Notifier notifier;
    private final String frontendBaseUrl;

    public DeliveryService(JdbcTemplate jdbc, StudioEvents events, EntitlementService entitlements, Notifier notifier,
                           @Value("${app.frontend.base-url}") String frontendBaseUrl) {
        this.jdbc = jdbc;
        this.events = events;
        this.entitlements = entitlements;
        this.notifier = notifier;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public List<StepView> steps(UUID photographerId, UUID eventId) {
        events.forPhotographer(eventId, photographerId);
        seedIfEmpty(photographerId, eventId);
        return list(eventId);
    }

    public List<StepView> list(UUID eventId) {
        return jdbc.query("SELECT * FROM fb_delivery_step WHERE event_id = ? ORDER BY sort_order, title", STEP, eventId);
    }

    @Transactional
    public List<StepView> save(UUID photographerId, UUID eventId, UUID stepId, StepRequest request) {
        entitlements.require(photographerId, Feature.DELIVERY_TRACKER);
        StudioEvents.EventInfo event = events.forPhotographer(eventId, photographerId);
        seedIfEmpty(photographerId, eventId);
        String status = request.status() == null ? "NOT_STARTED" : request.status();
        String kind = request.kind() == null ? "OTHER" : request.kind();
        String previousStatus = null;
        if (stepId == null) {
            Integer max = jdbc.queryForObject("SELECT COALESCE(max(sort_order), 0) FROM fb_delivery_step WHERE event_id = ?", Integer.class, eventId);
            Integer count = jdbc.queryForObject("SELECT count(*) FROM fb_delivery_step WHERE event_id = ?", Integer.class, eventId);
            if (count != null && count >= 25) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "An event can have up to 25 delivery steps.");
            jdbc.update("""
                            INSERT INTO fb_delivery_step (id, photographer_id, event_id, title, kind, status, due_date, completed_at, note, sort_order)
                            VALUES (?, ?, ?, ?, ?, ?, ?, CASE WHEN ? = 'DONE' THEN CURRENT_TIMESTAMP END, ?, ?)
                            """, UUID.randomUUID(), photographerId, eventId, request.title().strip(), kind, status,
                    request.dueDate() == null ? null : Date.valueOf(request.dueDate()), status, blankToNull(request.note()),
                    request.sortOrder() != null ? request.sortOrder() : (max == null ? 0 : max) + 10);
        } else {
            previousStatus = jdbc.query("SELECT status FROM fb_delivery_step WHERE id = ? AND event_id = ?", (rs, n) -> rs.getString(1),
                    stepId, eventId).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Step not found."));
            jdbc.update("""
                            UPDATE fb_delivery_step SET title = ?, kind = ?, status = ?, due_date = ?, note = ?,
                                sort_order = COALESCE(?, sort_order),
                                completed_at = CASE WHEN ? = 'DONE' THEN COALESCE(completed_at, CURRENT_TIMESTAMP) END,
                                updated_at = CURRENT_TIMESTAMP
                            WHERE id = ? AND event_id = ?
                            """, request.title().strip(), kind, status, request.dueDate() == null ? null : Date.valueOf(request.dueDate()),
                    blankToNull(request.note()), request.sortOrder(), status, stepId, eventId);
        }
        if (request.notifyClient() && "DONE".equals(status) && !"DONE".equals(previousStatus)) {
            notifyClient(event, request.title().strip() + " is ready", request.note());
        }
        return list(eventId);
    }

    @Transactional
    public List<StepView> delete(UUID photographerId, UUID eventId, UUID stepId) {
        entitlements.require(photographerId, Feature.DELIVERY_TRACKER);
        events.forPhotographer(eventId, photographerId);
        if (jdbc.update("DELETE FROM fb_delivery_step WHERE id = ? AND event_id = ?", stepId, eventId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Step not found.");
        }
        return list(eventId);
    }

    /** Automation hook: e.g. an approved album proof completes the "Album design" step. */
    public void markKind(UUID eventId, String kind, String status) {
        jdbc.update("""
                UPDATE fb_delivery_step SET status = ?, completed_at = CASE WHEN ? = 'DONE' THEN COALESCE(completed_at, CURRENT_TIMESTAMP) END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE event_id = ? AND kind = ? AND status <> 'DONE'
                """, status, status, eventId, kind);
    }

    void seedIfEmpty(UUID photographerId, UUID eventId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM fb_delivery_step WHERE event_id = ?", Integer.class, eventId);
        if (count != null && count > 0) return;
        int sort = 10;
        for (String[] step : DEFAULT_STEPS) {
            jdbc.update("INSERT INTO fb_delivery_step (id, photographer_id, event_id, title, kind, sort_order) VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), photographerId, eventId, step[0], step[1], sort);
            sort += 10;
        }
    }

    void notifyClient(StudioEvents.EventInfo event, String headline, String note) {
        notifier.email(event.customerEmail(), event.studioDisplayName() + ": " + headline,
                "Hi " + event.customerName() + ",\n\n" + headline + " for your " + event.eventTypeName() + "."
                        + (note == null || note.isBlank() ? "" : "\n\n" + note.strip())
                        + "\n\nSign in to your FrameBucket client portal to see it: " + frontendBaseUrl + "/login\n\n"
                        + event.studioDisplayName());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
