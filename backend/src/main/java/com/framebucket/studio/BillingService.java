package com.framebucket.studio;

import com.framebucket.studio.StudioDtos.CustomerBillingResponse;
import com.framebucket.studio.StudioDtos.DownloadMode;
import com.framebucket.studio.StudioDtos.DownloadPolicyRequest;
import com.framebucket.studio.StudioDtos.DownloadPolicyResponse;
import com.framebucket.studio.StudioDtos.EventBillingResponse;
import com.framebucket.studio.StudioDtos.ExpenseRequest;
import com.framebucket.studio.StudioDtos.ExpenseResponse;
import com.framebucket.studio.StudioDtos.FinanceEventRow;
import com.framebucket.studio.StudioDtos.FinanceSummaryResponse;
import com.framebucket.studio.StudioDtos.PaymentCreateRequest;
import com.framebucket.studio.StudioDtos.PaymentMethod;
import com.framebucket.studio.StudioDtos.PaymentPurpose;
import com.framebucket.studio.StudioDtos.PaymentResponse;
import com.framebucket.studio.StudioEvents.EventInfo;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BillingService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private static final RowMapper<PaymentResponse> PAYMENT = (rs, n) -> new PaymentResponse(
            rs.getObject("id", UUID.class),
            rs.getString("receipt_number"),
            rs.getBigDecimal("amount"),
            rs.getDate("paid_on").toLocalDate(),
            PaymentMethod.valueOf(rs.getString("method")),
            PaymentPurpose.valueOf(rs.getString("purpose")),
            rs.getString("reference"),
            rs.getString("notes"),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbc;
    private final StudioEvents events;
    private final StudioProfileService profiles;
    private final UpiQrService upi;
    private final FlipbookService flipbooks;

    public BillingService(JdbcTemplate jdbc, StudioEvents events, StudioProfileService profiles, UpiQrService upi,
                          FlipbookService flipbooks) {
        this.jdbc = jdbc;
        this.events = events;
        this.profiles = profiles;
        this.upi = upi;
        this.flipbooks = flipbooks;
    }

    // ---- Photographer: event billing -------------------------------------------------------

    @Transactional(readOnly = true)
    public EventBillingResponse eventBilling(UUID photographerId, UUID eventId) {
        EventInfo event = events.forPhotographer(eventId, photographerId);
        List<PaymentResponse> payments = payments(eventId);
        BigDecimal paid = sum(payments);
        BigDecimal total = event.totalAmount();
        BigDecimal outstanding = balance(total, sumTowardsEvent(payments));
        BigDecimal expenses = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM fb_expense WHERE event_id = ? AND photographer_id = ?",
                BigDecimal.class, eventId, photographerId);
        return new EventBillingResponse(event.id(), event.eventCode(), event.eventDate(), event.eventTypeName(),
                event.customerName(), total, paid, outstanding, scale(expenses),
                profiles.profile(photographerId).upiConfigured(), downloadPolicy(eventId), payments);
    }

    @Transactional
    public PaymentResponse recordPayment(UUID photographerId, UUID eventId, PaymentCreateRequest request) {
        events.forPhotographer(eventId, photographerId);
        UUID id = UUID.randomUUID();
        String receipt = profiles.nextReceiptNumber(photographerId);
        jdbc.update("""
                        INSERT INTO fb_event_payment (id, photographer_id, event_id, receipt_number, amount, paid_on,
                            method, purpose, reference, notes)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, id, photographerId, eventId, receipt, request.amount(), Date.valueOf(request.paidOn()),
                request.method().name(), request.purpose().name(),
                StudioProfileService.blankToNull(request.reference()), StudioProfileService.blankToNull(request.notes()));
        if (request.purpose() == PaymentPurpose.DOWNLOAD) {
            // A confirmed download payment unlocks the customer's gallery downloads.
            jdbc.update("""
                    UPDATE fb_event_download_policy SET unlocked_at = COALESCE(unlocked_at, CURRENT_TIMESTAMP),
                        updated_at = CURRENT_TIMESTAMP
                    WHERE event_id = ? AND mode = 'PAID'
                    """, eventId);
        }
        return jdbc.queryForObject("SELECT * FROM fb_event_payment WHERE id = ?", PAYMENT, id);
    }

    @Transactional
    public void deletePayment(UUID photographerId, UUID eventId, UUID paymentId) {
        int deleted = jdbc.update("DELETE FROM fb_event_payment WHERE id = ? AND event_id = ? AND photographer_id = ?",
                paymentId, eventId, photographerId);
        if (deleted == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found.");
    }

    @Transactional(readOnly = true)
    public byte[] receiptForPhotographer(UUID photographerId, UUID eventId, UUID paymentId) {
        return receipt(events.forPhotographer(eventId, photographerId), paymentId);
    }

    @Transactional(readOnly = true)
    public byte[] paymentQrForPhotographer(UUID photographerId, UUID eventId, BigDecimal amount) {
        EventInfo event = events.forPhotographer(eventId, photographerId);
        BigDecimal requested = amount != null && amount.signum() > 0 ? amount : balance(event.totalAmount(), sumTowardsEvent(payments(eventId)));
        return qr(event, requested, "Event " + event.eventCode());
    }

    // ---- Download policy (free / paid downloads) -------------------------------------------

    @Transactional(readOnly = true)
    public DownloadPolicyResponse downloadPolicy(UUID eventId) {
        return jdbc.query("SELECT mode, price, unlocked_at FROM fb_event_download_policy WHERE event_id = ?",
                        (rs, n) -> {
                            Timestamp unlockedAt = rs.getTimestamp("unlocked_at");
                            return new DownloadPolicyResponse(DownloadMode.valueOf(rs.getString("mode")),
                                    rs.getBigDecimal("price"), unlockedAt != null,
                                    unlockedAt == null ? null : unlockedAt.toInstant());
                        }, eventId)
                .stream().findFirst()
                .orElse(new DownloadPolicyResponse(DownloadMode.FREE, null, false, null));
    }

    @Transactional
    public DownloadPolicyResponse updateDownloadPolicy(UUID photographerId, UUID eventId, DownloadPolicyRequest request) {
        events.forPhotographer(eventId, photographerId);
        if (request.mode() == DownloadMode.PAID && (request.price() == null || request.price().signum() <= 0)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Set a download price for paid downloads.");
        }
        jdbc.update("""
                        INSERT INTO fb_event_download_policy (event_id, mode, price, unlocked_at, updated_at)
                        VALUES (?, ?, ?, CASE WHEN ? THEN CURRENT_TIMESTAMP END, CURRENT_TIMESTAMP)
                        ON CONFLICT (event_id) DO UPDATE SET mode = EXCLUDED.mode, price = EXCLUDED.price,
                            unlocked_at = CASE WHEN ? THEN COALESCE(fb_event_download_policy.unlocked_at, CURRENT_TIMESTAMP) END,
                            updated_at = CURRENT_TIMESTAMP
                        """, eventId, request.mode().name(),
                request.mode() == DownloadMode.PAID ? request.price() : null,
                request.unlocked(), request.unlocked());
        return downloadPolicy(eventId);
    }

    /** Called before any customer gallery download. */
    @Transactional(readOnly = true)
    public void requireDownloadsAllowed(UUID eventId) {
        DownloadPolicyResponse policy = downloadPolicy(eventId);
        if (policy.downloadsAllowed()) return;
        if (policy.mode() == DownloadMode.DISABLED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your photographer has turned off downloads for this gallery.");
        }
        throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                "Downloads for this gallery are paid. Pay Rs. " + ReceiptPdfGenerator.money(policy.price())
                        + " and your photographer will unlock them.");
    }

    // ---- Customer view ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public CustomerBillingResponse customerBilling(UUID eventId, Collection<UUID> customerIds) {
        EventInfo event = events.forCustomer(eventId, customerIds);
        List<PaymentResponse> payments = payments(eventId);
        BigDecimal paid = sum(payments);
        BigDecimal total = event.totalAmount();
        return new CustomerBillingResponse(event.id(), event.eventTypeName(), event.studioDisplayName(), total, paid,
                balance(total, sumTowardsEvent(payments)), profiles.profile(event.photographerId()).upiConfigured(), downloadPolicy(eventId),
                payments, flipbooks.linksForEvent(eventId));
    }

    @Transactional(readOnly = true)
    public byte[] receiptForCustomer(UUID eventId, UUID paymentId, Collection<UUID> customerIds) {
        return receipt(events.forCustomer(eventId, customerIds), paymentId);
    }

    @Transactional(readOnly = true)
    public byte[] paymentQrForCustomer(UUID eventId, Collection<UUID> customerIds, PaymentPurpose purpose) {
        EventInfo event = events.forCustomer(eventId, customerIds);
        if (purpose == PaymentPurpose.DOWNLOAD) {
            DownloadPolicyResponse policy = downloadPolicy(eventId);
            if (policy.mode() != DownloadMode.PAID || policy.unlocked()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "No download payment is due for this event.");
            }
            return qr(event, policy.price(), "Photo downloads " + event.eventCode());
        }
        BigDecimal balance = balance(event.totalAmount(), sumTowardsEvent(payments(eventId)));
        if (balance.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "There is no balance due for this event.");
        }
        return qr(event, balance, "Event " + event.eventCode());
    }

    // ---- Finance summary and expenses --------------------------------------------------------

    @Transactional(readOnly = true)
    public FinanceSummaryResponse financeSummary(UUID photographerId) {
        Map<UUID, BigDecimal> paidByEvent = new HashMap<>();
        Map<UUID, BigDecimal> towardsEvent = new HashMap<>();
        jdbc.query("""
                SELECT event_id, SUM(amount) AS paid,
                       COALESCE(SUM(amount) FILTER (WHERE purpose <> 'DOWNLOAD'), 0) AS towards_event
                FROM fb_event_payment WHERE photographer_id = ? GROUP BY event_id
                """, rs -> {
            UUID id = rs.getObject("event_id", UUID.class);
            paidByEvent.put(id, rs.getBigDecimal("paid"));
            towardsEvent.put(id, rs.getBigDecimal("towards_event"));
        }, photographerId);
        Map<UUID, BigDecimal> expensesByEvent = new HashMap<>();
        jdbc.query("SELECT event_id, SUM(amount) AS spent FROM fb_expense WHERE photographer_id = ? AND event_id IS NOT NULL GROUP BY event_id",
                rs -> { expensesByEvent.put(rs.getObject("event_id", UUID.class), rs.getBigDecimal("spent")); }, photographerId);
        BigDecimal allExpenses = scale(jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM fb_expense WHERE photographer_id = ?", BigDecimal.class, photographerId));

        List<FinanceEventRow> rows = new ArrayList<>();
        BigDecimal billed = ZERO, received = ZERO, outstanding = ZERO;
        for (EventInfo event : events.allForPhotographer(photographerId)) {
            BigDecimal total = event.totalAmount();
            BigDecimal paid = scale(paidByEvent.getOrDefault(event.id(), ZERO));
            BigDecimal balance = balance(total, scale(towardsEvent.getOrDefault(event.id(), ZERO)));
            rows.add(new FinanceEventRow(event.id(), event.eventCode(), event.eventDate(), event.eventTypeName(),
                    event.customerName(), total, paid, balance, scale(expensesByEvent.getOrDefault(event.id(), ZERO))));
            billed = billed.add(total);
            received = received.add(paid);
            outstanding = outstanding.add(balance);
        }
        return new FinanceSummaryResponse(billed, received, outstanding, allExpenses, received.subtract(allExpenses), rows);
    }

    private static final RowMapper<ExpenseResponse> EXPENSE = (rs, n) -> new ExpenseResponse(
            rs.getObject("id", UUID.class),
            rs.getObject("event_id", UUID.class),
            rs.getString("event_code"),
            rs.getString("category"),
            rs.getBigDecimal("amount"),
            rs.getDate("spent_on").toLocalDate(),
            rs.getString("vendor"),
            rs.getString("notes"),
            rs.getTimestamp("created_at").toInstant());

    private static final String EXPENSE_SELECT = """
            SELECT x.*, e.event_code FROM fb_expense x LEFT JOIN fb_event_booking e ON e.id = x.event_id
            """;

    @Transactional(readOnly = true)
    public List<ExpenseResponse> expenses(UUID photographerId, LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.of(2000, 1, 1) : from;
        LocalDate end = to == null ? LocalDate.of(2100, 1, 1) : to;
        return jdbc.query(EXPENSE_SELECT + " WHERE x.photographer_id = ? AND x.spent_on BETWEEN ? AND ? ORDER BY x.spent_on DESC, x.created_at DESC",
                EXPENSE, photographerId, Date.valueOf(start), Date.valueOf(end));
    }

    @Transactional
    public ExpenseResponse saveExpense(UUID photographerId, UUID expenseId, ExpenseRequest request) {
        if (request.eventId() != null) events.forPhotographer(request.eventId(), photographerId);
        UUID id = expenseId == null ? UUID.randomUUID() : expenseId;
        if (expenseId == null) {
            jdbc.update("""
                            INSERT INTO fb_expense (id, photographer_id, event_id, category, amount, spent_on, vendor, notes)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                            """, id, photographerId, request.eventId(), request.category().trim(), request.amount(),
                    Date.valueOf(request.spentOn()), StudioProfileService.blankToNull(request.vendor()),
                    StudioProfileService.blankToNull(request.notes()));
        } else {
            int updated = jdbc.update("""
                            UPDATE fb_expense SET event_id = ?, category = ?, amount = ?, spent_on = ?, vendor = ?, notes = ?
                            WHERE id = ? AND photographer_id = ?
                            """, request.eventId(), request.category().trim(), request.amount(), Date.valueOf(request.spentOn()),
                    StudioProfileService.blankToNull(request.vendor()), StudioProfileService.blankToNull(request.notes()),
                    id, photographerId);
            if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found.");
        }
        return jdbc.queryForObject(EXPENSE_SELECT + " WHERE x.id = ?", EXPENSE, id);
    }

    @Transactional
    public void deleteExpense(UUID photographerId, UUID expenseId) {
        if (jdbc.update("DELETE FROM fb_expense WHERE id = ? AND photographer_id = ?", expenseId, photographerId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found.");
        }
    }

    // ---- helpers ------------------------------------------------------------------------------

    List<PaymentResponse> payments(UUID eventId) {
        return jdbc.query("SELECT * FROM fb_event_payment WHERE event_id = ? ORDER BY paid_on, created_at", PAYMENT, eventId);
    }

    BigDecimal balanceFor(EventInfo event) {
        return balance(event.totalAmount(), sumTowardsEvent(payments(event.id())));
    }

    private byte[] receipt(EventInfo event, UUID paymentId) {
        List<PaymentResponse> payments = payments(event.id());
        PaymentResponse payment = payments.stream().filter(p -> p.id().equals(paymentId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Receipt not found."));
        // "Paid to date" is as of this receipt, so older receipts stay accurate after later payments.
        BigDecimal paidToDate = ZERO;
        for (PaymentResponse p : payments) {
            if (p.purpose() != PaymentPurpose.DOWNLOAD) paidToDate = paidToDate.add(p.amount());
            if (p.id().equals(paymentId)) break;
        }
        StudioProfileService.Profile profile = profiles.profile(event.photographerId());
        return ReceiptPdfGenerator.generate(new ReceiptPdfGenerator.ReceiptData(
                event.studioDisplayName(), profile.businessAddress(), profile.gstin(), event.photographerEmail(),
                event.photographerMobile(), payment.receiptNumber(), payment.paidOn(), event.customerName(),
                event.customerMobile(), event.eventCode(), event.eventTypeName(), event.eventDate(),
                payment.purpose().name(), payment.method().name(), payment.reference(), payment.amount(),
                event.totalAmount(), paidToDate, balance(event.totalAmount(), paidToDate), profile.receiptFooter()));
    }

    private byte[] qr(EventInfo event, BigDecimal amount, String note) {
        StudioProfileService.Profile profile = profiles.profile(event.photographerId());
        String payee = profile.upiPayeeName() != null ? profile.upiPayeeName() : event.studioDisplayName();
        return upi.qrPng(upi.upiUri(profile.upiId(), payee, amount, note));
    }

    private static BigDecimal sum(List<PaymentResponse> payments) {
        return payments.stream().map(PaymentResponse::amount).reduce(ZERO, BigDecimal::add);
    }

    /** Payments that count against the agreed event price (download fees are charged on top of it). */
    private static BigDecimal sumTowardsEvent(List<PaymentResponse> payments) {
        return payments.stream().filter(p -> p.purpose() != PaymentPurpose.DOWNLOAD)
                .map(PaymentResponse::amount).reduce(ZERO, BigDecimal::add);
    }

    private static BigDecimal balance(BigDecimal total, BigDecimal paid) {
        BigDecimal balance = total.subtract(paid);
        return balance.signum() < 0 ? ZERO : balance.setScale(2);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2);
    }
}
