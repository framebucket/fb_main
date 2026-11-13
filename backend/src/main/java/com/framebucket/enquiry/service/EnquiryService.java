package com.framebucket.enquiry.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framebucket.common.phone.IndianMobileNumber;
import com.framebucket.customer.model.Customer;
import com.framebucket.customer.repository.CustomerRepository;
import com.framebucket.enquiry.api.EnquiryDtos.ConvertEnquiryRequest;
import com.framebucket.enquiry.api.EnquiryDtos.CreateEventFromEnquiryRequest;
import com.framebucket.enquiry.api.EnquiryDtos.CreateEventFromEnquiryResponse;
import com.framebucket.enquiry.api.EnquiryDtos.CreateEnquiryRequest;
import com.framebucket.enquiry.api.EnquiryDtos.CustomerConversionResponse;
import com.framebucket.enquiry.api.EnquiryDtos.EnquiryEventSetupResponse;
import com.framebucket.enquiry.api.EnquiryDtos.EnquiryResponse;
import com.framebucket.enquiry.api.EnquiryDtos.EventTypeOption;
import com.framebucket.enquiry.api.EnquiryDtos.FinalizedQuoteRequest;
import com.framebucket.enquiry.api.EnquiryDtos.FinalizedQuoteResponse;
import com.framebucket.enquiry.api.EnquiryDtos.PublicQuoteContext;
import com.framebucket.enquiry.api.EnquiryDtos.QuoteCalculationResponse;
import com.framebucket.enquiry.api.EnquiryDtos.QuoteEventRequest;
import com.framebucket.enquiry.api.EnquiryDtos.QuoteServiceOption;
import com.framebucket.enquiry.api.EnquiryDtos.QuoteSubmissionRequest;
import com.framebucket.enquiry.api.EnquiryDtos.QuoteSubmissionResponse;
import com.framebucket.enquiry.model.Enquiry;
import com.framebucket.enquiry.model.Quotation;
import com.framebucket.enquiry.notification.EnquiryCreatedNotice;
import com.framebucket.enquiry.notification.QuotationSubmittedNotice;
import com.framebucket.enquiry.repository.EnquiryRepository;
import com.framebucket.enquiry.repository.QuotationRepository;
import com.framebucket.event.model.EventType;
import com.framebucket.event.api.EventDtos.EventCreateRequest;
import com.framebucket.event.api.EventDtos.EventResponse;
import com.framebucket.event.repository.EventTypeRepository;
import com.framebucket.event.service.EventService;
import com.framebucket.user.auth.model.PhotographerPrincipal;
import com.framebucket.user.auth.model.PhotographerUser;
import com.framebucket.user.auth.repository.PhotographerUserRepository;
import com.framebucket.user.auth.service.SubIdService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.math.RoundingMode;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class EnquiryService {
    // Plan features and limits (see com.framebucket.subscription.EntitlementService).
    @org.springframework.beans.factory.annotation.Autowired
    private com.framebucket.subscription.EntitlementService entitlements;

    // Lead source, quote links, contracts and date holds (see com.framebucket.bookings).
    @org.springframework.beans.factory.annotation.Autowired
    private com.framebucket.bookings.BookingHooks bookingHooks;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final DateTimeFormatter ENQUIRY_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSSSSSSSS", Locale.ROOT);

    private final EnquiryRepository enquiryRepository;
    private final QuotationRepository quotationRepository;
    private final CustomerRepository customerRepository;
    private final EventTypeRepository eventTypeRepository;
    private final EventService eventService;
    private final PhotographerUserRepository photographerRepository;
    private final SubIdService subIdService;
    private final QuotePricingService pricingService;
    private final QuotationPdfGenerator pdfGenerator;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final String frontendBaseUrl;

    public EnquiryService(
            EnquiryRepository enquiryRepository,
            QuotationRepository quotationRepository,
            CustomerRepository customerRepository,
            EventTypeRepository eventTypeRepository,
            EventService eventService,
            PhotographerUserRepository photographerRepository,
            SubIdService subIdService,
            QuotePricingService pricingService,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher,
            @Value("${app.frontend.base-url}") String frontendBaseUrl
    ) {
        this.enquiryRepository = enquiryRepository;
        this.quotationRepository = quotationRepository;
        this.customerRepository = customerRepository;
        this.eventTypeRepository = eventTypeRepository;
        this.eventService = eventService;
        this.photographerRepository = photographerRepository;
        this.subIdService = subIdService;
        this.pricingService = pricingService;
        this.pdfGenerator = new QuotationPdfGenerator();
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+\\z", "");
    }

    @Transactional(readOnly = true)
    public List<EnquiryResponse> listForPhotographer(PhotographerPrincipal photographer) {
        return enquiryRepository.findAllByPhotographer_IdOrderByCreatedAtDesc(photographer.getId())
                .stream()
                .map(enquiry -> toResponse(enquiry, quotationRepository.findByEnquiry_Id(enquiry.getId()).orElse(null)))
                .toList();
    }

    @Transactional
    public EnquiryResponse create(PhotographerPrincipal principal, CreateEnquiryRequest request) {
        return createFor(principal.getId(), request, "MANUAL", null, null);
    }

    /** An enquiry submitted through the studio's public enquiry form. */
    @Transactional
    public UUID createLead(UUID photographerId, CreateEnquiryRequest request, String source, String message,
                           java.time.LocalDate preferredDate) {
        return createFor(photographerId, request, source, message, preferredDate).id();
    }

    private EnquiryResponse createFor(UUID photographerId, CreateEnquiryRequest request, String source, String message,
                                      java.time.LocalDate preferredDate) {
        PhotographerUser photographer = photographerRepository.findByIdForUpdate(photographerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photographer account not found."));
        EventType eventType = findEventType(request.eventTypeId(), photographer.getId());
        String mobileNumber;
        try {
            mobileNumber = IndianMobileNumber.normalize(request.mobileNumber());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }

        String subId = subIdService.getPlainSubId(photographer);
        LocalDateTime timestamp = LocalDateTime.now();
        String enquiryId = subId + timestamp.format(ENQUIRY_TIMESTAMP);
        while (enquiryRepository.existsByPhotographer_IdAndEnquiryId(photographer.getId(), enquiryId)) {
            timestamp = timestamp.plusNanos(1);
            enquiryId = subId + timestamp.format(ENQUIRY_TIMESTAMP);
        }

        String token = createQuoteAccessToken();
        Enquiry enquiry = enquiryRepository.saveAndFlush(new Enquiry(
                enquiryId,
                photographer,
                eventType,
                request.name(),
                mobileNumber,
                request.email(),
                hashToken(token)
        ));
        bookingHooks.afterEnquiryCreated(enquiry.getId(), token, source, message, preferredDate);
        String quoteLink = frontendBaseUrl + "/quote/" + token;
        eventPublisher.publishEvent(new EnquiryCreatedNotice(
                enquiry.getName(),
                enquiry.getEmail(),
                enquiry.getMobileNumber(),
                displayName(photographer),
                quoteLink
        ));
        return toResponse(enquiry, null);
    }

    @Transactional
    public CustomerConversionResponse convertToCustomer(
            PhotographerPrincipal principal,
            UUID enquiryId,
            ConvertEnquiryRequest request
    ) {
        PhotographerUser photographer = photographerRepository.findByIdForUpdate(principal.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photographer account not found."));
        Enquiry enquiry = enquiryRepository.findByIdAndPhotographer_Id(enquiryId, photographer.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enquiry not found."));
        Customer customer = existingCustomer(enquiry);
        boolean created = customer == null;
        if (created) {
            entitlements.requireCanAddCustomer(photographer.getId());
            int customerSequence;
            try {
                customerSequence = photographer.allocateCustomerSequence();
            } catch (IllegalStateException exception) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
            }
            String customerCode = subIdService.getPlainSubId(photographer) + "_"
                    + String.format(Locale.ROOT, "%03d", customerSequence);
            customer = customerRepository.save(new Customer(
                    photographer,
                    customerCode,
                    enquiry.getName(),
                    enquiry.getEmail(),
                    enquiry.getMobileNumber(),
                    request.dateOfBirth(),
                    request.address()
            ));
        }
        enquiry.convertToCustomer(customer);
        return new CustomerConversionResponse(customer.getId(), customer.getCustomerCode(), created);
    }

    @Transactional
    public FinalizedQuoteResponse saveFinalizedQuote(
            PhotographerPrincipal principal,
            UUID enquiryId,
            FinalizedQuoteRequest request
    ) {
        Enquiry enquiry = enquiryRepository.findByIdAndPhotographer_IdForUpdate(enquiryId, principal.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enquiry not found."));
        if (quotationRepository.findByEnquiry_Id(enquiry.getId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Wait for the customer to submit a quotation first.");
        }
        if (enquiry.getCreatedEventId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The finalized quote is locked after the event is created.");
        }
        BigDecimal amount = request.finalizedQuote().setScale(2, RoundingMode.HALF_UP);
        enquiry.setFinalizedQuote(amount);
        return new FinalizedQuoteResponse(amount);
    }

    @Transactional(readOnly = true)
    public EnquiryEventSetupResponse eventSetup(PhotographerPrincipal principal, UUID enquiryId) {
        Enquiry enquiry = enquiryRepository.findByIdAndPhotographer_Id(enquiryId, principal.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enquiry not found."));
        if (quotationRepository.findByEnquiry_Id(enquiry.getId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Wait for the customer to submit a quotation first.");
        }
        if (enquiry.getFinalizedQuote() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Save a finalized quote before creating the event.");
        }
        Customer customer = existingCustomer(enquiry);
        Quotation quotation = quotationRepository.findByEnquiry_Id(enquiry.getId()).orElseThrow();
        return new EnquiryEventSetupResponse(
                enquiry.getEventType().getId(),
                enquiry.getEventType().getName(),
                bookingHooks.contractDateOr(enquiry.getId(), quotation.getPreferredEventDate()),
                customer != null,
                customer == null ? null : customer.getId(),
                customer == null ? null : customer.getCustomerCode(),
                bookingHooks.excludeOwnHold(enquiry.getId(), eventService.blockedDatesForPhotographer(principal))
        );
    }

    @Transactional
    public CreateEventFromEnquiryResponse createEventFromEnquiry(
            PhotographerPrincipal principal,
            UUID enquiryId,
            CreateEventFromEnquiryRequest request
    ) {
        PhotographerUser photographer = photographerRepository.findByIdForUpdate(principal.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photographer account not found."));
        Enquiry enquiry = enquiryRepository.findByIdAndPhotographer_IdForUpdate(enquiryId, photographer.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enquiry not found."));
        if (enquiry.getCreatedEventId() != null) {
            Customer customer = existingCustomer(enquiry);
            return new CreateEventFromEnquiryResponse(
                    enquiry.getCreatedEventId(),
                    enquiry.getCreatedEventCode(),
                    customer == null ? null : customer.getId(),
                    customer == null ? null : customer.getCustomerCode(),
                    false,
                    enquiry.getFinalizedQuote()
            );
        }
        if (enquiry.getFinalizedQuote() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Save a finalized quote before creating the event.");
        }
        Quotation quotation = quotationRepository.findByEnquiry_Id(enquiry.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Wait for the customer to submit a quotation first."));

        entitlements.requireCanAddUpcomingEvent(photographer.getId(), request.eventDate());
        Customer customer = existingCustomer(enquiry);
        boolean customerCreated = customer == null;
        if (customerCreated) {
            entitlements.requireCanAddCustomer(photographer.getId());
            if (request.dateOfBirth() == null || request.address() == null || request.address().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the new customer's date of birth and address.");
            }
            int customerSequence;
            try {
                customerSequence = photographer.allocateCustomerSequence();
            } catch (IllegalStateException exception) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
            }
            String customerCode = subIdService.getPlainSubId(photographer) + "_"
                    + String.format(Locale.ROOT, "%03d", customerSequence);
            customer = customerRepository.save(new Customer(
                    photographer,
                    customerCode,
                    enquiry.getName(),
                    enquiry.getEmail(),
                    enquiry.getMobileNumber(),
                    request.dateOfBirth(),
                    request.address()
            ));
        }

        BigDecimal finalAmount = enquiry.getFinalizedQuote().setScale(2, RoundingMode.HALF_UP);
        byte[] finalizedPdf = regenerateQuotationPdf(enquiry, quotation, finalAmount);
        bookingHooks.beforeEventFromEnquiry(enquiry.getId());
        EventResponse event = eventService.createEvent(principal, new EventCreateRequest(
                request.eventDate(),
                enquiry.getEventType().getId(),
                customer.getId(),
                finalAmount.toPlainString()
        ));
        enquiry.convertToCustomer(customer);
        enquiry.setCreatedEvent(event.id(), event.eventCode());
        quotation.replaceFinalPriceAndPdf(finalAmount, finalizedPdf);
        bookingHooks.afterEventFromEnquiry(photographer.getId(), enquiry.getId(), event.id());
        return new CreateEventFromEnquiryResponse(
                event.id(),
                event.eventCode(),
                customer.getId(),
                customer.getCustomerCode(),
                customerCreated,
                finalAmount
        );
    }

    private Customer existingCustomer(Enquiry enquiry) {
        return customerRepository.findByPhotographer_IdAndNameIgnoreCaseAndEmailIgnoreCaseAndMobileNumber(
                enquiry.getPhotographer().getId(),
                enquiry.getName(),
                enquiry.getEmail(),
                enquiry.getMobileNumber()
        ).orElse(null);
    }

    private byte[] regenerateQuotationPdf(Enquiry enquiry, Quotation quotation, BigDecimal finalAmount) {
        List<QuoteEventRequest> selections;
        try {
            selections = objectMapper.readValue(quotation.getEventSelectionsJson(), new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read the saved quotation selections.", exception);
        }
        List<EventType> selectedEventTypes = validateEventSelections(
                new QuoteSubmissionRequest(
                        quotation.getVenue(),
                        quotation.getPreferredEventDate(),
                        quotation.getPreferredTime(),
                        selections,
                        quotation.getAlbumSheets(), null, null, null
                ),
                enquiry.getPhotographer().getId()
        );
        List<QuotationPdfGenerator.EventSummary> summaries = buildEventSummaries(
                new QuoteSubmissionRequest(
                        quotation.getVenue(),
                        quotation.getPreferredEventDate(),
                        quotation.getPreferredTime(),
                        selections,
                        quotation.getAlbumSheets(), null, null, null
                ),
                selectedEventTypes,
                enquiry.getPhotographer().getId()
        );
        com.framebucket.bookings.BookingHooks.QuotationExtras extras = bookingHooks.quotationExtras(enquiry.getId());
        summaries = withExtras(summaries, enquiry.getPhotographer().getId(), extras.packageName(), extras.addonCodes());
        return pdfGenerator.generate(
                displayName(enquiry.getPhotographer()),
                quotation.getQuoteNumber(),
                enquiry.getName(),
                "+91" + enquiry.getMobileNumber(),
                enquiry.getEmail(),
                quotation.getOccasion(),
                quotation.getVenue(),
                quotation.getPreferredEventDate() == null ? null : quotation.getPreferredEventDate().toString(),
                quotation.getPreferredTime(),
                summaries,
                quotation.getAlbumSheets(),
                finalAmount
        );
    }

    @Transactional(readOnly = true)
    public PublicQuoteContext publicQuoteContext(String rawToken) {
        Enquiry enquiry = findByToken(rawToken);
        PhotographerUser photographer = enquiry.getPhotographer();
        Quotation quotation = quotationRepository.findByEnquiry_Id(enquiry.getId()).orElse(null);
        String pdfUrl = quotation == null ? null : publicPdfUrl(rawToken);
        return new PublicQuoteContext(
                enquiry.getName(),
                "+91" + enquiry.getMobileNumber(),
                enquiry.getEmail(),
                enquiry.getEventType().getName(),
                displayName(photographer),
                eventTypesFor(photographer.getId()),
                pricingService.serviceOptions(photographer.getId()),
                quotation != null,
                pdfUrl,
                quotation == null ? null : quotation.getFinalPrice(),
                pricingService.addonOptions(photographer.getId()),
                pricingService.packageOptions(photographer.getId()),
                pricingService.settings(photographer.getId()).albumSheetsPerEvent(),
                quotation == null ? null : bookingHooks.quotationExtras(enquiry.getId()).packageName(),
                quotation == null ? List.of() : pricingService.addonNames(photographer.getId(),
                        bookingHooks.quotationExtras(enquiry.getId()).addonCodes()),
                bookingHooks.publicContract(enquiry.getId()),
                bookingHooks.brand(photographer.getId())
        );
    }

    @Transactional(readOnly = true)
    public QuoteCalculationResponse calculate(String rawToken, QuoteSubmissionRequest request) {
        Enquiry enquiry = findByToken(rawToken);
        if (quotationRepository.findByEnquiry_Id(enquiry.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This quotation has already been submitted.");
        }
        validateEventSelections(request, enquiry.getPhotographer().getId());
        return pricingService.calculate(enquiry.getPhotographer().getId(), request);
    }

    @Transactional
    public QuoteSubmissionResponse submitQuotation(String rawToken, QuoteSubmissionRequest request) {
        Enquiry enquiry = findByToken(rawToken);
        if (quotationRepository.findByEnquiry_Id(enquiry.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This quotation has already been submitted.");
        }
        List<EventType> eventTypes = validateEventSelections(request, enquiry.getPhotographer().getId());
        UUID photographerId = enquiry.getPhotographer().getId();
        BigDecimal finalPrice = pricingService.calculate(photographerId, request).finalPrice();
        String packageName = request.packageId() == null ? null
                : pricingService.packageById(photographerId, request.packageId(), true).name();
        List<String> addonCodes = QuotePricingService.addonCodes(request);
        int albumSheets = pricingService.albumSheets(photographerId, request);
        List<QuotationPdfGenerator.EventSummary> summaries = withExtras(
                buildEventSummaries(request, eventTypes, photographerId), photographerId, packageName, addonCodes);
        String quoteNumber = "Q-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        byte[] pdf = pdfGenerator.generate(
                displayName(enquiry.getPhotographer()),
                quoteNumber,
                enquiry.getName(),
                "+91" + enquiry.getMobileNumber(),
                enquiry.getEmail(),
                enquiry.getEventType().getName(),
                request.venue(),
                request.eventDate() == null ? null : request.eventDate().toString(),
                request.preferredTime(),
                summaries,
                albumSheets,
                finalPrice
        );
        String selectionsJson;
        try {
            selectionsJson = objectMapper.writeValueAsString(request.events());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not save the quotation selections.", exception);
        }

        quotationRepository.saveAndFlush(new Quotation(
                enquiry,
                quoteNumber,
                enquiry.getName(),
                enquiry.getMobileNumber(),
                enquiry.getEmail(),
                enquiry.getEventType().getName(),
                request.venue(),
                request.eventDate(),
                request.preferredTime(),
                request.events().size(),
                selectionsJson,
                albumSheets,
                finalPrice,
                pdf
        ));
        bookingHooks.afterQuotationSaved(enquiry.getId(), request.packageId(), packageName, addonCodes);
        String pdfUrl = publicPdfUrl(rawToken);
        eventPublisher.publishEvent(new QuotationSubmittedNotice(
                enquiry.getPhotographer().getFullName(),
                enquiry.getPhotographer().getMobileNumber(),
                enquiry.getName(),
                frontendBaseUrl + pdfUrl
        ));
        return new QuoteSubmissionResponse(quoteNumber, finalPrice, pdfUrl);
    }

    @Transactional(readOnly = true)
    public byte[] publicQuotationPdf(String rawToken) {
        Enquiry enquiry = findByToken(rawToken);
        return quotationRepository.findByEnquiry_Id(enquiry.getId())
                .map(Quotation::getOriginalQuotePdf)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "The quotation PDF has not been submitted yet."));
    }

    @Transactional(readOnly = true)
    public byte[] photographerQuotationPdf(PhotographerPrincipal photographer, UUID enquiryId) {
        Enquiry enquiry = enquiryRepository.findByIdAndPhotographer_Id(enquiryId, photographer.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enquiry not found."));
        return quotationRepository.findByEnquiry_Id(enquiry.getId())
                .map(Quotation::getOriginalQuotePdf)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No quotation has been submitted for this enquiry."));
    }

    @Transactional(readOnly = true)
    public byte[] photographerFinalizedQuotationPdf(PhotographerPrincipal photographer, UUID enquiryId) {
        Enquiry enquiry = enquiryRepository.findByIdAndPhotographer_Id(enquiryId, photographer.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enquiry not found."));
        if (enquiry.getCreatedEventId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The finalized quotation is available after creating the event.");
        }
        return quotationRepository.findByEnquiry_Id(enquiry.getId())
                .map(Quotation::getQuotePdf)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No finalized quotation is available for this enquiry."));
    }

    private List<EventType> validateEventSelections(QuoteSubmissionRequest request, UUID photographerId) {
        return request.events().stream()
                .map(event -> findEventType(event.eventTypeId(), photographerId))
                .toList();
    }

    private List<QuotationPdfGenerator.EventSummary> withExtras(List<QuotationPdfGenerator.EventSummary> summaries,
                                                                UUID photographerId, String packageName, List<String> addonCodes) {
        List<QuotationPdfGenerator.EventSummary> all = new java.util.ArrayList<>(summaries);
        if (packageName != null) {
            all.add(0, new QuotationPdfGenerator.EventSummary("Package: " + packageName, List.of("As described in your selected package")));
        }
        List<String> addonNames = pricingService.addonNames(photographerId, addonCodes);
        if (!addonNames.isEmpty()) all.add(new QuotationPdfGenerator.EventSummary("Add-ons", addonNames));
        return all;
    }

    private List<QuotationPdfGenerator.EventSummary> buildEventSummaries(
            QuoteSubmissionRequest request,
            List<EventType> eventTypes,
            UUID photographerId
    ) {
        List<QuoteEventRequest> requestedEvents = request.events();
        List<QuotationPdfGenerator.EventSummary> summaries = new java.util.ArrayList<>();
        for (int index = 0; index < requestedEvents.size(); index++) {
            QuoteEventRequest event = requestedEvents.get(index);
            summaries.add(new QuotationPdfGenerator.EventSummary(
                    eventTypes.get(index).getName(),
                    pricingService.serviceNames(photographerId, event)
            ));
        }
        return summaries;
    }

    private List<EventTypeOption> eventTypesFor(UUID photographerId) {
        return Stream.concat(
                        eventTypeRepository.findAllByPhotographerIsNullOrderByNameAsc().stream(),
                        eventTypeRepository.findAllByPhotographer_IdOrderByNameAsc(photographerId).stream()
                )
                .map(type -> new EventTypeOption(type.getId(), type.getName()))
                .toList();
    }

    private EventType findEventType(UUID eventTypeId, UUID photographerId) {
        return eventTypeRepository.findByIdAndPhotographerIsNull(eventTypeId)
                .or(() -> eventTypeRepository.findByIdAndPhotographer_Id(eventTypeId, photographerId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose an available event type."));
    }

    private Enquiry findByToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Quotation link not found.");
        }
        return enquiryRepository.findByQuoteTokenHash(hashToken(rawToken))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quotation link not found."));
    }

    private String publicPdfUrl(String rawToken) {
        return "/api/public/enquiries/quote/" + rawToken + "/pdf";
    }

    private static EnquiryResponse toResponse(Enquiry enquiry, Quotation quotation) {
        return new EnquiryResponse(
                enquiry.getId(),
                enquiry.getEnquiryId(),
                enquiry.getEventType().getName(),
                enquiry.getName(),
                "+91" + enquiry.getMobileNumber(),
                enquiry.getEmail(),
                enquiry.getEventType().getId(),
                quotation == null ? null : quotation.getId(),
                quotation == null ? null : quotation.getOriginalPrice(),
                enquiry.getConvertedCustomer() == null ? null : enquiry.getConvertedCustomer().getCustomerCode(),
                enquiry.getFinalizedQuote(),
                enquiry.getCreatedEventId(),
                enquiry.getCreatedEventCode(),
                enquiry.getCreatedAt()
        );
    }

    private static String createQuoteAccessToken() {
        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private static String hashToken(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static String displayName(PhotographerUser photographer) {
        return photographer.getStudioName() == null ? photographer.getFullName() : photographer.getStudioName();
    }

}
