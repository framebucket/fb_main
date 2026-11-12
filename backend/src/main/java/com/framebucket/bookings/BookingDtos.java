package com.framebucket.bookings;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class BookingDtos {
    private BookingDtos() {
    }

    // ---- Quote catalogue (photographer) -----------------------------------------------------------

    public record CatalogResponse(
            BigDecimal albumSheetPrice,
            BigDecimal discountPercent,
            BigDecimal advancePercent,
            String contractTerms,
            boolean customTerms,
            List<ItemView> services,
            List<ItemView> addons,
            List<PackageView> packages
    ) {
    }

    public record ItemView(UUID id, String code, String kind, String name, String description, BigDecimal price,
                           boolean active, int sortOrder) {
    }

    public record PackageView(UUID id, String name, String description, List<String> inclusions, BigDecimal price,
                              boolean highlighted, boolean active, int sortOrder) {
    }

    public record CatalogSettingsRequest(
            @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal albumSheetPrice,
            @NotNull @DecimalMin("0.00") @DecimalMax("90.00") BigDecimal discountPercent,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal advancePercent,
            @Size(max = 20000) String contractTerms
    ) {
    }

    public record ItemRequest(
            @NotBlank @Pattern(regexp = "SERVICE|ADDON") String kind,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 300) String description,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
            boolean active,
            @Min(0) @Max(10000) int sortOrder
    ) {
    }

    public record PackageRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            @Size(max = 40) List<@NotBlank @Size(max = 200) String> inclusions,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
            boolean highlighted,
            boolean active,
            @Min(0) @Max(10000) int sortOrder
    ) {
    }

    // ---- Lead capture ---------------------------------------------------------------------------

    public record LeadCaptureResponse(
            boolean ready,
            String slug,
            String formUrl,
            String websiteUrl,
            String instagramUrl,
            String whatsappUrl,
            String facebookUrl,
            String embedCode,
            String message
    ) {
    }

    public record PublicEnquiryForm(
            String studioName,
            String slug,
            List<EventTypeOption> eventTypes,
            boolean whatsappAvailable,
            Object brand
    ) {
    }

    public record EventTypeOption(UUID id, String name) {
    }

    public record PublicEnquiryRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Pattern(regexp = "^(?:[0-9]{10}|\\+91[0-9]{10})$") String mobileNumber,
            @NotBlank @Email @Size(max = 320) String email,
            @NotNull UUID eventTypeId,
            LocalDate preferredDate,
            @Size(max = 1000) String message,
            @Size(max = 20) String source,
            @Size(max = 200) String website
    ) {
    }

    public record PublicEnquiryResponse(String message, String whatsappUrl) {
    }

    // ---- Enquiry tracking (photographer list) ----------------------------------------------------

    public record EnquiryTracking(
            UUID enquiryId,
            String source,
            String message,
            LocalDate preferredDate,
            Instant quoteOpenedAt,
            Instant quoteReminderSentAt,
            boolean canRemind,
            ContractSummary contract
    ) {
    }

    public record ContractSummary(String status, BigDecimal totalAmount, BigDecimal advanceAmount, LocalDate eventDate,
                                  Instant sentAt, Instant signedAt, String signerName, String advanceReference,
                                  Instant advanceReportedAt, Instant advanceConfirmedAt) {
    }

    // ---- Contracts -------------------------------------------------------------------------------

    public record SendContractRequest(
            LocalDate eventDate,
            @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal totalAmount,
            @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal advanceAmount,
            @Size(max = 20000) String terms
    ) {
    }

    public record ContractDraft(LocalDate eventDate, BigDecimal totalAmount, BigDecimal advanceAmount, String terms,
                                ContractSummary current, List<LocalDate> blockedDates) {
    }

    public record ContractSendResponse(ContractSummary contract, String shareLink, boolean emailed) {
    }

    /** What the client sees on the quotation page once a contract has been sent. */
    public record PublicContractView(
            String status,
            String studioName,
            String terms,
            BigDecimal totalAmount,
            BigDecimal advanceAmount,
            LocalDate eventDate,
            Instant sentAt,
            Instant signedAt,
            String signerName,
            boolean upiAvailable,
            String upiId,
            String advanceReference,
            Instant advanceReportedAt,
            Instant advanceConfirmedAt
    ) {
    }

    public record SignContractRequest(
            @NotBlank @Size(max = 120) String signerName,
            @NotBlank @Size(max = 400000) String signature,
            boolean agree
    ) {
        @AssertTrue(message = "Please confirm that you agree to the terms.")
        public boolean isAgreed() {
            return agree;
        }
    }

    public record AdvanceReportRequest(@NotBlank @Size(max = 120) String reference) {
    }

    public record ConfirmAdvanceRequest(@Pattern(regexp = "UPI|CASH|BANK_TRANSFER|CARD|CHEQUE|OTHER") String method,
                                        LocalDate paidOn) {
    }
}
