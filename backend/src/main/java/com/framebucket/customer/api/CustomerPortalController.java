package com.framebucket.customer.api;

import com.framebucket.customer.api.CustomerAuthDtos.CustomerEventResponse;
import com.framebucket.customer.model.CustomerPrincipal;
import com.framebucket.event.api.EventDtos.CustomerLocationUpdateRequest;
import com.framebucket.event.service.EventService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/customer-portal")
public class CustomerPortalController {
    private final EventService eventService;

    public CustomerPortalController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping("/events")
    public List<CustomerEventResponse> events(
            @AuthenticationPrincipal CustomerPrincipal customer
    ) {
        return eventService.eventsForCustomers(customer.getCustomerIds());
    }

    @PutMapping("/events/{eventId}/location")
    public CustomerEventResponse updateEventLocation(
            @AuthenticationPrincipal CustomerPrincipal customer,
            @PathVariable UUID eventId,
            @Valid @RequestBody CustomerLocationUpdateRequest request
    ) {
        return eventService.updateCustomerLocation(customer, eventId, request);
    }
}
