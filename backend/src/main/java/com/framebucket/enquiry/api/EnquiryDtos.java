package com.framebucket.enquiry.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class EnquiryDtos {
    private EnquiryDtos() {
    }

    public record CreateEnquiryRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Pattern(regexp = "^(?:[0-9]{10}|\\+91[0-9]{10})$") @Size(max = 13) String mobileNumber,
            @NotBlank @Email @Size(max = 320) String email,
            @NotNull UUID eventTypeId
    ) {
    }

    public record EnquiryResponse(
            UUID id,
            String enquiryId,
            String eventName,
            String name,
            String mobileNumber,
            String email,
            UUID eventTypeId,
            UUID quotationId,
            BigDecimal quotationPrice,
            String convertedCustomerCode,
            BigDecimal finalizedQuote,
            UUID createdEventId,
            String createdEventCode,
            Instant createdAt
    ) {
    }

    public record ConvertEnquiryRequest(
            @NotNull @Past LocalDate dateOfBirth,
            @NotBlank @Size(max = 1000) String address
    ) {
    }

    public record CustomerConversionResponse(UUID customerId, String customerCode, boolean created) {
    }

    public record FinalizedQuoteRequest(
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal finalizedQuote
    ) {
    }

    public record FinalizedQuoteResponse(BigDecimal finalizedQuote) {
    }

    public record EnquiryEventSetupResponse(
            UUID eventTypeId,
            String eventTypeName,
            LocalDate suggestedEventDate,
            boolean customerExists,
            UUID existingCustomerId,
            String existingCustomerCode,
            List<LocalDate> blockedDates
    ) {
    }

    public record CreateEventFromEnquiryRequest(
            @NotNull LocalDate eventDate,
            @Past LocalDate dateOfBirth,
            @Size(max = 1000) String address
    ) {
    }

    public record CreateEventFromEnquiryResponse(
            UUID eventId,
            String eventCode,
            UUID customerId,
            String customerCode,
            boolean customerCreated,
            BigDecimal finalizedQuote
    ) {
    }

    public record EventTypeOption(UUID id, String name) {
    }

    public record QuoteServiceOption(String id, String name, String description) {
    }

    public record QuotePackageOption(UUID id, String name, String description, List<String> inclusions,
                                     BigDecimal price, boolean highlighted) {
    }

    public record PublicQuoteContext(
            String customerName,
            String customerMobile,
            String customerEmail,
            String enquiryEvent,
            String studioName,
            List<EventTypeOption> eventTypes,
            List<QuoteServiceOption> services,
            boolean alreadySubmitted,
            String submittedPdfUrl,
            BigDecimal submittedFinalPrice,
            List<QuoteServiceOption> addons,
            List<QuotePackageOption> packages,
            String submittedPackageName,
            List<String> submittedAddonNames,
            Object contract,
            Object brand
    ) {
    }

    public record QuoteEventRequest(
            @NotNull UUID eventTypeId,
            @NotNull @Size(max = 9) List<@NotBlank @Size(max = 80) String> serviceIds
    ) {
    }

    public record QuoteSubmissionRequest(
            @Size(max = 240) String venue,
            LocalDate eventDate,
            @Size(max = 40) String preferredTime,
            @NotEmpty @Size(max = 8) List<@Valid QuoteEventRequest> events,
            @Min(0) @Max(500) int albumSheets,
            UUID packageId,
            @Size(max = 20) List<@NotBlank @Size(max = 80) String> addonIds
    ) {
    }

    public record QuoteCalculationResponse(BigDecimal finalPrice) {
    }

    public record QuoteSubmissionResponse(String quoteNumber, BigDecimal finalPrice, String pdfUrl) {
    }
}
