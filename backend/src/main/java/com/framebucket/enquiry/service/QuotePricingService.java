package com.framebucket.enquiry.service;

import com.framebucket.enquiry.api.EnquiryDtos.QuoteCalculationResponse;
import com.framebucket.enquiry.api.EnquiryDtos.QuoteEventRequest;
import com.framebucket.enquiry.api.EnquiryDtos.QuoteServiceOption;
import com.framebucket.enquiry.api.EnquiryDtos.QuoteSubmissionRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class QuotePricingService {
    private record ServicePrice(String id, String name, String description, int price) {
    }

    private static final List<ServicePrice> CATALOG = List.of(
            new ServicePrice("photo", "Conventional Photography", "Traditional event coverage", 7000),
            new ServicePrice("video", "Conventional Videography", "Event video coverage", 8000),
            new ServicePrice("candid", "Candid Photography", "Natural, candid moments", 20000),
            new ServicePrice("candidvideo", "Candid Videography", "Cinematic candid film", 25000),
            new ServicePrice("drone", "Drone Videography", "Aerial coverage where permitted", 8000),
            new ServicePrice("led", "LED Wall", "On-site display setup", 8000),
            new ServicePrice("mixing", "Spot Mixing", "Live video mixing setup", 20000),
            new ServicePrice("qr", "Live Photo QR Scanner", "Guest photo access by QR", 3000),
            new ServicePrice("youtube", "YouTube Live", "Live streaming coverage", 6000)
    );

    private static final Map<LocalDate, String> FESTIVAL_DATES = Map.ofEntries(
            Map.entry(LocalDate.parse("2026-01-14"), "Makar Sankranti"),
            Map.entry(LocalDate.parse("2026-03-04"), "Holi"),
            Map.entry(LocalDate.parse("2026-08-28"), "Onam"),
            Map.entry(LocalDate.parse("2026-10-20"), "Dussehra"),
            Map.entry(LocalDate.parse("2026-11-08"), "Diwali"),
            Map.entry(LocalDate.parse("2027-01-14"), "Makar Sankranti"),
            Map.entry(LocalDate.parse("2027-03-22"), "Holi"),
            Map.entry(LocalDate.parse("2027-09-15"), "Onam"),
            Map.entry(LocalDate.parse("2027-10-09"), "Dussehra"),
            Map.entry(LocalDate.parse("2027-10-29"), "Diwali"),
            Map.entry(LocalDate.parse("2028-01-14"), "Makar Sankranti"),
            Map.entry(LocalDate.parse("2028-03-11"), "Holi"),
            Map.entry(LocalDate.parse("2028-09-04"), "Onam"),
            Map.entry(LocalDate.parse("2028-09-27"), "Dussehra"),
            Map.entry(LocalDate.parse("2028-10-17"), "Diwali")
    );

    private final Map<String, ServicePrice> pricesById;

    public QuotePricingService() {
        Map<String, ServicePrice> prices = new LinkedHashMap<>();
        CATALOG.forEach(service -> prices.put(service.id(), service));
        this.pricesById = Map.copyOf(prices);
    }

    public List<QuoteServiceOption> serviceOptions() {
        return CATALOG.stream()
                .map(service -> new QuoteServiceOption(service.id(), service.name(), service.description()))
                .toList();
    }

    public QuoteCalculationResponse calculate(QuoteSubmissionRequest request) {
        long serviceSubtotal = request.events().stream()
                .mapToLong(this::sumServices)
                .sum();
        long subtotal = serviceSubtotal + request.albumSheets() * 400L;
        long standardDiscount = Math.round(subtotal * 0.05d);
        boolean festivalCouponValid = "FESTFSK10".equalsIgnoreCase(nullToEmpty(request.couponCode()).trim())
                && request.eventDate() != null
                && FESTIVAL_DATES.containsKey(request.eventDate());
        long festivalDiscount = festivalCouponValid ? Math.round(subtotal * 0.05d) : 0;
        long finalTotal = Math.max(0L, subtotal - standardDiscount - festivalDiscount);
        return new QuoteCalculationResponse(BigDecimal.valueOf(finalTotal).setScale(2));
    }

    public String festivalName(LocalDate date) {
        return FESTIVAL_DATES.get(date);
    }

    public List<String> serviceNames(QuoteEventRequest event) {
        return event.serviceIds().stream()
                .map(this::servicePrice)
                .map(ServicePrice::name)
                .toList();
    }

    private long sumServices(QuoteEventRequest event) {
        return event.serviceIds().stream().mapToLong(id -> servicePrice(id).price()).sum();
    }

    private ServicePrice servicePrice(String serviceId) {
        ServicePrice service = pricesById.get(serviceId);
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A selected photo service is not available.");
        }
        return service;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
