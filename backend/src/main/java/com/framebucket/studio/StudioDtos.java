package com.framebucket.studio;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class StudioDtos {
    private StudioDtos() {
    }

    public static final String UPI_ID_PATTERN = "^[A-Za-z0-9.\\-_]{2,256}@[A-Za-z][A-Za-z0-9]{1,63}$";
    public static final String SLUG_PATTERN = "^[a-z0-9](?:[a-z0-9-]{1,58}[a-z0-9])$";

    // ---- Studio profile -------------------------------------------------------------------

    public record StudioProfileRequest(
            @Pattern(regexp = UPI_ID_PATTERN, message = "Enter a valid UPI ID, for example studio@okhdfcbank.") String upiId,
            @Size(max = 120) String upiPayeeName,
            @Size(max = 500) String businessAddress,
            @Pattern(regexp = "^$|^[0-9A-Za-z]{15}$", message = "GSTIN must be 15 letters/digits.") String gstin,
            @Size(max = 500) String receiptFooter,
            @Pattern(regexp = SLUG_PATTERN, message = "Use 3–60 lowercase letters, digits or hyphens for the portfolio address.") String portfolioSlug,
            @Size(max = 160) String portfolioHeadline,
            @Size(max = 2000) String portfolioBio,
            boolean portfolioPublic,
            boolean remindersEnabled
    ) {
    }

    public record StudioProfileResponse(
            String upiId,
            String upiPayeeName,
            String businessAddress,
            String gstin,
            String receiptFooter,
            String portfolioSlug,
            String portfolioHeadline,
            String portfolioBio,
            boolean portfolioPublic,
            boolean remindersEnabled,
            String studioName
    ) {
    }

    // ---- Payments, receipts and billing ---------------------------------------------------

    public enum PaymentMethod { UPI, CASH, BANK_TRANSFER, CARD, CHEQUE, OTHER }

    public enum PaymentPurpose { ADVANCE, BALANCE, DOWNLOAD, OTHER }

    public enum DownloadMode { FREE, PAID, DISABLED }

    public record PaymentCreateRequest(
            @NotNull @DecimalMin(value = "1.00") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @NotNull LocalDate paidOn,
            @NotNull PaymentMethod method,
            @NotNull PaymentPurpose purpose,
            @Size(max = 120) String reference,
            @Size(max = 500) String notes
    ) {
    }

    public record PaymentResponse(
            UUID id,
            String receiptNumber,
            BigDecimal amount,
            LocalDate paidOn,
            PaymentMethod method,
            PaymentPurpose purpose,
            String reference,
            String notes,
            Instant createdAt
    ) {
    }

    public record DownloadPolicyRequest(
            @NotNull DownloadMode mode,
            @DecimalMin(value = "1.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
            boolean unlocked
    ) {
    }

    public record DownloadPolicyResponse(DownloadMode mode, BigDecimal price, boolean unlocked, Instant unlockedAt) {
        public boolean downloadsAllowed() {
            return mode == DownloadMode.FREE || (mode == DownloadMode.PAID && unlocked);
        }
    }

    public record EventBillingResponse(
            UUID eventId,
            String eventCode,
            LocalDate eventDate,
            String eventTypeName,
            String customerName,
            BigDecimal total,
            BigDecimal paid,
            BigDecimal balance,
            BigDecimal expenses,
            boolean upiConfigured,
            DownloadPolicyResponse downloadPolicy,
            List<PaymentResponse> payments
    ) {
    }

    public record FinanceEventRow(
            UUID eventId,
            String eventCode,
            LocalDate eventDate,
            String eventTypeName,
            String customerName,
            BigDecimal total,
            BigDecimal paid,
            BigDecimal balance,
            BigDecimal expenses
    ) {
    }

    public record FinanceSummaryResponse(
            BigDecimal billed,
            BigDecimal received,
            BigDecimal outstanding,
            BigDecimal expenses,
            BigDecimal net,
            List<FinanceEventRow> events
    ) {
    }

    public record CustomerBillingResponse(
            UUID eventId,
            String eventName,
            String photographerName,
            BigDecimal total,
            BigDecimal paid,
            BigDecimal balance,
            boolean upiConfigured,
            DownloadPolicyResponse downloadPolicy,
            List<PaymentResponse> payments,
            List<FlipbookLink> flipbooks
    ) {
    }

    // ---- Expenses ---------------------------------------------------------------------------

    public record ExpenseRequest(
            UUID eventId,
            @NotBlank @Size(max = 40) String category,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @NotNull LocalDate spentOn,
            @Size(max = 160) String vendor,
            @Size(max = 500) String notes
    ) {
    }

    public record ExpenseResponse(
            UUID id,
            UUID eventId,
            String eventCode,
            String category,
            BigDecimal amount,
            LocalDate spentOn,
            String vendor,
            String notes,
            Instant createdAt
    ) {
    }

    // ---- Portfolio --------------------------------------------------------------------------

    public record PortfolioItemRequest(
            @NotNull UUID eventId,
            @NotEmpty @Size(max = 200) List<@NotBlank @Size(max = 128) String> mediaIds,
            @Size(max = 240) String caption
    ) {
    }

    public record PortfolioItemUpdateRequest(@Size(max = 240) String caption, int sortOrder) {
    }

    public record PortfolioItemResponse(
            UUID id,
            UUID eventId,
            String eventCode,
            String mediaId,
            String caption,
            int sortOrder,
            String imageUrl
    ) {
    }

    public record PublicPortfolioResponse(
            String studioName,
            String headline,
            String bio,
            String contactEmail,
            String contactMobile,
            List<PublicPortfolioItem> items,
            boolean poweredByFrameBucket
    ) {
    }

    public record PublicPortfolioItem(UUID id, String caption, String imageUrl) {
    }

    // ---- E-album flipbooks ------------------------------------------------------------------

    public record FlipbookRequest(
            @NotBlank @Size(max = 160) String title,
            @NotEmpty @Size(max = 400) List<@NotBlank @Size(max = 128) String> mediaIds
    ) {
    }

    public record FlipbookResponse(
            UUID id,
            UUID eventId,
            String title,
            int pageCount,
            List<String> mediaIds,
            String shareUrl,
            Instant createdAt
    ) {
    }

    public record FlipbookLink(String title, int pageCount, String url) {
    }

    public record PublicFlipbookResponse(
            String title,
            String studioName,
            String eventTypeName,
            LocalDate eventDate,
            LocalTime eventTime,
            int pageCount,
            boolean poweredByFrameBucket
    ) {
    }
}
