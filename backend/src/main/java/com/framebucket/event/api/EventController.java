package com.framebucket.event.api;

import com.framebucket.event.api.EventDtos.EventCreateRequest;
import com.framebucket.event.api.EventDtos.EventIdPreview;
import com.framebucket.event.api.EventDtos.EventResponse;
import com.framebucket.event.api.EventDtos.AssigneeUpdateRequest;
import com.framebucket.event.api.EventDtos.EventTypeCreateRequest;
import com.framebucket.event.api.EventDtos.EventTypeResponse;
import com.framebucket.event.service.EventService;
import com.framebucket.event.service.EventQrCodeService;
import com.framebucket.user.auth.model.PhotographerPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
public class EventController {
    private final EventService eventService;
    private final EventQrCodeService qrCodeService;

    public EventController(EventService eventService, EventQrCodeService qrCodeService) {
        this.eventService = eventService;
        this.qrCodeService = qrCodeService;
    }

    @GetMapping
    public List<EventResponse> listEvents(@AuthenticationPrincipal PhotographerPrincipal photographer) {
        return eventService.listForPhotographer(photographer);
    }

    @GetMapping(value = "/{eventId}/qr-code.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> eventQrCode(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal PhotographerPrincipal photographer
    ) {
        String token = eventService.guestAccessTokenForPhotographer(eventId, photographer);
        byte[] image = qrCodeService.guestEntryQrCode(token);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"FrameBucket-event-" + eventId + "-QR.png\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(MediaType.IMAGE_PNG)
                .body(image);
    }

    @GetMapping("/next-id")
    public EventIdPreview nextEventId(@AuthenticationPrincipal PhotographerPrincipal photographer) {
        return eventService.nextEventIdForPhotographer(photographer);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse createEvent(
            @AuthenticationPrincipal PhotographerPrincipal photographer,
            @Valid @RequestBody EventCreateRequest request
    ) {
        return eventService.createEvent(photographer, request);
    }

    @PutMapping("/{eventId}/assignee")
    public EventResponse assignEvent(
            @AuthenticationPrincipal PhotographerPrincipal photographer,
            @PathVariable UUID eventId,
            @Valid @RequestBody AssigneeUpdateRequest request
    ) {
        return eventService.updateAssignee(photographer, eventId, request);
    }

    @GetMapping("/blocked-dates")
    public List<LocalDate> blockedDates(@AuthenticationPrincipal PhotographerPrincipal photographer) {
        return eventService.blockedDatesForPhotographer(photographer);
    }

    @GetMapping("/types")
    public List<EventTypeResponse> eventTypes(@AuthenticationPrincipal PhotographerPrincipal photographer) {
        return eventService.eventTypesForPhotographer(photographer);
    }

    @PostMapping("/types")
    @ResponseStatus(HttpStatus.CREATED)
    public EventTypeResponse createEventType(
            @AuthenticationPrincipal PhotographerPrincipal photographer,
            @Valid @RequestBody EventTypeCreateRequest request
    ) {
        return eventService.createEventType(photographer, request.name());
    }
}
