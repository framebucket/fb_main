package com.framebucket.delivery;

import com.framebucket.customer.model.CustomerPrincipal;
import com.framebucket.gallery.DataServiceClient;
import com.framebucket.gallery.DataServiceClient.NamedBytes;
import com.framebucket.studio.StudioEvents;
import com.framebucket.subscription.EntitlementService;
import com.framebucket.subscription.Feature;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Wedding invitations in the client portal: the client enters the details (and optionally a couple photo),
 * gets five designs, can ask for new ones, then picks one to get an animated video and a poster to share.
 * Available when the studio's plan includes {@link Feature#AI_INVITATIONS}.
 */
@RestController
@RequestMapping("/api/customer-portal/events/{eventId}/invitation")
public class CustomerInvitationController {
    private static final long MAX_PHOTO_BYTES = 25L * 1024 * 1024;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private final StudioEvents events;
    private final EntitlementService entitlements;
    private final DataServiceClient dataService;

    public CustomerInvitationController(StudioEvents events, EntitlementService entitlements, DataServiceClient dataService) {
        this.events = events;
        this.entitlements = entitlements;
        this.dataService = dataService;
    }

    public record FinalizeRequest(@Min(0) @Max(20) int index) {
    }

    @GetMapping
    public Map<String, Object> get(@PathVariable UUID eventId, @AuthenticationPrincipal CustomerPrincipal customer) {
        StudioEvents.EventInfo event = events.forCustomer(eventId, customer.getCustomerIds());
        boolean allowed = entitlements.has(event.photographerId(), Feature.AI_INVITATIONS);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("planAllows", allowed);
        if (!allowed) return response;
        response.putAll(dataService.invitation(id(event), photographer(event), customer(event)));
        if (!Boolean.TRUE.equals(response.get("exists"))) {
            response.put("suggested", suggested(event));
        }
        return response;
    }

    @PutMapping
    public Map<String, Object> save(@PathVariable UUID eventId, @RequestBody Map<String, Object> details,
                                    @AuthenticationPrincipal CustomerPrincipal customer) {
        StudioEvents.EventInfo event = allowed(eventId, customer);
        return dataService.saveInvitation(id(event), photographer(event), customer(event), details == null ? Map.of() : details);
    }

    @PostMapping("/photo")
    public Map<String, Object> photo(@PathVariable UUID eventId, @RequestParam("file") MultipartFile file,
                                     @AuthenticationPrincipal CustomerPrincipal customer) {
        StudioEvents.EventInfo event = allowed(eventId, customer);
        if (file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a photo.");
        if (file.getSize() > MAX_PHOTO_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "The photo must be 25 MB or smaller.");
        String type = file.getContentType() == null ? "" : file.getContentType();
        if (!type.startsWith("image/")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a JPG or PNG photo.");
        try {
            return dataService.saveInvitationPhoto(id(event), photographer(event), customer(event),
                    new NamedBytes("couple.jpg", MediaType.parseMediaType(type), file.getBytes()));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the photo.", exception);
        }
    }

    @DeleteMapping("/photo")
    public Map<String, Object> deletePhoto(@PathVariable UUID eventId, @AuthenticationPrincipal CustomerPrincipal customer) {
        StudioEvents.EventInfo event = allowed(eventId, customer);
        return dataService.deleteInvitationPhoto(id(event), photographer(event), customer(event));
    }

    @PostMapping("/designs")
    public Map<String, Object> designs(@PathVariable UUID eventId, @AuthenticationPrincipal CustomerPrincipal customer) {
        StudioEvents.EventInfo event = allowed(eventId, customer);
        return dataService.generateInvitationDesigns(id(event), photographer(event), customer(event));
    }

    @GetMapping("/designs/{index}")
    public ResponseEntity<StreamingResponseBody> design(@PathVariable UUID eventId, @PathVariable int index,
                                                        @AuthenticationPrincipal CustomerPrincipal customer) {
        StudioEvents.EventInfo event = allowed(eventId, customer);
        return stream(dataService.invitationDesign(id(event), customer(event), index), CacheControl.noCache().cachePrivate(), null);
    }

    @PostMapping("/finalize")
    public Map<String, Object> finalizeDesign(@PathVariable UUID eventId, @RequestBody FinalizeRequest request,
                                              @AuthenticationPrincipal CustomerPrincipal customer) {
        StudioEvents.EventInfo event = allowed(eventId, customer);
        return dataService.finalizeInvitation(id(event), photographer(event), customer(event), request.index());
    }

    @GetMapping("/{kind:video|poster}")
    public ResponseEntity<StreamingResponseBody> file(@PathVariable UUID eventId, @PathVariable String kind,
                                                      @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
                                                      @AuthenticationPrincipal CustomerPrincipal customer) {
        StudioEvents.EventInfo event = allowed(eventId, customer);
        return stream(dataService.invitationFile(id(event), customer(event), kind, range), CacheControl.noStore(), range);
    }

    private StudioEvents.EventInfo allowed(UUID eventId, CustomerPrincipal customer) {
        StudioEvents.EventInfo event = events.forCustomer(eventId, customer.getCustomerIds());
        if (!entitlements.has(event.photographerId(), Feature.AI_INVITATIONS)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invitations aren't available from your studio yet.");
        }
        return event;
    }

    private static Map<String, Object> suggested(StudioEvents.EventInfo event) {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("title", event.eventTypeName() == null ? "" : event.eventTypeName());
        first.put("date", event.eventDate() == null ? "" : DATE.format(event.eventDate()));
        first.put("time", event.eventTime() == null ? "" : TIME.format(event.eventTime()));
        first.put("venue", event.locationName() == null ? "" : event.locationName());
        first.put("address", "");
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("kind", "WEDDING");
        details.put("events", List.of(first));
        details.put("rsvp", event.customerMobile() == null ? "" : "RSVP " + event.customerMobile());
        return details;
    }

    private static ResponseEntity<StreamingResponseBody> stream(DataServiceClient.MediaStream media, CacheControl cache, String range) {
        StreamingResponseBody body = output -> {
            try (InputStream input = media.body()) {
                input.transferTo(output);
            }
        };
        ResponseEntity.BodyBuilder response = ResponseEntity.status(media.status())
                .contentType(MediaType.parseMediaType(media.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, media.contentDisposition())
                .cacheControl(cache);
        if (media.contentRange() != null) response.header(HttpHeaders.CONTENT_RANGE, media.contentRange());
        if (media.acceptRanges() != null) response.header(HttpHeaders.ACCEPT_RANGES, media.acceptRanges());
        if (media.contentLength() >= 0) response.contentLength(media.contentLength());
        return response.body(body);
    }

    private static String id(StudioEvents.EventInfo event) {
        return event.id().toString();
    }

    private static String photographer(StudioEvents.EventInfo event) {
        return event.photographerId().toString();
    }

    private static String customer(StudioEvents.EventInfo event) {
        return event.customerId().toString();
    }
}
