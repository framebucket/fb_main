package com.framebucket.delivery;

import com.framebucket.common.notify.Notifier;
import com.framebucket.delivery.DeliveryDtos.CommentRequest;
import com.framebucket.delivery.DeliveryDtos.CommentView;
import com.framebucket.delivery.DeliveryDtos.ProofCreateRequest;
import com.framebucket.delivery.DeliveryDtos.ProofView;
import com.framebucket.gallery.DataServiceClient;
import com.framebucket.studio.StudioEvents;
import com.framebucket.subscription.EntitlementService;
import com.framebucket.subscription.Feature;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Album design proofing: the photographer uploads album spreads to a gallery folder and sends them as a proof
 * version. The client comments on individual spreads, then requests changes or approves the design.
 */
@Service
public class ProofService {
    record ProofRow(UUID id, UUID eventId, int version, String status, List<String> mediaIds, String message, String customerNote,
                    Instant createdAt, Instant respondedAt) {
    }

    private final JdbcTemplate jdbc;
    private final StudioEvents events;
    private final EntitlementService entitlements;
    private final DataServiceClient dataService;
    private final DeliveryService delivery;
    private final Notifier notifier;

    public ProofService(JdbcTemplate jdbc, StudioEvents events, EntitlementService entitlements, DataServiceClient dataService,
                        DeliveryService delivery, Notifier notifier) {
        this.jdbc = jdbc;
        this.events = events;
        this.entitlements = entitlements;
        this.dataService = dataService;
        this.delivery = delivery;
        this.notifier = notifier;
    }

    public List<ProofView> forPhotographer(UUID photographerId, UUID eventId) {
        events.forPhotographer(eventId, photographerId);
        return rows(eventId).stream().map(this::view).toList();
    }

    @Transactional
    public List<ProofView> create(UUID photographerId, UUID eventId, ProofCreateRequest request) {
        entitlements.require(photographerId, Feature.ALBUM_PROOFING);
        StudioEvents.EventInfo event = events.forPhotographer(eventId, photographerId);
        List<Map<String, Object>> images = images(event, null);
        List<String> ids;
        if (request.mediaIds() != null && !request.mediaIds().isEmpty()) {
            Set<String> available = new LinkedHashSet<>(images.stream().map(m -> String.valueOf(m.get("photo_id"))).toList());
            ids = new ArrayList<>(new LinkedHashSet<>(request.mediaIds()));
            if (!available.containsAll(ids)) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Some selected spreads aren't in this event's gallery.");
            }
        } else if (request.folderId() != null && !request.folderId().isBlank()) {
            ids = images.stream().filter(m -> request.folderId().equals(String.valueOf(m.get("folder_id"))))
                    .sorted(Comparator.comparing(m -> naturalKey(String.valueOf(m.get("original_filename")))))
                    .map(m -> String.valueOf(m.get("photo_id"))).toList();
        } else {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Choose the gallery folder that holds your album spreads.");
        }
        if (ids.isEmpty()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "That folder has no processed images yet.");
        if (ids.size() > 400) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "A proof can have up to 400 spreads.");
        Integer version = jdbc.queryForObject("SELECT COALESCE(max(version), 0) + 1 FROM fb_album_proof WHERE event_id = ?", Integer.class, eventId);
        jdbc.update("UPDATE fb_album_proof SET status = 'SUPERSEDED' WHERE event_id = ? AND status IN ('SENT', 'CHANGES_REQUESTED')", eventId);
        jdbc.update("""
                        INSERT INTO fb_album_proof (id, photographer_id, event_id, version, status, media_ids, message)
                        VALUES (?, ?, ?, ?, 'SENT', ?, ?)
                        """, UUID.randomUUID(), photographerId, eventId, version, String.join(",", ids), blankToNull(request.message()));
        delivery.seedIfEmpty(photographerId, eventId);
        delivery.markKind(eventId, "ALBUM_DESIGN", "IN_PROGRESS");
        delivery.notifyClient(event, "Your album design (version " + version + ") is ready for review",
                "Open it, leave comments on any spread, then approve the design or ask for changes."
                        + (request.message() == null || request.message().isBlank() ? "" : "\n\nMessage from the studio: " + request.message().strip()));
        return forPhotographer(photographerId, eventId);
    }

    @Transactional
    public List<ProofView> photographerComment(UUID photographerId, UUID eventId, UUID proofId, CommentRequest request) {
        events.forPhotographer(eventId, photographerId);
        ProofRow proof = proof(eventId, proofId);
        addComment(proof, "PHOTOGRAPHER", request);
        return forPhotographer(photographerId, eventId);
    }

    @Transactional
    public List<ProofView> resolveComment(UUID photographerId, UUID eventId, UUID proofId, UUID commentId, boolean resolved) {
        events.forPhotographer(eventId, photographerId);
        proof(eventId, proofId);
        if (jdbc.update("UPDATE fb_album_proof_comment SET resolved = ? WHERE id = ? AND proof_id = ?", resolved, commentId, proofId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found.");
        }
        return forPhotographer(photographerId, eventId);
    }

    // ---- client -------------------------------------------------------------------------------------

    public ProofView latestForCustomer(UUID eventId) {
        return rows(eventId).stream().filter(p -> !"SUPERSEDED".equals(p.status())).findFirst().map(this::view).orElse(null);
    }

    @Transactional
    public ProofView customerComment(StudioEvents.EventInfo event, UUID proofId, CommentRequest request) {
        ProofRow proof = proof(event.id(), proofId);
        if (!"SENT".equals(proof.status()) && !"CHANGES_REQUESTED".equals(proof.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This album version is closed for comments.");
        }
        addComment(proof, "CUSTOMER", request);
        return view(proof(event.id(), proofId));
    }

    @Transactional
    public ProofView decide(StudioEvents.EventInfo event, UUID proofId, boolean approve, String note) {
        ProofRow proof = proof(event.id(), proofId);
        if (!"SENT".equals(proof.status()) && !"CHANGES_REQUESTED".equals(proof.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This album version has already been " + proof.status().toLowerCase() + ".");
        }
        if (!approve && (note == null || note.isBlank())) {
            Integer comments = jdbc.queryForObject("SELECT count(*) FROM fb_album_proof_comment WHERE proof_id = ? AND author = 'CUSTOMER' AND NOT resolved",
                    Integer.class, proofId);
            if (comments == null || comments == 0) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Tell the studio what you'd like changed.");
            }
        }
        jdbc.update("UPDATE fb_album_proof SET status = ?, customer_note = ?, responded_at = CURRENT_TIMESTAMP WHERE id = ?",
                approve ? "APPROVED" : "CHANGES_REQUESTED", blankToNull(note), proofId);
        if (approve) delivery.markKind(event.id(), "ALBUM_DESIGN", "DONE");
        int open = jdbc.queryForObject("SELECT count(*) FROM fb_album_proof_comment WHERE proof_id = ? AND author = 'CUSTOMER' AND NOT resolved",
                Integer.class, proofId);
        notifier.email(event.photographerEmail(),
                (approve ? "Album approved: " : "Album changes requested: ") + event.customerName() + " (v" + proof.version() + ")",
                event.customerName() + (approve ? " approved album design version " : " asked for changes to album design version ")
                        + proof.version() + " for " + event.eventTypeName() + " on " + event.eventDate() + "."
                        + (note == null || note.isBlank() ? "" : "\n\nNote: " + note.strip())
                        + (approve ? "" : "\n\nOpen comments: " + open));
        return view(proof(event.id(), proofId));
    }

    public String spreadMediaId(UUID eventId, UUID proofId, int index) {
        ProofRow proof = proof(eventId, proofId);
        if (index < 0 || index >= proof.mediaIds().size()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Spread not found.");
        return proof.mediaIds().get(index);
    }

    // ---- helpers ------------------------------------------------------------------------------------

    private void addComment(ProofRow proof, String author, CommentRequest request) {
        if (request.spreadIndex() >= proof.mediaIds().size()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Spread not found.");
        Integer count = jdbc.queryForObject("SELECT count(*) FROM fb_album_proof_comment WHERE proof_id = ?", Integer.class, proof.id());
        if (count != null && count >= 1000) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Too many comments on this version.");
        jdbc.update("INSERT INTO fb_album_proof_comment (id, proof_id, spread_index, author, body) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), proof.id(), request.spreadIndex(), author, request.body().strip());
    }

    private List<ProofRow> rows(UUID eventId) {
        return jdbc.query("SELECT * FROM fb_album_proof WHERE event_id = ? ORDER BY version DESC", (rs, n) -> new ProofRow(
                rs.getObject("id", UUID.class), rs.getObject("event_id", UUID.class), rs.getInt("version"), rs.getString("status"),
                Arrays.asList(rs.getString("media_ids").split(",")), rs.getString("message"), rs.getString("customer_note"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("responded_at") == null ? null : rs.getTimestamp("responded_at").toInstant()), eventId);
    }

    private ProofRow proof(UUID eventId, UUID proofId) {
        return rows(eventId).stream().filter(p -> p.id().equals(proofId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Album proof not found."));
    }

    private ProofView view(ProofRow row) {
        List<CommentView> comments = jdbc.query("SELECT * FROM fb_album_proof_comment WHERE proof_id = ? ORDER BY spread_index, created_at",
                (rs, n) -> new CommentView(rs.getObject("id", UUID.class), rs.getInt("spread_index"), rs.getString("author"),
                        rs.getString("body"), rs.getBoolean("resolved"), rs.getTimestamp("created_at").toInstant()), row.id());
        return new ProofView(row.id(), row.version(), row.status(), row.mediaIds().size(), row.mediaIds(), row.message(),
                row.customerNote(), row.createdAt(), row.respondedAt(), comments);
    }

    private List<Map<String, Object>> images(StudioEvents.EventInfo event, String folderId) {
        try {
            Object photos = dataService.listMedia(event.id().toString(), event.customerId().toString(), folderId, false).get("photos");
            if (photos instanceof List<?> list) {
                return list.stream().filter(Map.class::isInstance).map(item -> {
                    @SuppressWarnings("unchecked") Map<String, Object> map = (Map<String, Object>) item;
                    return map;
                }).filter(m -> !"video".equals(m.get("media_type"))).toList();
            }
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Open the event gallery and upload your album spreads first.");
        }
        return List.of();
    }

    /** "spread-2.jpg" sorts before "spread-10.jpg". */
    static String naturalKey(String name) {
        StringBuilder key = new StringBuilder();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\d+|\\D+").matcher(name.toLowerCase());
        while (matcher.find()) {
            String part = matcher.group();
            key.append(Character.isDigit(part.charAt(0)) ? String.format("%012d", Long.parseLong(part.length() > 12 ? part.substring(0, 12) : part)) : part);
        }
        return key.toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
