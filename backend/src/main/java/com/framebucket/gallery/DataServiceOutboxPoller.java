package com.framebucket.gallery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pulls notifications produced by data-service workers (face matches, album archives) and
 * applies them. Items are leased by the data service, so several application instances can poll
 * without handling the same item twice; an item that is not acknowledged is retried after its lease.
 */
@Component
public class DataServiceOutboxPoller {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataServiceOutboxPoller.class);
    private static final int BATCH_SIZE = 50;
    private static final int LEASE_SECONDS = 120;

    private final DataServiceClient dataService;
    private final GuestPhotoDeliveryService photoDelivery;
    private final DataGalleryService gallery;
    private final boolean enabled;
    private final AtomicBoolean polling = new AtomicBoolean();

    public DataServiceOutboxPoller(
            DataServiceClient dataService,
            GuestPhotoDeliveryService photoDelivery,
            DataGalleryService gallery,
            @Value("${app.data-service.token:}") String serviceToken
    ) {
        this.dataService = dataService;
        this.photoDelivery = photoDelivery;
        this.gallery = gallery;
        this.enabled = serviceToken != null && !serviceToken.isBlank();
    }

    @Scheduled(fixedDelayString = "${app.data-service.outbox-poll-ms:5000}",
            initialDelayString = "${app.data-service.outbox-initial-delay-ms:15000}")
    public void poll() {
        if (!enabled || !polling.compareAndSet(false, true)) return;
        try {
            List<Map<String, Object>> items;
            do {
                items = dataService.claimOutbox(BATCH_SIZE, LEASE_SECONDS);
                List<Long> handled = new ArrayList<>();
                for (Map<String, Object> item : items) {
                    Long id = item.get("id") instanceof Number number ? number.longValue() : null;
                    if (id == null) continue;
                    try {
                        handle(item);
                        handled.add(id);
                    } catch (RuntimeException exception) {
                        LOGGER.warn("Data-service notification {} ({}) failed; it will be retried.",
                                id, item.get("kind"), exception);
                    }
                }
                dataService.ackOutbox(handled);
            } while (items.size() == BATCH_SIZE);
        } catch (RuntimeException exception) {
            LOGGER.debug("Could not poll the data-service outbox.", exception);
        } finally {
            polling.set(false);
        }
    }

    private void handle(Map<String, Object> item) {
        String kind = String.valueOf(item.get("kind"));
        UUID eventId;
        try {
            eventId = UUID.fromString(String.valueOf(item.get("event_id")));
        } catch (IllegalArgumentException exception) {
            // Malformed items can never succeed; acknowledge them instead of retrying forever.
            LOGGER.error("Dropping data-service notification {} with invalid event ID {}.", item.get("id"), item.get("event_id"));
            return;
        }
        Map<?, ?> payload = item.get("payload") instanceof Map<?, ?> map ? map : Map.of();
        switch (kind) {
            case "face_match" -> photoDelivery.enqueueMatch(
                    eventId,
                    payload.get("guest_id") instanceof String guestId ? guestId : null,
                    payload.get("photo_id") instanceof String photoId ? photoId : null,
                    payload.get("photo_number") instanceof Number number ? number.intValue() : null);
            case "album_ready" -> gallery.notifyAlbumReady(eventId);
            case "album_failed" -> LOGGER.error("The album-design archive for event {} could not be built: {}",
                    eventId, payload.get("error"));
            default -> LOGGER.warn("Ignoring unknown data-service notification kind {}.", kind);
        }
    }
}
