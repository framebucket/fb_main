package com.framebucket.event.api;

import com.framebucket.event.api.EventDtos.EventCreateRequest;
import com.framebucket.event.api.EventDtos.EventIdPreview;
import com.framebucket.event.api.EventDtos.EventResponse;
import com.framebucket.event.api.EventDtos.EventTypeCreateRequest;
import com.framebucket.event.api.EventDtos.EventTypeResponse;
import com.framebucket.event.service.EventService;
import com.framebucket.user.auth.model.PhotographerPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventController {
    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public List<EventResponse> listEvents(@AuthenticationPrincipal PhotographerPrincipal photographer) {
        return eventService.listForPhotographer(photographer);
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
