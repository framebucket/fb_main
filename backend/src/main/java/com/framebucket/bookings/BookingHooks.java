package com.framebucket.bookings;

import com.framebucket.delivery.BrandService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Integration points the existing enquiry flow calls into (kept here so EnquiryService stays small). */
@Component
public class BookingHooks {
    public record QuotationExtras(UUID packageId, String packageName, List<String> addonCodes) {
    }

    @PersistenceContext
    private EntityManager entityManager;

    private final JdbcTemplate jdbc;
    private final QuoteTokens tokens;
    private final DateHolds holds;
    private final ContractService contracts;
    private final BrandService brand;

    public BookingHooks(JdbcTemplate jdbc, QuoteTokens tokens, DateHolds holds, ContractService contracts, BrandService brand) {
        this.jdbc = jdbc;
        this.tokens = tokens;
        this.holds = holds;
        this.contracts = contracts;
        this.brand = brand;
    }

    public void afterEnquiryCreated(UUID enquiryId, String rawToken, String source, String message, LocalDate preferredDate) {
        tokens.remember(enquiryId, rawToken);
        jdbc.update("UPDATE fb_enquiries SET source = ?, message = ?, preferred_date = ? WHERE id = ?",
                source == null ? "MANUAL" : source, message, preferredDate == null ? null : Date.valueOf(preferredDate), enquiryId);
    }

    public void afterQuotationSaved(UUID enquiryId, UUID packageId, String packageName, List<String> addonCodes) {
        jdbc.update("UPDATE fb_quotaion SET package_id = ?, package_name = ?, addon_codes = ? WHERE enquiry_id = ?",
                packageId, packageName, addonCodes == null || addonCodes.isEmpty() ? null : String.join(",", addonCodes), enquiryId);
    }

    public QuotationExtras quotationExtras(UUID enquiryId) {
        return jdbc.query("SELECT package_id, package_name, addon_codes FROM fb_quotaion WHERE enquiry_id = ?",
                (rs, n) -> new QuotationExtras(rs.getObject(1, UUID.class), rs.getString(2),
                        ContractService.splitCodes(rs.getString(3))), enquiryId).stream().findFirst()
                .orElse(new QuotationExtras(null, null, List.of()));
    }

    public Object publicContract(UUID enquiryId) {
        return contracts.publicView(enquiryId);
    }

    public Object brand(UUID photographerId) {
        return brand.brandView(photographerId);
    }

    public void beforeEventFromEnquiry(UUID enquiryId) {
        holds.release(enquiryId);
    }

    public void afterEventFromEnquiry(UUID photographerId, UUID enquiryId, UUID eventId) {
        entityManager.flush(); // the new event was saved through JPA; make it visible to plain JDBC queries
        contracts.recordAdvanceIfNeeded(photographerId, enquiryId, eventId);
    }

    public List<LocalDate> excludeOwnHold(UUID enquiryId, List<LocalDate> blocked) {
        List<LocalDate> own = jdbc.query("SELECT hold_date FROM fb_date_hold WHERE enquiry_id = ?",
                (rs, n) -> rs.getDate(1).toLocalDate(), enquiryId);
        return own.isEmpty() ? blocked : blocked.stream().filter(date -> !own.contains(date)).toList();
    }

    public LocalDate contractDateOr(UUID enquiryId, LocalDate fallback) {
        return jdbc.query("SELECT event_date FROM fb_contract WHERE enquiry_id = ? AND status <> 'CANCELLED'",
                        (rs, n) -> rs.getDate(1) == null ? null : rs.getDate(1).toLocalDate(), enquiryId)
                .stream().filter(java.util.Objects::nonNull).findFirst().orElse(fallback);
    }

    public void markOpened(String rawToken) {
        tokens.markOpened(rawToken);
    }
}
