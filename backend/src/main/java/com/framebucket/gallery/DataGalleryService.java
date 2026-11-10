package com.framebucket.gallery;

import com.framebucket.customer.model.CustomerPrincipal;
import com.framebucket.enquiry.notification.WhatsAppTemplateSender;
import com.framebucket.event.model.EventBooking;
import com.framebucket.event.repository.EventBookingRepository;
import com.framebucket.user.auth.model.PhotographerPrincipal;
import com.framebucket.common.phone.IndianMobileNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DataGalleryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataGalleryService.class);

    private final EventBookingRepository events;
    private final DataServiceClient dataService;
    private final WhatsAppTemplateSender whatsAppSender;
    private final String albumTemplate;

    public DataGalleryService(
            EventBookingRepository events,
            DataServiceClient dataService,
            WhatsAppTemplateSender whatsAppSender,
            @Value("${app.whatsapp.album-design-template:framebucket_album_design_ready}") String albumTemplate
    ) {
        this.events = events;
        this.dataService = dataService;
        this.whatsAppSender = whatsAppSender;
        this.albumTemplate = albumTemplate;
    }

    public void provisionBestEffort(EventBooking event) {
        try {
            ensureProvisioned(event);
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not provision data storage for event {}. It will retry when the gallery is opened.",
                    event.getEventCode(), exception);
        }
    }

    public Map<String, Object> foldersForPhotographer(UUID eventId, PhotographerPrincipal principal) {
        EventBooking event = photographerEvent(eventId, principal);
        ensureProvisioned(event);
        return dataService.listFolders(storageEventId(event), principal.getId().toString(), event.getCustomer().getId().toString());
    }

    public Map<String, Object> createFolder(UUID eventId, PhotographerPrincipal principal, String name) {
        EventBooking event = photographerEvent(eventId, principal);
        ensureProvisioned(event);
        return dataService.createFolder(storageEventId(event), principal.getId().toString(), event.getCustomer().getId().toString(), name);
    }

    public Map<String, Object> mediaForPhotographer(UUID eventId, PhotographerPrincipal principal, String folderId) {
        EventBooking event = photographerEvent(eventId, principal);
        ensureProvisioned(event);
        return dataService.listMedia(storageEventId(event), event.getCustomer().getId().toString(), folderId);
    }

    public Map<String, Object> upload(UUID eventId, PhotographerPrincipal principal, String folderId, MultipartFile file) {
        EventBooking event = photographerEvent(eventId, principal);
        ensureProvisioned(event);
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a non-empty photo or video file.");
        }
        return dataService.upload(storageEventId(event), principal.getId().toString(),
                event.getCustomer().getId().toString(), folderId, file);
    }

    public Map<String, Object> mediaForCustomer(UUID eventId, CustomerPrincipal principal) {
        EventBooking event = customerEvent(eventId, principal);
        ensureProvisioned(event);
        return dataService.listMedia(storageEventId(event), event.getCustomer().getId().toString(), null);
    }

    public DataServiceClient.MediaStream previewForCustomer(UUID eventId, String mediaId, String range, CustomerPrincipal principal) {
        EventBooking event = customerEvent(eventId, principal);
        ensureProvisioned(event);
        return dataService.openPreview(storageEventId(event), mediaId, event.getCustomer().getId().toString(), range);
    }

    public DataServiceClient.MediaStream previewForPhotographer(UUID eventId, String mediaId, String range, PhotographerPrincipal principal) {
        EventBooking event = photographerEvent(eventId, principal);
        ensureProvisioned(event);
        return dataService.openPreview(storageEventId(event), mediaId, event.getCustomer().getId().toString(), range);
    }

    public DataServiceClient.MediaStream originalForPhotographer(UUID eventId, String mediaId, PhotographerPrincipal principal) {
        EventBooking event = photographerEvent(eventId, principal);
        ensureProvisioned(event);
        return dataService.openOriginal(storageEventId(event), mediaId, principal.getId().toString(), event.getCustomer().getId().toString());
    }

    @Transactional
    public Map<String, Object> submitAlbum(UUID eventId, CustomerPrincipal principal, List<String> photoIds) {
        EventBooking event = customerEvent(eventId, principal);
        ensureProvisioned(event);
        Map<String, Object> submission = dataService.submitAlbum(storageEventId(event),
                event.getPhotographer().getId().toString(), event.getCustomer().getId().toString(), photoIds);
        Object link = submission.get("album_design_link");
        if (!(link instanceof String albumLink) || albumLink.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The album-design link was not returned by the data service.");
        }
        event.setEventGallery(albumLink);
        events.save(event);
        notifyPhotographer(event, submission, albumLink);
        return submission;
    }

    private void notifyPhotographer(EventBooking event, Map<String, Object> submission, String albumLink) {
        String mobile = event.getPhotographer().getMobileNumber();
        if (mobile == null || mobile.isBlank()) {
            LOGGER.warn("Album design was submitted for event {}, but the photographer has no mobile number.", event.getEventCode());
            return;
        }
        try {
            Object rawNumbers = submission.get("photo_numbers");
            String photoNumbers = rawNumbers instanceof List<?> numbers
                    ? numbers.stream().map(item -> {
                        if (item instanceof Map<?, ?> map && map.get("photo_number") != null) return String.valueOf(map.get("photo_number"));
                        return String.valueOf(item);
                    }).collect(Collectors.joining(", "))
                    : String.valueOf(submission.getOrDefault("photo_count", ""));
            String whatsappNumber = "91" + IndianMobileNumber.normalize(mobile);
            whatsAppSender.sendTemplate(whatsappNumber, albumTemplate,
                    List.of(event.getEventCode(), photoNumbers, albumLink));
        } catch (RuntimeException exception) {
            LOGGER.warn("Album design link saved for event {}, but the WhatsApp notification could not be sent.", event.getEventCode(), exception);
        }
    }

    private void ensureProvisioned(EventBooking event) {
        dataService.createEvent(storageEventId(event), event.getEventCode(),
                event.getPhotographer().getId().toString(), event.getCustomer().getId().toString());
    }

    private static String storageEventId(EventBooking event) {
        return event.getId().toString();
    }

    private EventBooking photographerEvent(UUID eventId, PhotographerPrincipal principal) {
        if (!principal.isDataModuleEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The Data module is not included with your current plan.");
        }
        return events.findByIdAndPhotographer_Id(eventId, principal.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found."));
    }

    private EventBooking customerEvent(UUID eventId, CustomerPrincipal principal) {
        return events.findByIdAndCustomer_IdIn(eventId, principal.getCustomerIds())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found."));
    }
}
