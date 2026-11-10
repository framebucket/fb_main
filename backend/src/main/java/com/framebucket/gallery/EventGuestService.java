package com.framebucket.gallery;

import com.framebucket.common.phone.IndianMobileNumber;
import com.framebucket.event.model.EventBooking;
import com.framebucket.event.repository.EventBookingRepository;
import com.framebucket.gallery.GuestEventDtos.EventContext;
import com.framebucket.gallery.GuestEventDtos.GuestDeleteRequest;
import com.framebucket.gallery.GuestEventDtos.RegistrationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
public class EventGuestService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventGuestService.class);
    private static final long MAX_SELFIE_BYTES = 8L * 1024 * 1024;

    private final EventBookingRepository events;
    private final EventGuestRepository guests;
    private final DataServiceClient dataService;
    private final GuestPhotoDeliveryService photoDelivery;

    public EventGuestService(EventBookingRepository events, EventGuestRepository guests,
                             DataServiceClient dataService, GuestPhotoDeliveryService photoDelivery) {
        this.events = events;
        this.guests = guests;
        this.dataService = dataService;
        this.photoDelivery = photoDelivery;
    }

    @Transactional(readOnly = true)
    public EventContext publicContext(String token) {
        EventBooking event = eventByToken(token);
        String photographerName = event.getPhotographer().getStudioName();
        if (photographerName == null || photographerName.isBlank()) {
            photographerName = event.getPhotographer().getFullName();
        }
        return new EventContext(event.getEventType().getName(), event.getEventDate(), photographerName);
    }

    public RegistrationResponse register(String token, String name, String mobileNumber,
                                         boolean faceConsent, boolean whatsappConsent, MultipartFile selfie) {
        EventBooking event = eventByToken(token);
        if (!faceConsent || !whatsappConsent) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Consent to event face matching and WhatsApp delivery is required.");
        }
        String cleanedName = name == null ? "" : name.trim();
        if (cleanedName.isBlank() || cleanedName.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter your name (up to 120 characters)." );
        }
        if (!IndianMobileNumber.isValidInput(mobileNumber)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid 10 digit WhatsApp number, with or without +91.");
        }
        if (selfie == null || selfie.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a clear selfie to continue.");
        }
        if (selfie.getSize() > MAX_SELFIE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Selfies must be 8 MB or smaller.");
        }
        MediaType selfieType;
        try {
            selfieType = MediaType.parseMediaType(
                    selfie.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : selfie.getContentType());
        } catch (InvalidMediaTypeException exception) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Upload a JPG or PNG selfie.");
        }
        if (!MediaType.IMAGE_JPEG.equals(selfieType) && !MediaType.IMAGE_PNG.equals(selfieType)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Upload a JPG or PNG selfie.");
        }

        String normalizedMobile = "+91" + IndianMobileNumber.normalize(mobileNumber);
        Instant consentAt = Instant.now();
        EventGuest guest = guests.findByEvent_IdAndMobileNumber(event.getId(), normalizedMobile)
                .orElseGet(() -> new EventGuest(event, cleanedName, normalizedMobile, consentAt));
        guest.updateConsentAndName(cleanedName, consentAt);
        String deletionToken = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        guest.setDeletionTokenHash(sha256(deletionToken));
        guest = guests.save(guest);

        try {
            dataService.createEvent(event.getId().toString(), event.getEventCode(),
                    event.getPhotographer().getId().toString(), event.getCustomer().getId().toString());
            String filename = selfie.getOriginalFilename() == null || selfie.getOriginalFilename().isBlank()
                    ? "selfie.jpg"
                    : selfie.getOriginalFilename().replaceAll("[^A-Za-z0-9._-]", "_");
            Map<String, Object> result = dataService.registerGuestFace(
                    event.getId().toString(), guest.getId().toString(), event.getCustomer().getId().toString(),
                    selfie.getBytes(), filename, selfieType);
            Object matchedIds = result.get("matched_photo_ids");
            int matched = matchedIds instanceof java.util.List<?> values ? values.size() : 0;
            int queued = 0;
            try {
                queued = photoDelivery.enqueueExistingMatches(event, guest, matchedIds);
            } catch (RuntimeException exception) {
                // Keep registration successful if delivery queueing is temporarily unavailable.
                LOGGER.warn("Guest profile was registered for event {}, but initial photo deliveries could not be queued.",
                        event.getEventCode(), exception);
            }
            return new RegistrationResponse(
                    matched == 0
                            ? "You're registered. We'll send your matching event photos to WhatsApp as they are uploaded."
                            : "You're registered. Your matching photos are being prepared for WhatsApp.",
                    matched,
                    queued,
                    guest.getId().toString(),
                    deletionToken
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the uploaded selfie.", exception);
        }
    }

    @Transactional
    public void deleteGuest(String token, GuestDeleteRequest request) {
        EventBooking event = eventByToken(token);
        UUID guestId;
        try {
            guestId = UUID.fromString(request.guestId());
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Guest profile not found.");
        }
        EventGuest guest = guests.findByIdAndEvent_Id(guestId, event.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Guest profile not found."));
        String suppliedHash = sha256(request.deletionToken() == null ? "" : request.deletionToken());
        if (!MessageDigest.isEqual(guest.getDeletionTokenHash().getBytes(StandardCharsets.UTF_8),
                suppliedHash.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The face-data removal code is invalid.");
        }
        dataService.deleteGuestFace(event.getId().toString(), guest.getId().toString(), event.getCustomer().getId().toString());
        guests.delete(guest);
    }

    private EventBooking eventByToken(String token) {
        if (token == null || !token.matches("^[a-fA-F0-9]{64}$")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event check-in link not found.");
        }
        return events.findByGuestAccessToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event check-in link not found."));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
