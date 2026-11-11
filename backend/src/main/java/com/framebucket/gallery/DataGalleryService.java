package com.framebucket.gallery;

import com.framebucket.customer.model.CustomerPrincipal;
import com.framebucket.enquiry.notification.EnquiryEmailSender;
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
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DataGalleryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataGalleryService.class);

    private final EventBookingRepository events;
    private final DataServiceClient dataService;
    private final EnquiryEmailSender emailSender;
    private final WhatsAppTemplateSender whatsAppSender;
    private final GuestPhotoDeliveryService guestPhotoDeliveryService;
    private final String albumTemplate;
    private final String albumTemplateLanguage;

    public DataGalleryService(
            EventBookingRepository events,
            DataServiceClient dataService,
            EnquiryEmailSender emailSender,
            WhatsAppTemplateSender whatsAppSender,
            GuestPhotoDeliveryService guestPhotoDeliveryService,
            @Value("${app.whatsapp.album-design-template:framebucket_album_selection_finalized}") String albumTemplate,
            @Value("${app.whatsapp.album-design-language:${app.whatsapp.language:en}}") String albumTemplateLanguage
    ) {
        this.events = events;
        this.dataService = dataService;
        this.emailSender = emailSender;
        this.whatsAppSender = whatsAppSender;
        this.guestPhotoDeliveryService = guestPhotoDeliveryService;
        this.albumTemplate = albumTemplate;
        this.albumTemplateLanguage = albumTemplateLanguage;
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
        return dataService.listMedia(storageEventId(event), event.getCustomer().getId().toString(), folderId, true);
    }

    /**
     * Registers an upload and returns where the browser should PUT the file. With S3-compatible
     * storage this is a short-lived presigned bucket URL; otherwise it is this application's
     * same-origin content endpoint, which streams the bytes to the data service.
     */
    public Map<String, Object> initUpload(UUID eventId, PhotographerPrincipal principal, String folderId,
                                          String filename, long sizeBytes, String contentType) {
        EventBooking event = photographerEvent(eventId, principal);
        ensureProvisioned(event);
        Map<String, Object> grant = new java.util.LinkedHashMap<>(dataService.initUpload(
                storageEventId(event), principal.getId().toString(), event.getCustomer().getId().toString(),
                folderId, filename, sizeBytes, contentType));
        boolean direct = grant.get("upload_url") instanceof String url && !url.isBlank();
        if (!direct) {
            grant.put("upload_url", "/api/events/" + eventId + "/gallery/uploads/" + grant.get("photo_id") + "/content");
            grant.put("upload_headers", Map.of("Content-Type", "application/octet-stream"));
        }
        grant.put("same_origin", !direct);
        return grant;
    }

    public void uploadContent(UUID eventId, String mediaId, PhotographerPrincipal principal,
                              java.io.InputStream content, long contentLength) {
        EventBooking event = photographerEvent(eventId, principal);
        dataService.uploadContent(storageEventId(event), mediaId, principal.getId().toString(),
                event.getCustomer().getId().toString(), content, contentLength);
    }

    public Map<String, Object> completeUpload(UUID eventId, String mediaId, PhotographerPrincipal principal) {
        EventBooking event = photographerEvent(eventId, principal);
        return dataService.completeUpload(storageEventId(event), mediaId, principal.getId().toString(),
                event.getCustomer().getId().toString());
    }

    public String previewUrlForPhotographer(UUID eventId, String mediaId, PhotographerPrincipal principal) {
        EventBooking event = photographerEvent(eventId, principal);
        return dataService.previewUrl(storageEventId(event), mediaId, event.getCustomer().getId().toString());
    }

    public String previewUrlForCustomer(UUID eventId, String mediaId, CustomerPrincipal principal) {
        EventBooking event = customerEvent(eventId, principal);
        return dataService.previewUrl(storageEventId(event), mediaId, event.getCustomer().getId().toString());
    }

    public String originalUrlForPhotographer(UUID eventId, String mediaId, PhotographerPrincipal principal) {
        EventBooking event = photographerEvent(eventId, principal);
        return dataService.originalUrl(storageEventId(event), mediaId, principal.getId().toString(),
                event.getCustomer().getId().toString());
    }

    public Map<String, Object> mediaForCustomer(UUID eventId, CustomerPrincipal principal) {
        EventBooking event = customerEvent(eventId, principal);
        ensureProvisioned(event);
        return dataService.listMedia(storageEventId(event), event.getCustomer().getId().toString(), null);
    }

    public Map<String, Object> foldersForCustomer(UUID eventId, CustomerPrincipal principal) {
        EventBooking event = customerEvent(eventId, principal);
        ensureProvisioned(event);
        return dataService.listFolders(storageEventId(event), event.getPhotographer().getId().toString(),
                event.getCustomer().getId().toString());
    }

    public Map<String, Object> selectionForCustomer(UUID eventId, CustomerPrincipal principal) {
        EventBooking event = customerEvent(eventId, principal);
        ensureProvisioned(event);
        return dataService.albumSelection(storageEventId(event), event.getPhotographer().getId().toString(),
                event.getCustomer().getId().toString());
    }

    public Map<String, Object> saveSelectionForCustomer(UUID eventId, CustomerPrincipal principal,
                                                        List<String> photoIds) {
        EventBooking event = customerEvent(eventId, principal);
        ensureProvisioned(event);
        return dataService.saveAlbumSelection(storageEventId(event), event.getPhotographer().getId().toString(),
                event.getCustomer().getId().toString(), photoIds);
    }

    public DataServiceClient.MediaStream downloadForCustomer(UUID eventId, CustomerPrincipal principal,
                                                              List<String> photoIds) {
        EventBooking event = customerEvent(eventId, principal);
        ensureProvisioned(event);
        return dataService.downloadCustomerGallery(storageEventId(event), event.getCustomer().getId().toString(),
                photoIds);
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
        // The data service packages the RAW files in the background; the photographer is notified
        // when the archive is ready (see DataServiceOutboxPoller -> notifyAlbumReady).
        event.setEventGallery(albumLink);
        events.save(event);
        Map<String, Object> customerResult = new java.util.LinkedHashMap<>();
        customerResult.put("status", submission.getOrDefault("status", "SUBMITTED"));
        customerResult.put("photo_count", submission.getOrDefault("photo_count", 0));
        customerResult.put("photo_numbers", submission.getOrDefault("photo_numbers", List.of()));
        return customerResult;
    }

    @Transactional(readOnly = true)
    public void notifyAlbumReady(UUID eventId) {
        EventBooking event = events.findById(eventId).orElse(null);
        if (event == null || event.getEventGallery() == null || event.getEventGallery().isBlank()) {
            LOGGER.warn("An album archive became ready for unknown event {} or the event has no album link.", eventId);
            return;
        }
        notifyPhotographer(event, event.getEventGallery());
    }

    private void notifyPhotographer(EventBooking event, String albumLink) {
        String customerName = event.getCustomer().getName();
        String body = "Hello,\n\nYour customer (" + customerName
                + ") has finalised the Photo selection for Album designing. Please find the Raw photos link.\n\n"
                + "Link: " + albumLink + "\n\nRegards,\nframebucket.in";
        String email = event.getPhotographer().getEmail();
        if (email == null || email.isBlank()) {
            LOGGER.warn("Album design was submitted for event {}, but the photographer has no email address.",
                    event.getEventCode());
        } else {
            try {
                emailSender.send(email, "Photo selection finalized for album design", body);
            } catch (RuntimeException exception) {
                LOGGER.warn("Album design link saved for event {}, but the photographer email could not be sent.",
                        event.getEventCode(), exception);
            }
        }

        String mobile = event.getPhotographer().getMobileNumber();
        if (mobile == null || mobile.isBlank()) {
            LOGGER.warn("Album design was submitted for event {}, but the photographer has no mobile number.", event.getEventCode());
        } else {
            try {
                String whatsappNumber = "91" + IndianMobileNumber.normalize(mobile);
                whatsAppSender.sendTemplate(whatsappNumber, albumTemplate, albumTemplateLanguage, List.of(customerName, albumLink));
            } catch (RuntimeException exception) {
                LOGGER.warn("Album design link saved for event {}, but the WhatsApp notification could not be sent.",
                        event.getEventCode(), exception);
            }
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
