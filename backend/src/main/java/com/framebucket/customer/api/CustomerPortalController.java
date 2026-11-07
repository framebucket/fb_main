package com.framebucket.customer.api;

import com.framebucket.customer.api.CustomerAuthDtos.CustomerEventResponse;
import com.framebucket.customer.model.CustomerPrincipal;
import com.framebucket.event.service.EventService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
}
