package com.framebucket.bookings;

import com.framebucket.bookings.BookingDtos.AdvanceReportRequest;
import com.framebucket.bookings.BookingDtos.ConfirmAdvanceRequest;
import com.framebucket.bookings.BookingDtos.ContractDraft;
import com.framebucket.bookings.BookingDtos.ContractSendResponse;
import com.framebucket.bookings.BookingDtos.ContractSummary;
import com.framebucket.bookings.BookingDtos.PublicContractView;
import com.framebucket.bookings.BookingDtos.SendContractRequest;
import com.framebucket.bookings.BookingDtos.SignContractRequest;
import com.framebucket.common.notify.Notifier;
import com.framebucket.enquiry.service.QuotePricingService;
import com.framebucket.studio.BillingService;
import com.framebucket.studio.StudioDtos.PaymentCreateRequest;
import com.framebucket.studio.StudioDtos.PaymentMethod;
import com.framebucket.studio.StudioDtos.PaymentPurpose;
import com.framebucket.studio.StudioProfileService;
import com.framebucket.studio.UpiQrService;
import com.framebucket.subscription.EntitlementService;
import com.framebucket.subscription.Feature;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Booking agreements: the photographer sends a contract for a submitted quotation, the client signs it on the
 * quotation page (typed name + drawn signature), the date is held on the photographer's calendar, and the
 * client pays the advance by UPI. When the photographer creates the event the confirmed advance is recorded
 * as a payment with a receipt.
 */
@Service
public class ContractService {
    private static final int MAX_SIGNATURE_BYTES = 300_000;

    record EnquiryRow(UUID id, UUID photographerId, String name, String mobile, String email, String occasion,
                      BigDecimal finalizedQuote, UUID createdEventId, String quoteNumber, BigDecimal quotePrice,
                      LocalDate preferredDate, String venue, String packageName, String addonCodes) {
    }

    record ContractRow(UUID id, UUID enquiryId, UUID photographerId, String status, String terms, BigDecimal total,
                       BigDecimal advance, LocalDate eventDate, Instant sentAt, Instant signedAt, String signerName,
                       byte[] signature, String signerIp, String sha, String advanceReference, Instant advanceReportedAt,
                       Instant advanceConfirmedAt, String advanceMethod, LocalDate advancePaidOn, boolean advanceRecorded) {
        ContractSummary summary() {
            return new ContractSummary(status, total, advance, eventDate, sentAt, signedAt, signerName, advanceReference,
                    advanceReportedAt, advanceConfirmedAt);
        }
    }

    private static final RowMapper<EnquiryRow> ENQUIRY = (rs, n) -> new EnquiryRow(
            rs.getObject("id", UUID.class), rs.getObject("photographer_id", UUID.class), rs.getString("name"),
            rs.getString("mobile_number"), rs.getString("email"), rs.getString("occasion"),
            rs.getBigDecimal("finalized_quote"), rs.getObject("created_event_id", UUID.class), rs.getString("quote_number"),
            rs.getBigDecimal("final_price"), localDate(rs.getDate("preferred_event_date")), rs.getString("venue"),
            rs.getString("package_name"), rs.getString("addon_codes"));

    static final RowMapper<ContractRow> CONTRACT = (rs, n) -> new ContractRow(
            rs.getObject("id", UUID.class), rs.getObject("enquiry_id", UUID.class), rs.getObject("photographer_id", UUID.class),
            rs.getString("status"), rs.getString("terms"), rs.getBigDecimal("total_amount"), rs.getBigDecimal("advance_amount"),
            localDate(rs.getDate("event_date")), instant(rs.getTimestamp("sent_at")), instant(rs.getTimestamp("signed_at")),
            rs.getString("signer_name"), rs.getBytes("signature_png"), rs.getString("signer_ip"), rs.getString("content_sha256"),
            rs.getString("advance_reference"), instant(rs.getTimestamp("advance_reported_at")),
            instant(rs.getTimestamp("advance_confirmed_at")), rs.getString("advance_method"),
            localDate(rs.getDate("advance_paid_on")), rs.getBoolean("advance_payment_recorded"));

    private final JdbcTemplate jdbc;
    private final QuotePricingService catalog;
    private final DateHolds holds;
    private final QuoteTokens tokens;
    private final Notifier notifier;
    private final EntitlementService entitlements;
    private final StudioProfileService profiles;
    private final UpiQrService upi;
    private final BillingService billing;

    public ContractService(JdbcTemplate jdbc, QuotePricingService catalog, DateHolds holds, QuoteTokens tokens,
                           Notifier notifier, EntitlementService entitlements, StudioProfileService profiles,
                           UpiQrService upi, BillingService billing) {
        this.jdbc = jdbc;
        this.catalog = catalog;
        this.holds = holds;
        this.tokens = tokens;
        this.notifier = notifier;
        this.entitlements = entitlements;
        this.profiles = profiles;
        this.upi = upi;
        this.billing = billing;
    }

    // ---- photographer -----------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ContractDraft draft(UUID photographerId, UUID enquiryId) {
        EnquiryRow enquiry = enquiryForPhotographer(photographerId, enquiryId);
        Optional<ContractRow> current = contract(enquiryId);
        QuotePricingService.Settings settings = catalog.settings(photographerId);
        BigDecimal total = defaultTotal(enquiry);
        BigDecimal advance = total.multiply(settings.advancePercent()).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
        TreeSet<LocalDate> blocked = new TreeSet<>(jdbc.query(
                "SELECT event_date FROM fb_event_booking WHERE photographer_id = ? AND event_date >= CURRENT_DATE",
                (rs, n) -> rs.getDate(1).toLocalDate(), photographerId));
        jdbc.query("SELECT hold_date FROM fb_date_hold WHERE photographer_id = ? AND enquiry_id <> ?",
                (rs, n) -> rs.getDate(1).toLocalDate(), photographerId, enquiryId).forEach(blocked::add);
        return new ContractDraft(
                current.filter(c -> !"CANCELLED".equals(c.status())).map(ContractRow::eventDate).orElse(enquiry.preferredDate()),
                current.filter(c -> !"CANCELLED".equals(c.status())).map(ContractRow::total).orElse(total),
                current.filter(c -> !"CANCELLED".equals(c.status())).map(ContractRow::advance).orElse(advance),
                current.filter(c -> !"CANCELLED".equals(c.status())).map(ContractRow::terms).orElse(settings.contractTerms()),
                current.map(ContractRow::summary).orElse(null),
                new ArrayList<>(blocked));
    }

    @Transactional
    public ContractSendResponse send(UUID photographerId, UUID enquiryId, SendContractRequest request) {
        entitlements.require(photographerId, Feature.CONTRACTS);
        EnquiryRow enquiry = enquiryForPhotographer(photographerId, enquiryId);
        if (enquiry.createdEventId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This enquiry is already booked as an event.");
        }
        Optional<ContractRow> existing = contract(enquiryId);
        if (existing.isPresent() && "SIGNED".equals(existing.get().status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The client has already signed. Cancel the contract first to send a new one.");
        }
        ContractDraft defaults = draft(photographerId, enquiryId);
        LocalDate date = request.eventDate() != null ? request.eventDate() : defaults.eventDate();
        if (date == null) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Choose the event date for this contract.");
        if (date.isBefore(EntitlementService.today())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "The event date can't be in the past.");
        }
        holds.requireAvailable(photographerId, date, enquiryId);
        BigDecimal total = scale(request.totalAmount() != null ? request.totalAmount() : defaults.totalAmount());
        BigDecimal advance = scale(request.advanceAmount() != null ? request.advanceAmount() : defaults.advanceAmount());
        if (advance.compareTo(total) > 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "The advance can't be more than the total.");
        }
        String terms = request.terms() != null && !request.terms().isBlank() ? request.terms().strip() : defaults.terms();
        if (existing.isPresent()) {
            jdbc.update("""
                            UPDATE fb_contract SET status = 'SENT', terms = ?, total_amount = ?, advance_amount = ?, event_date = ?,
                                sent_at = CURRENT_TIMESTAMP, signed_at = NULL, signer_name = NULL, signature_png = NULL,
                                signer_ip = NULL, signer_user_agent = NULL, content_sha256 = NULL, advance_reference = NULL,
                                advance_reported_at = NULL, advance_confirmed_at = NULL, advance_method = NULL,
                                advance_paid_on = NULL, cancelled_at = NULL
                            WHERE id = ?
                            """, terms, total, advance, Date.valueOf(date), existing.get().id());
        } else {
            jdbc.update("""
                            INSERT INTO fb_contract (id, enquiry_id, photographer_id, status, terms, total_amount, advance_amount, event_date)
                            VALUES (?, ?, ?, 'SENT', ?, ?, ?, ?)
                            """, UUID.randomUUID(), enquiryId, photographerId, terms, total, advance, Date.valueOf(date));
        }
        Notifier.Studio studio = notifier.studio(photographerId);
        Optional<String> link = tokens.link(enquiryId);
        notifier.email(enquiry.email(), studio.displayName() + ": your booking agreement is ready to sign",
                "Hi " + enquiry.name() + ",\n\n" + studio.displayName() + " has sent your booking agreement for "
                        + enquiry.occasion() + " on " + date + ".\n\nTotal: Rs. " + ContractPdf.money(total)
                        + "\nAdvance to confirm the date: Rs. " + ContractPdf.money(advance) + "\n\n"
                        + link.map(url -> "Review and sign here: " + url + "\n\n")
                        .orElse("Open the quotation link you received earlier to review and sign.\n\n")
                        + "Your date is reserved as soon as you sign.\n\n" + studio.displayName());
        return new ContractSendResponse(contract(enquiryId).orElseThrow().summary(), link.orElse(null), enquiry.email() != null);
    }

    @Transactional
    public void cancel(UUID photographerId, UUID enquiryId) {
        enquiryForPhotographer(photographerId, enquiryId);
        int updated = jdbc.update("UPDATE fb_contract SET status = 'CANCELLED', cancelled_at = CURRENT_TIMESTAMP WHERE enquiry_id = ? AND status <> 'CANCELLED'",
                enquiryId);
        if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no active contract for this enquiry.");
        holds.release(enquiryId);
    }

    @Transactional
    public ContractSummary confirmAdvance(UUID photographerId, UUID enquiryId, ConfirmAdvanceRequest request) {
        EnquiryRow enquiry = enquiryForPhotographer(photographerId, enquiryId);
        ContractRow contract = contract(enquiryId).filter(c -> "SIGNED".equals(c.status()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "The client hasn't signed the contract yet."));
        if (contract.advanceConfirmedAt() != null) return contract.summary();
        String method = request == null || request.method() == null ? "UPI" : request.method();
        LocalDate paidOn = request == null || request.paidOn() == null ? EntitlementService.today() : request.paidOn();
        jdbc.update("UPDATE fb_contract SET advance_confirmed_at = CURRENT_TIMESTAMP, advance_method = ?, advance_paid_on = ? WHERE id = ?",
                method, Date.valueOf(paidOn), contract.id());
        if (enquiry.createdEventId() != null) recordAdvanceIfNeeded(photographerId, enquiryId, enquiry.createdEventId());
        notifier.email(enquiry.email(), notifier.studio(photographerId).displayName() + ": advance received",
                "Hi " + enquiry.name() + ",\n\nWe've received your advance of Rs. " + ContractPdf.money(contract.advance())
                        + ". Your date " + contract.eventDate() + " is confirmed.\n\nThank you!");
        return contract(enquiryId).orElseThrow().summary();
    }

    /** Called after the event is created from the enquiry: turns a confirmed advance into a receipted payment. */
    @Transactional
    public void recordAdvanceIfNeeded(UUID photographerId, UUID enquiryId, UUID eventId) {
        ContractRow contract = contract(enquiryId).orElse(null);
        if (contract == null || contract.advanceConfirmedAt() == null || contract.advanceRecorded()
                || contract.advance().signum() <= 0) {
            return;
        }
        billing.recordPayment(photographerId, eventId, new PaymentCreateRequest(contract.advance(),
                contract.advancePaidOn() == null ? EntitlementService.today() : contract.advancePaidOn(),
                PaymentMethod.valueOf(contract.advanceMethod() == null ? "UPI" : contract.advanceMethod()),
                PaymentPurpose.ADVANCE, contract.advanceReference(), "Advance collected with the signed booking agreement"));
        jdbc.update("UPDATE fb_contract SET advance_payment_recorded = TRUE WHERE id = ?", contract.id());
    }

    @Transactional(readOnly = true)
    public byte[] pdfForPhotographer(UUID photographerId, UUID enquiryId) {
        EnquiryRow enquiry = enquiryForPhotographer(photographerId, enquiryId);
        ContractRow contract = contract(enquiryId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No contract yet."));
        return pdf(enquiry, contract);
    }

    @Transactional(readOnly = true)
    public Optional<ContractSummary> summary(UUID enquiryId) {
        return contract(enquiryId).map(ContractRow::summary);
    }

    // ---- client (quotation link) ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PublicContractView publicView(UUID enquiryId) {
        ContractRow contract = contract(enquiryId).filter(c -> !"CANCELLED".equals(c.status())).orElse(null);
        if (contract == null) return null;
        StudioProfileService.Profile profile = profiles.profile(contract.photographerId());
        return new PublicContractView(contract.status(), notifier.studio(contract.photographerId()).displayName(),
                contract.terms(), contract.total(), contract.advance(), contract.eventDate(), contract.sentAt(),
                contract.signedAt(), contract.signerName(), profile.upiConfigured(),
                profile.upiConfigured() ? profile.upiId() : null, contract.advanceReference(), contract.advanceReportedAt(),
                contract.advanceConfirmedAt());
    }

    @Transactional
    public PublicContractView sign(String token, SignContractRequest request, String ip, String userAgent) {
        QuoteTokens.EnquiryRef ref = tokens.resolve(token);
        ContractRow contract = jdbc.query("SELECT * FROM fb_contract WHERE enquiry_id = ? FOR UPDATE", CONTRACT, ref.enquiryId())
                .stream().findFirst().filter(c -> !"CANCELLED".equals(c.status()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no contract to sign."));
        if ("SIGNED".equals(contract.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This agreement has already been signed.");
        }
        byte[] signature = decodeSignature(request.signature());
        holds.hold(contract.photographerId(), contract.eventDate(), ref.enquiryId());
        Instant signedAt = Instant.now();
        String signer = request.signerName().strip();
        String sha = sha256(contract.terms() + "|" + contract.total() + "|" + contract.advance() + "|" + contract.eventDate()
                + "|" + signer + "|" + signedAt + "|" + HexFormat.of().formatHex(sha256Bytes(signature)));
        jdbc.update("""
                        UPDATE fb_contract SET status = 'SIGNED', signed_at = ?, signer_name = ?, signature_png = ?, signer_ip = ?,
                            signer_user_agent = ?, content_sha256 = ?
                        WHERE id = ?
                        """, Timestamp.from(signedAt), signer, signature, truncate(ip, 64), truncate(userAgent, 400), sha, contract.id());
        EnquiryRow enquiry = enquiryRow(ref.enquiryId());
        Notifier.Studio studio = notifier.studio(contract.photographerId());
        notifier.email(studio.email(), "Signed: " + enquiry.name() + " · " + enquiry.occasion() + " on " + contract.eventDate(),
                enquiry.name() + " signed the booking agreement for " + contract.eventDate()
                        + ". The date is now on hold in your calendar.\n\nAdvance due: Rs. " + ContractPdf.money(contract.advance())
                        + "\nConfirm the advance in FrameBucket once you receive it, then create the event.");
        notifier.email(enquiry.email(), studio.displayName() + ": agreement signed",
                "Hi " + enquiry.name() + ",\n\nThank you for signing. Your date " + contract.eventDate()
                        + " is on hold. Please pay the advance of Rs. " + ContractPdf.money(contract.advance())
                        + " to confirm the booking." + tokens.link(ref.enquiryId()).map(url -> "\n\nYour agreement and payment details: " + url).orElse(""));
        return publicView(ref.enquiryId());
    }

    @Transactional
    public PublicContractView reportAdvance(String token, AdvanceReportRequest request) {
        QuoteTokens.EnquiryRef ref = tokens.resolve(token);
        ContractRow contract = contract(ref.enquiryId()).filter(c -> "SIGNED".equals(c.status()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Sign the agreement before paying the advance."));
        jdbc.update("UPDATE fb_contract SET advance_reference = ?, advance_reported_at = CURRENT_TIMESTAMP WHERE id = ?",
                request.reference().strip(), contract.id());
        EnquiryRow enquiry = enquiryRow(ref.enquiryId());
        notifier.email(notifier.studio(contract.photographerId()).email(), "Advance paid by " + enquiry.name(),
                enquiry.name() + " says they paid the advance of Rs. " + ContractPdf.money(contract.advance())
                        + " (reference " + request.reference().strip() + ").\nCheck your account and confirm it in FrameBucket.");
        return publicView(ref.enquiryId());
    }

    @Transactional(readOnly = true)
    public byte[] advanceQr(String token) {
        QuoteTokens.EnquiryRef ref = tokens.resolve(token);
        ContractRow contract = contract(ref.enquiryId()).filter(c -> "SIGNED".equals(c.status()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Sign the agreement before paying the advance."));
        StudioProfileService.Profile profile = profiles.profile(contract.photographerId());
        if (!profile.upiConfigured()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The studio hasn't set up UPI payments yet.");
        }
        EnquiryRow enquiry = enquiryRow(ref.enquiryId());
        String payee = profile.upiPayeeName() == null ? notifier.studio(contract.photographerId()).displayName() : profile.upiPayeeName();
        return upi.qrPng(upi.upiUri(profile.upiId(), payee, contract.advance(), "Advance " + enquiry.quoteNumber()));
    }

    @Transactional(readOnly = true)
    public byte[] pdfForClient(String token) {
        QuoteTokens.EnquiryRef ref = tokens.resolve(token);
        ContractRow contract = contract(ref.enquiryId()).filter(c -> !"CANCELLED".equals(c.status()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no agreement yet."));
        return pdf(enquiryRow(ref.enquiryId()), contract);
    }

    // ---- helpers ------------------------------------------------------------------------------------

    private byte[] pdf(EnquiryRow enquiry, ContractRow contract) {
        Notifier.Studio studio = notifier.studio(contract.photographerId());
        List<String> parts = new ArrayList<>();
        if (enquiry.packageName() != null) parts.add("Package: " + enquiry.packageName());
        List<String> addons = catalog.addonNames(contract.photographerId(), splitCodes(enquiry.addonCodes()));
        if (!addons.isEmpty()) parts.add("Add-ons: " + String.join(", ", addons));
        parts.add("As per quotation " + enquiry.quoteNumber() + ".");
        return ContractPdf.generate(new ContractPdf.Data(studio.displayName(),
                String.join("  |  ", nonBlank(studio.mobileNumber()), nonBlank(studio.email())),
                enquiry.quoteNumber(), enquiry.name(), "+91" + enquiry.mobile(), enquiry.email(), enquiry.occasion(),
                enquiry.venue(), contract.eventDate(), String.join("  ", parts), contract.total(), contract.advance(),
                contract.terms(), contract.status(), contract.signerName(), contract.signedAt(), contract.signerIp(),
                contract.sha(), contract.signature()));
    }

    Optional<ContractRow> contract(UUID enquiryId) {
        return jdbc.query("SELECT * FROM fb_contract WHERE enquiry_id = ?", CONTRACT, enquiryId).stream().findFirst();
    }

    private EnquiryRow enquiryForPhotographer(UUID photographerId, UUID enquiryId) {
        EnquiryRow row = enquiryRow(enquiryId);
        if (!row.photographerId().equals(photographerId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Enquiry not found.");
        if (row.quoteNumber() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Wait for the client to submit a quotation first.");
        }
        return row;
    }

    private EnquiryRow enquiryRow(UUID enquiryId) {
        return jdbc.query("""
                        SELECT e.id, e.photographer_id, e.name, e.mobile_number, e.email, t.name AS occasion, e.finalized_quote,
                               e.created_event_id, q.quote_number, q.final_price, q.preferred_event_date, q.venue,
                               q.package_name, q.addon_codes
                        FROM fb_enquiries e
                        JOIN fb_event_list t ON t.id = e.event_type_id
                        LEFT JOIN fb_quotaion q ON q.enquiry_id = e.id
                        WHERE e.id = ?
                        """, ENQUIRY, enquiryId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enquiry not found."));
    }

    private static BigDecimal defaultTotal(EnquiryRow enquiry) {
        BigDecimal value = enquiry.finalizedQuote() != null ? enquiry.finalizedQuote() : enquiry.quotePrice();
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static byte[] decodeSignature(String value) {
        String data = value.trim();
        int comma = data.indexOf(',');
        if (data.startsWith("data:")) {
            if (!data.startsWith("data:image/png;base64,")) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Draw your signature to sign.");
            }
            data = data.substring(comma + 1);
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Draw your signature to sign.");
        }
        if (bytes.length == 0 || bytes.length > MAX_SIGNATURE_BYTES) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "The signature image is too large.");
        }
        try {
            if (ImageIO.read(new ByteArrayInputStream(bytes)) == null) throw new IOException("not an image");
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Draw your signature to sign.");
        }
        return bytes;
    }

    static List<String> splitCodes(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value.split(","));
    }

    private static BigDecimal scale(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static String sha256(String value) {
        return HexFormat.of().formatHex(sha256Bytes(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] sha256Bytes(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String truncate(String value, int max) {
        return value == null ? null : value.length() <= max ? value : value.substring(0, max);
    }

    private static String nonBlank(String value) {
        return value == null ? "" : value;
    }

    static LocalDate localDate(Date value) {
        return value == null ? null : value.toLocalDate();
    }

    static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
