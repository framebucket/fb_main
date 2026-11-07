package com.framebucket.event.service;

import com.framebucket.customer.model.Customer;
import com.framebucket.customer.repository.CustomerRepository;
import com.framebucket.enquiry.repository.EnquiryRepository;
import com.framebucket.event.api.EventDtos.EventCreateRequest;
import com.framebucket.event.api.EventDtos.EventIdPreview;
import com.framebucket.event.api.EventDtos.EventResponse;
import com.framebucket.event.api.EventDtos.EventTypeResponse;
import com.framebucket.event.model.EventBooking;
import com.framebucket.event.model.EventType;
import com.framebucket.event.repository.EventBookingRepository;
import com.framebucket.event.repository.EventTypeRepository;
import com.framebucket.user.auth.model.PhotographerPrincipal;
import com.framebucket.user.auth.model.PhotographerUser;
import com.framebucket.user.auth.repository.PhotographerUserRepository;
import com.framebucket.user.auth.service.SubIdService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class EventService {
    private final EventBookingRepository eventRepository;
    private final EventTypeRepository eventTypeRepository;
    private final CustomerRepository customerRepository;
    private final EnquiryRepository enquiryRepository;
    private final PhotographerUserRepository photographerRepository;
    private final SubIdService subIdService;

    public EventService(
            EventBookingRepository eventRepository,
            EventTypeRepository eventTypeRepository,
            CustomerRepository customerRepository,
            EnquiryRepository enquiryRepository,
            PhotographerUserRepository photographerRepository,
            SubIdService subIdService
    ) {
        this.eventRepository = eventRepository;
        this.eventTypeRepository = eventTypeRepository;
        this.customerRepository = customerRepository;
        this.enquiryRepository = enquiryRepository;
        this.photographerRepository = photographerRepository;
        this.subIdService = subIdService;
    }

    @Transactional(readOnly = true)
    public List<EventResponse> listForPhotographer(PhotographerPrincipal photographer) {
        return eventRepository.findAllByPhotographer_IdOrderByEventDateAscCreatedAtAsc(photographer.getId())
                .stream()
                .map(event -> toResponse(
                        event,
                        enquiryRepository.findByCreatedEventIdAndPhotographer_Id(event.getId(), photographer.getId())
                                .map(enquiry -> enquiry.getId())
                                .orElse(null)
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public EventIdPreview nextEventIdForPhotographer(PhotographerPrincipal photographer) {
        PhotographerUser owner = photographerRepository.findById(photographer.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photographer account not found."));
        if (owner.getEventSequence() > 99999) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This photographer has reached the event ID limit.");
        }
        return new EventIdPreview(subIdService.getPlainSubId(owner) + "_" + String.format(Locale.ROOT, "%05d", owner.getEventSequence()));
    }

    @Transactional(readOnly = true)
    public List<LocalDate> blockedDatesForPhotographer(PhotographerPrincipal photographer) {
        return eventRepository.findAllByPhotographer_IdOrderByEventDateAsc(photographer.getId())
                .stream()
                .map(EventBooking::getEventDate)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EventTypeResponse> eventTypesForPhotographer(PhotographerPrincipal photographer) {
        return Stream.concat(
                        eventTypeRepository.findAllByPhotographerIsNullOrderByNameAsc().stream(),
                        eventTypeRepository.findAllByPhotographer_IdOrderByNameAsc(photographer.getId()).stream()
                )
                .map(EventService::toTypeResponse)
                .toList();
    }

    @Transactional
    public EventTypeResponse createEventType(PhotographerPrincipal photographer, String submittedName) {
        PhotographerUser owner = photographerRepository.findByIdForUpdate(photographer.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photographer account not found."));
        String name = submittedName.trim();
        if (eventTypeRepository.existsByPhotographerIsNullAndNameIgnoreCase(name)
                || eventTypeRepository.existsByPhotographer_IdAndNameIgnoreCase(photographer.getId(), name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That event type already exists.");
        }

        EventType eventType = eventTypeRepository.save(new EventType(owner, name));
        return toTypeResponse(eventType);
    }

    @Transactional
    public EventResponse createEvent(PhotographerPrincipal photographer, EventCreateRequest request) {
        PhotographerUser owner = photographerRepository.findByIdForUpdate(photographer.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photographer account not found."));

        if (eventRepository.existsByPhotographer_IdAndEventDate(owner.getId(), request.eventDate())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That date is already booked for another event.");
        }

        Customer customer = customerRepository.findByIdAndPhotographer_Id(request.customerId(), owner.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found."));

        EventType eventType = eventTypeRepository.findByIdAndPhotographerIsNull(request.eventTypeId())
                .or(() -> eventTypeRepository.findByIdAndPhotographer_Id(request.eventTypeId(), owner.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid event type."));

        int sequence;
        try {
            sequence = owner.allocateEventSequence();
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }

        String eventCode = subIdService.getPlainSubId(owner) + "_" + String.format(Locale.ROOT, "%05d", sequence);
        EventBooking event = eventRepository.save(new EventBooking(
                eventCode,
                owner,
                request.eventDate(),
                eventType,
                request.price(),
                customer
        ));
        customer.recordEventBooking();
        return toResponse(event, null);
    }

    @Transactional(readOnly = true)
    public List<com.framebucket.customer.api.CustomerAuthDtos.CustomerEventResponse> eventsForCustomers(List<UUID> customerIds) {
        if (customerIds.isEmpty()) {
            return List.of();
        }
        return eventRepository.findAllByCustomer_IdInOrderByEventDateDesc(customerIds)
                .stream()
                .map(event -> new com.framebucket.customer.api.CustomerAuthDtos.CustomerEventResponse(
                        event.getEventType().getName(),
                        event.getPhotographer().getStudioName() == null
                                ? event.getPhotographer().getFullName()
                                : event.getPhotographer().getStudioName(),
                        event.getEventDate(),
                        "Booked"
                ))
                .toList();
    }

    private static EventResponse toResponse(EventBooking event, UUID enquiryId) {
        Customer customer = event.getCustomer();
        return new EventResponse(
                event.getId(),
                event.getEventCode(),
                event.getEventDate(),
                event.getEventType().getName(),
                event.getPrice(),
                customer.getId(),
                customer.getCustomerCode(),
                customer.getName(),
                enquiryId,
                event.getCreatedAt()
        );
    }

    private static EventTypeResponse toTypeResponse(EventType eventType) {
        return new EventTypeResponse(eventType.getId(), eventType.getName());
    }
}
