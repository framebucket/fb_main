package com.framebucket.gallery;

import com.framebucket.common.phone.IndianMobileNumber;
import com.framebucket.enquiry.notification.WhatsAppPhotoSender;
import com.framebucket.event.model.EventBooking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class GuestPhotoDeliveryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuestPhotoDeliveryService.class);

    private final EventGuestRepository guests;
    private final EventGuestPhotoDeliveryRepository deliveries;
    private final DataServiceClient dataService;
    private final WhatsAppPhotoSender photoSender;
    private final String templateName;
    private final String templateLanguage;
    private final AtomicBoolean dispatching = new AtomicBoolean();

    public GuestPhotoDeliveryService(
            EventGuestRepository guests,
            EventGuestPhotoDeliveryRepository deliveries,
            DataServiceClient dataService,
            WhatsAppPhotoSender photoSender,
            @Value("${app.whatsapp.photo-match-template:framebucket_event_photo_match}") String templateName,
            @Value("${app.whatsapp.photo-match-language:${app.whatsapp.language:en}}") String templateLanguage
    ) {
        this.guests = guests;
        this.deliveries = deliveries;
        this.dataService = dataService;
        this.photoSender = photoSender;
        this.templateName = templateName;
        this.templateLanguage = templateLanguage;
    }

    public void enqueueUploadMatches(EventBooking event, Object rawGuestIds, String photoId, Integer photoNumber) {
        if (!(rawGuestIds instanceof List<?> guestIds) || photoId == null || photoId.isBlank()) return;
        for (Object rawGuestId : guestIds) {
            if (!(rawGuestId instanceof String guestId)) continue;
            try {
                guests.findByIdAndEvent_Id(UUID.fromString(guestId), event.getId())
                        .ifPresent(guest -> enqueue(guest, photoId, photoNumber));
            } catch (IllegalArgumentException exception) {
                LOGGER.warn("The data service returned an invalid matched guest ID for event {}.", event.getEventCode());
            }
        }
    }

    public int enqueueExistingMatches(EventBooking event, EventGuest guest, Object rawPhotoIds) {
        if (!(rawPhotoIds instanceof List<?> photoIds) || photoIds.isEmpty()) return 0;
        Map<String, Object> response = dataService.listMedia(
                event.getId().toString(), event.getCustomer().getId().toString(), null);
        Object rawPhotos = response.get("photos");
        if (!(rawPhotos instanceof List<?> photos)) return 0;
        int count = 0;
        for (Object rawPhoto : photos) {
            if (!(rawPhoto instanceof Map<?, ?> photo)) continue;
            Object rawId = photo.get("photo_id");
            Object mediaType = photo.get("media_type");
            if (!(rawId instanceof String photoId) || !photoIds.contains(photoId)
                    || "video".equals(mediaType)) continue;
            Integer number = photo.get("photo_number") instanceof Number value ? value.intValue() : null;
            if (enqueue(guest, photoId, number)) count++;
        }
        return count;
    }

    private boolean enqueue(EventGuest guest, String photoId, Integer photoNumber) {
        if (guest.getWhatsappConsentAt() == null) return false;
        EventGuestPhotoDelivery delivery = deliveries.findByGuest_IdAndPhotoId(guest.getId(), photoId).orElse(null);
        if (delivery != null) {
            if ("SENT".equals(delivery.getStatus()) || "PENDING".equals(delivery.getStatus())
                    || "SENDING".equals(delivery.getStatus()) || "FAILED_FINAL".equals(delivery.getStatus())) {
                return false;
            }
            delivery.retry();
        } else {
            delivery = new EventGuestPhotoDelivery(guest, photoId, photoNumber);
        }
        deliveries.save(delivery);
        return true;
    }

    @Scheduled(fixedDelayString = "${app.gallery.photo-delivery-poll-ms:5000}")
    public void dispatchDueMessages() {
        if (!dispatching.compareAndSet(false, true)) return;
        try {
            List<EventGuestPhotoDelivery> due = deliveries
                    .findTop20ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                            List.of("PENDING", "FAILED", "SENDING"), Instant.now());
            for (EventGuestPhotoDelivery delivery : due) dispatch(delivery);
        } catch (RuntimeException exception) {
            LOGGER.error("Could not process the event photo delivery queue.", exception);
        } finally {
            dispatching.set(false);
        }
    }

    private void dispatch(EventGuestPhotoDelivery delivery) {
        delivery.markSending();
        deliveries.save(delivery);
        EventGuest guest = delivery.getGuest();
        EventBooking event = guest.getEvent();
        try {
            byte[] image = dataService.whatsappPhotoBytes(
                    event.getId().toString(), delivery.getPhotoId(), event.getCustomer().getId().toString());
            int number = delivery.getPhotoNumber() == null ? 0 : delivery.getPhotoNumber();
            String filename = String.format("FrameBucket-%s-%05d.jpg", event.getEventCode(), number)
                    .replaceAll("[^A-Za-z0-9._-]", "_");
            String recipient = "91" + IndianMobileNumber.normalize(guest.getMobileNumber());
            photoSender.sendPhotoTemplate(
                    recipient,
                    templateName,
                    templateLanguage,
                    image,
                    filename,
                    List.of(guest.getFullName(), event.getEventType().getName(), Integer.toString(number))
            );
            delivery.markSent();
            deliveries.save(delivery);
        } catch (RuntimeException exception) {
            delivery.markFailed(exception.getMessage());
            deliveries.save(delivery);
            LOGGER.warn("Matched photo {} could not be delivered to guest {} for event {}.",
                    delivery.getPhotoId(), guest.getId(), event.getEventCode(), exception);
        }
    }
}
