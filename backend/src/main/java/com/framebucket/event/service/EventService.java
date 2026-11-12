package com.framebucket.event.service;

import com.framebucket.customer.model.Customer;
import com.framebucket.customer.model.CustomerPrincipal;
import com.framebucket.customer.api.CustomerAuthDtos.CustomerEventResponse;
import com.framebucket.customer.repository.CustomerRepository;
import com.framebucket.enquiry.repository.EnquiryRepository;
import com.framebucket.event.api.EventDtos.EventCreateRequest;
import com.framebucket.event.api.EventDtos.EventIdPreview;
import com.framebucket.event.api.EventDtos.EventResponse;
import com.framebucket.event.api.EventDtos.EventTypeResponse;
import com.framebucket.event.api.EventDtos.AssigneeUpdateRequest;
import com.framebucket.event.api.EventDtos.CustomerLocationUpdateRequest;
import com.framebucket.event.model.EventBooking;
import com.framebucket.event.model.EventType;
import com.framebucket.gallery.DataGalleryService;
import com.framebucket.event.repository.EventBookingRepository;
import com.framebucket.event.repository.EventTypeRepository;
import com.framebucket.event.notification.EventAssignmentNotice;
import com.framebucket.event.notification.EventDetailsUpdatedNotice;
import com.framebucket.team.model.Freelancer;
import com.framebucket.team.model.MemberType;
import com.framebucket.team.model.TeamMember;
import com.framebucket.team.model.TeamMemberStatus;
import com.framebucket.team.repository.FreelancerRepository;
import com.framebucket.team.repository.TeamMemberRepository;
import com.framebucket.user.auth.model.PhotographerPrincipal;
import com.framebucket.user.auth.model.PhotographerUser;
import com.framebucket.user.auth.repository.PhotographerUserRepository;
import com.framebucket.user.auth.service.SubIdService;
import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class EventService {
    // Plan features and limits (see com.framebucket.subscription.EntitlementService).
    @org.springframework.beans.factory.annotation.Autowired
    private com.framebucket.subscription.EntitlementService entitlements;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private final EventBookingRepository eventRepository;
    private final EventTypeRepository eventTypeRepository;
    private final CustomerRepository customerRepository;
    private final EnquiryRepository enquiryRepository;
    private final PhotographerUserRepository photographerRepository;
    private final SubIdService subIdService;
    private final TeamMemberRepository teamMemberRepository;
    private final FreelancerRepository freelancerRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final DataGalleryService dataGalleryService;

    public EventService(
            EventBookingRepository eventRepository,
            EventTypeRepository eventTypeRepository,
            CustomerRepository customerRepository,
            EnquiryRepository enquiryRepository,
            PhotographerUserRepository photographerRepository,
            SubIdService subIdService,
            TeamMemberRepository teamMemberRepository,
            FreelancerRepository freelancerRepository,
            ApplicationEventPublisher eventPublisher,
            DataGalleryService dataGalleryService
    ) {
        this.eventRepository = eventRepository;
        this.eventTypeRepository = eventTypeRepository;
        this.customerRepository = customerRepository;
        this.enquiryRepository = enquiryRepository;
        this.photographerRepository = photographerRepository;
        this.subIdService = subIdService;
        this.teamMemberRepository = teamMemberRepository;
        this.freelancerRepository = freelancerRepository;
        this.eventPublisher = eventPublisher;
        this.dataGalleryService = dataGalleryService;
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

    // Dates held by signed booking agreements (com.framebucket.bookings.DateHolds).
    @org.springframework.beans.factory.annotation.Autowired
    private com.framebucket.bookings.DateHolds dateHolds;

    @Transactional(readOnly = true)
    public List<LocalDate> blockedDatesForPhotographer(PhotographerPrincipal photographer) {
        return java.util.stream.Stream.concat(
                        eventRepository.findAllByPhotographer_IdOrderByEventDateAsc(photographer.getId())
                                .stream()
                                .map(EventBooking::getEventDate),
                        dateHolds.heldDates(photographer.getId()).stream())
                .distinct()
                .sorted()
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
        entitlements.requireCanAddUpcomingEvent(photographer.getId(), request.eventDate());
        PhotographerUser owner = photographerRepository.findByIdForUpdate(photographer.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photographer account not found."));

        if (eventRepository.existsByPhotographer_IdAndEventDate(owner.getId(), request.eventDate())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That date is already booked for another event.");
        }
        if (dateHolds.holder(owner.getId(), request.eventDate()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "That date is on hold for a signed booking agreement. Create the event from that enquiry, or cancel its contract first.");
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
        dataGalleryService.provisionBestEffort(event);
        return toResponse(event, null);
    }

    @Transactional
    public EventResponse updateAssignee(
            PhotographerPrincipal photographer,
            UUID eventId,
            AssigneeUpdateRequest request
    ) {
        EventBooking event = eventRepository.findByIdAndPhotographer_Id(eventId, photographer.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found."));

        UUID previousAssigneeId = event.getTeamMemberAssignee() != null
                ? event.getTeamMemberAssignee().getId()
                : event.getFreelancerAssignee() != null ? event.getFreelancerAssignee().getId() : null;
        MemberType previousAssigneeType = event.getTeamMemberAssignee() != null
                ? MemberType.TEAM_MEMBER
                : event.getFreelancerAssignee() != null ? MemberType.FREELANCER : null;

        TeamMember teamMember = null;
        Freelancer freelancer = null;
        if (request.assigneeId() != null) {
            if (request.assigneeType() == MemberType.TEAM_MEMBER) {
                teamMember = teamMemberRepository.findByIdAndPhotographer_Id(request.assigneeId(), photographer.getId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team member not found."));
                if (teamMember.getStatus() != TeamMemberStatus.ACTIVE) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Only active team members can be assigned to an event.");
                }
            } else {
                freelancer = freelancerRepository.findByIdAndPhotographer_Id(request.assigneeId(), photographer.getId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Freelancer not found."));
                if (freelancer.getStatus() != TeamMemberStatus.ACTIVE) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Only active freelancers can be assigned to an event.");
                }
            }
        }
        event.assign(teamMember, freelancer);
        if (request.assigneeId() != null
                && (!request.assigneeId().equals(previousAssigneeId) || request.assigneeType() != previousAssigneeType)) {
            eventPublisher.publishEvent(toAssignmentNotice(event, teamMember, freelancer));
        }
        return toResponse(event, null);
    }

    @Transactional
    public CustomerEventResponse updateCustomerLocation(
            CustomerPrincipal customer,
            UUID eventId,
            CustomerLocationUpdateRequest request
    ) {
        EventBooking event = eventRepository.findByIdAndCustomer_IdIn(eventId, customer.getCustomerIds())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booked event not found."));
        String locationName = request.locationName().trim();
        String locationMapUrl = normalizeGoogleMapsUrl(locationName, request.locationMapUrl());
        String landmark = blankToNull(request.landmark());
        String notes = blankToNull(request.notes());
        boolean changed = !java.util.Objects.equals(event.getLocationName(), locationName)
                || !java.util.Objects.equals(event.getLocationMapUrl(), locationMapUrl)
                || !java.util.Objects.equals(event.getLocationLandmark(), landmark)
                || !java.util.Objects.equals(event.getLocationNotes(), notes)
                || !java.util.Objects.equals(event.getEventTime(), request.eventTime());
        event.updateLocation(locationName, locationMapUrl, landmark, notes, request.eventTime(), java.time.Instant.now());
        if (changed) {
            // Location and time matter to everyone on the shoot: alert them on every change from the portal.
            eventPublisher.publishEvent(toDetailsUpdatedNotice(event));
        }
        return toCustomerEventResponse(event);
    }

    @Transactional(readOnly = true)
    public List<CustomerEventResponse> eventsForCustomers(List<UUID> customerIds) {
        if (customerIds.isEmpty()) {
            return List.of();
        }
        return eventRepository.findAllByCustomer_IdInOrderByEventDateDesc(customerIds)
                .stream()
                .map(EventService::toCustomerEventResponse)
                .toList();
    }

    private static EventResponse toResponse(EventBooking event, UUID enquiryId) {
        Customer customer = event.getCustomer();
        UUID assigneeId = event.getTeamMemberAssignee() != null
                ? event.getTeamMemberAssignee().getId()
                : event.getFreelancerAssignee() != null ? event.getFreelancerAssignee().getId() : null;
        MemberType assigneeType = event.getTeamMemberAssignee() != null
                ? MemberType.TEAM_MEMBER
                : event.getFreelancerAssignee() != null ? MemberType.FREELANCER : null;
        String assigneeName = event.getTeamMemberAssignee() != null
                ? event.getTeamMemberAssignee().getName()
                : event.getFreelancerAssignee() != null ? event.getFreelancerAssignee().getName() : null;
        return new EventResponse(
                event.getId(),
                event.getEventCode(),
                event.getEventDate(),
                event.getEventTime(),
                event.getEventType().getName(),
                event.getPrice(),
                customer.getId(),
                customer.getCustomerCode(),
                customer.getName(),
                enquiryId,
                event.getCreatedAt(),
                assigneeId,
                assigneeType,
                assigneeName,
                event.getLocationName(),
                event.getLocationMapUrl(),
                event.getLocationLandmark(),
                event.getLocationNotes(),
                event.getLocationUpdatedAt(),
                event.getEventGallery()
        );
    }

    @Transactional(readOnly = true)
    public String guestAccessTokenForPhotographer(UUID eventId, PhotographerPrincipal photographer) {
        return eventRepository.findByIdAndPhotographer_Id(eventId, photographer.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found."))
                .getGuestAccessToken();
    }

    private static CustomerEventResponse toCustomerEventResponse(EventBooking event) {
        String assigneeName = event.getTeamMemberAssignee() != null
                ? event.getTeamMemberAssignee().getName()
                : event.getFreelancerAssignee() != null ? event.getFreelancerAssignee().getName() : null;
        String photographerName = event.getPhotographer().getStudioName() == null
                ? event.getPhotographer().getFullName()
                : event.getPhotographer().getStudioName();
        return new CustomerEventResponse(
                event.getId(), event.getEventType().getName(), photographerName, event.getEventDate(), event.getEventTime(), "Booked",
                assigneeName, event.getLocationName(), event.getLocationMapUrl(), event.getLocationLandmark(),
                event.getLocationNotes(), event.getLocationUpdatedAt(),
                event.getEventGallery() != null && !event.getEventGallery().isBlank()
        );
    }

    private static EventAssignmentNotice toAssignmentNotice(EventBooking event, TeamMember teamMember, Freelancer freelancer) {
        PhotographerUser photographer = event.getPhotographer();
        Customer customer = event.getCustomer();
        var assignee = teamMember != null ? teamMember : freelancer;
        String studioName = blankToNull(photographer.getStudioName());
        String photographerName = photographer.getFullName();
        String eventLocation = blankToNull(event.getLocationName());
        if (eventLocation == null) {
            eventLocation = "To be confirmed";
        } else if (blankToNull(event.getLocationMapUrl()) != null) {
            eventLocation += " — " + event.getLocationMapUrl();
        }
        String eventTime = event.getEventTime() == null
                ? "To be confirmed"
                : event.getEventTime().format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH));
        return new EventAssignmentNotice(
                whatsappNumber(customer.getMobileNumber()),
                photographerName,
                blankToNull(photographer.getMobileNumber()) == null ? "Not provided" : photographer.getMobileNumber(),
                studioName == null ? photographerName : studioName,
                whatsappNumber(assignee.getMobileNumber()),
                event.getEventType().getName(),
                eventLocation,
                blankToNull(event.getLocationLandmark()) == null ? "Not provided" : event.getLocationLandmark(),
                eventTime,
                assignee.getName()
        );
    }

    private EventDetailsUpdatedNotice toDetailsUpdatedNotice(EventBooking event) {
        PhotographerUser photographer = event.getPhotographer();
        java.util.Map<String, EventDetailsUpdatedNotice.Recipient> crew = new java.util.LinkedHashMap<>();
        if (event.getTeamMemberAssignee() != null) {
            addRecipient(crew, event.getTeamMemberAssignee().getName(), event.getTeamMemberAssignee().getMobileNumber());
        }
        if (event.getFreelancerAssignee() != null) {
            addRecipient(crew, event.getFreelancerAssignee().getName(), event.getFreelancerAssignee().getMobileNumber());
        }
        jdbc.query("""
                SELECT COALESCE(t.name, f.name), COALESCE(t.mobile_number, f.mobile_number)
                FROM fb_event_crew c
                LEFT JOIN fb_team_member t ON t.id = c.team_member_id
                LEFT JOIN fb_freelancer f ON f.id = c.freelancer_id
                WHERE c.event_id = ? AND c.photographer_id = ?
                """, rs -> {
            addRecipient(crew, rs.getString(1), rs.getString(2));
        }, event.getId(), photographer.getId());

        java.util.Map<String, EventDetailsUpdatedNotice.Recipient> owner = new java.util.LinkedHashMap<>();
        addRecipient(owner, photographer.getFullName(), photographer.getMobileNumber());
        EventDetailsUpdatedNotice.Recipient studioOwner = owner.values().stream()
                .filter(recipient -> !crew.containsKey(recipient.whatsAppNumber()))
                .findFirst().orElse(null);

        String studioName = blankToNull(photographer.getStudioName()) == null
                ? photographer.getFullName() : photographer.getStudioName();
        String eventLocation = blankToNull(event.getLocationName()) == null ? "To be confirmed" : event.getLocationName();
        if (blankToNull(event.getLocationMapUrl()) != null) {
            eventLocation += " — " + event.getLocationMapUrl();
        }
        String eventTime = event.getEventTime() == null
                ? "To be confirmed"
                : event.getEventTime().format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH));
        String eventDate = event.getEventDate() == null
                ? "Date to be confirmed"
                : event.getEventDate().format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH));
        return new EventDetailsUpdatedNotice(
                List.copyOf(crew.values()),
                studioOwner,
                studioName,
                event.getCustomer().getName(),
                event.getEventType().getName(),
                eventDate,
                eventLocation,
                blankToNull(event.getLocationLandmark()) == null ? "Not provided" : event.getLocationLandmark(),
                eventTime
        );
    }

    private static void addRecipient(java.util.Map<String, EventDetailsUpdatedNotice.Recipient> recipients,
                                     String name, String mobileNumber) {
        if (mobileNumber == null || mobileNumber.isBlank()) {
            return;
        }
        try {
            String number = whatsappNumber(mobileNumber);
            recipients.putIfAbsent(number, new EventDetailsUpdatedNotice.Recipient(name == null ? "crew member" : name, number));
        } catch (IllegalArgumentException ignored) {
            // A number that isn't a valid Indian mobile can't receive WhatsApp; skip it.
        }
    }

    private static String whatsappNumber(String mobileNumber) {
        return "91" + com.framebucket.common.phone.IndianMobileNumber.normalize(mobileNumber);
    }

    private static String normalizeGoogleMapsUrl(String locationName, String submittedUrl) {
        if (submittedUrl == null || submittedUrl.isBlank()) {
            return "https://www.google.com/maps/search/?api=1&query="
                    + URLEncoder.encode(locationName, StandardCharsets.UTF_8).replace("+", "%20");
        }
        try {
            URI uri = URI.create(submittedUrl.trim());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            boolean googleMapsHost = host.equals("maps.app.goo.gl")
                    || (host.equals("goo.gl") && uri.getPath() != null && uri.getPath().startsWith("/maps"))
                    || host.equals("google.com") || host.equals("www.google.com") || host.equals("maps.google.com")
                    || host.endsWith(".google.com") || host.equals("google.co.in")
                    || host.equals("www.google.co.in") || host.equals("maps.google.co.in");
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !googleMapsHost) {
                throw new IllegalArgumentException("Use a secure Google Maps place link.");
            }
            return uri.toString();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid HTTPS Google Maps link, or leave it blank to create a map search link.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static EventTypeResponse toTypeResponse(EventType eventType) {
        return new EventTypeResponse(eventType.getId(), eventType.getName());
    }
}
