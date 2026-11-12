package com.framebucket.admin;

import com.framebucket.admin.AdminDtos.ChangePlanRequest;
import com.framebucket.admin.AdminDtos.DailyCount;
import com.framebucket.admin.AdminDtos.EventRow;
import com.framebucket.admin.AdminDtos.Overview;
import com.framebucket.admin.AdminDtos.Page;
import com.framebucket.admin.AdminDtos.PhotographerDetail;
import com.framebucket.admin.AdminDtos.PhotographerRow;
import com.framebucket.enquiry.notification.EnquiryEmailSender;
import com.framebucket.subscription.EntitlementService;
import com.framebucket.subscription.SubscriptionDtos.PlanRequestView;
import com.framebucket.subscription.SubscriptionDtos.UsageView;
import com.framebucket.subscription.SubscriptionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Read models and actions behind the admin portal. All SQL is read-only except the explicit actions. */
@Service
public class AdminService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminService.class);
    private static final ZoneId STUDIO_ZONE = ZoneId.of("Asia/Kolkata");
    private static final Set<String> STATUSES = Set.of("TRIAL", "TRIAL_ENDED", "FREE", "ACTIVE", "GRACE", "LAPSED", "SUSPENDED");

    /** Same rules as EntitlementService, in SQL, so lists can be filtered and counted by status. */
    static final String STATUS_SQL = """
            CASE WHEN NOT u.active THEN 'SUSPENDED'
                 WHEN s.trial_days > 0 THEN CASE WHEN u.trial_ends_at > now() THEN 'TRIAL' ELSE 'TRIAL_ENDED' END
                 WHEN s.plan_code IS NULL OR s.plan_code = 'FREE' THEN 'FREE'
                 WHEN u.plan_expires_at IS NULL OR u.plan_expires_at > now() THEN 'ACTIVE'
                 WHEN u.plan_expires_at + interval '3 days' > now() THEN 'GRACE'
                 ELSE 'LAPSED' END""";

    private static final String BASE = """
            SELECT u.id, u.full_name, u.studio_name, u.email, u.mobile_number, u.email_verified, u.mobile_verified,
                   u.trial_ends_at, u.plan_expires_at, u.billing_cycle, u.created_at, u.last_login_at,
                   u.admin_notes, u.suspended_reason, s.plan_code, s.plan_name, s.price_amount, s.yearly_price_amount,
                   """ + STATUS_SQL + """
             AS status
            FROM fb_user u LEFT JOIN fb_user_subscription s ON s.id = u.subscription_id
            """;

    private final JdbcTemplate jdbc;
    private final EntitlementService entitlements;
    private final SubscriptionManager subscriptions;
    private final AdminAuditService audit;
    private final AdminAccess adminAccess;
    private final EnquiryEmailSender emailSender;
    private final AccountStatusFilter accountStatus;

    public AdminService(JdbcTemplate jdbc, EntitlementService entitlements, SubscriptionManager subscriptions,
                        AdminAuditService audit, AdminAccess adminAccess, EnquiryEmailSender emailSender,
                        AccountStatusFilter accountStatus) {
        this.jdbc = jdbc;
        this.entitlements = entitlements;
        this.subscriptions = subscriptions;
        this.audit = audit;
        this.adminAccess = adminAccess;
        this.emailSender = emailSender;
        this.accountStatus = accountStatus;
    }

    // ---- Overview ----------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Overview overview() {
        Map<String, Object> users = jdbc.queryForMap("""
                SELECT count(*) AS total,
                       count(*) FILTER (WHERE email_verified AND mobile_verified) AS verified,
                       count(*) FILTER (WHERE created_at >= now() - interval '7 days') AS new7,
                       count(*) FILTER (WHERE created_at >= now() - interval '30 days') AS new30
                FROM fb_user
                """);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (String status : List.of("TRIAL", "ACTIVE", "GRACE", "FREE", "TRIAL_ENDED", "LAPSED", "SUSPENDED")) {
            byStatus.put(status, 0L);
        }
        Map<String, Long> payingByPlan = new LinkedHashMap<>();
        BigDecimal[] mrr = {BigDecimal.ZERO};
        jdbc.query("SELECT b.status, b.plan_code, b.billing_cycle, b.price_amount, b.yearly_price_amount FROM (" + BASE + ") b",
                rs -> {
                    String status = rs.getString("status");
                    byStatus.merge(status, 1L, Long::sum);
                    if ("ACTIVE".equals(status) || "GRACE".equals(status)) {
                        payingByPlan.merge(rs.getString("plan_code"), 1L, Long::sum);
                        BigDecimal monthly = rs.getBigDecimal("price_amount");
                        if ("YEARLY".equals(rs.getString("billing_cycle"))) {
                            BigDecimal yearly = rs.getBigDecimal("yearly_price_amount");
                            monthly = (yearly != null ? yearly : monthly.multiply(BigDecimal.TEN))
                                    .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
                        }
                        mrr[0] = mrr[0].add(monthly == null ? BigDecimal.ZERO : monthly);
                    }
                });
        long trialsEnding = count("""
                SELECT count(*) FROM fb_user u JOIN fb_user_subscription s ON s.id = u.subscription_id
                WHERE u.active AND s.trial_days > 0 AND u.trial_ends_at > now() AND u.trial_ends_at <= now() + interval '7 days'
                """);
        List<DailyCount> signups = jdbc.query("""
                        SELECT d::date AS day, count(u.id) AS count
                        FROM generate_series(CAST(? AS date) - 29, CAST(? AS date), interval '1 day') d
                        LEFT JOIN fb_user u ON (u.created_at AT TIME ZONE 'Asia/Kolkata')::date = d::date
                        GROUP BY d ORDER BY d
                        """, (rs, n) -> new DailyCount(rs.getDate("day").toLocalDate(), rs.getLong("count")),
                Date.valueOf(EntitlementService.today()), Date.valueOf(EntitlementService.today()));
        return new Overview(
                ((Number) users.get("total")).longValue(),
                ((Number) users.get("verified")).longValue(),
                ((Number) users.get("new7")).longValue(),
                ((Number) users.get("new30")).longValue(),
                byStatus,
                payingByPlan,
                mrr[0].setScale(2, RoundingMode.HALF_UP),
                trialsEnding,
                count("SELECT count(*) FROM fb_plan_request WHERE status = 'PENDING'"),
                count("SELECT count(*) FROM fb_customer"),
                count("SELECT count(*) FROM fb_enquiries"),
                count("SELECT count(*) FROM fb_event_booking"),
                jdbc.queryForObject("SELECT count(*) FROM fb_event_booking WHERE event_date >= ?", Long.class,
                        Date.valueOf(EntitlementService.today())),
                count("SELECT count(*) FROM fb_event_guest"),
                money("SELECT COALESCE(SUM(amount), 0) FROM fb_event_payment"),
                totalStorage(),
                money("SELECT COALESCE(SUM(amount), 0) FROM fb_subscription_event WHERE created_at >= now() - interval '30 days'"),
                signups);
    }

    // ---- Photographers -----------------------------------------------------------------------------

    private static final RowMapper<PhotographerRow> ROW = (rs, n) -> new PhotographerRow(
            rs.getObject("id", UUID.class),
            rs.getString("full_name"),
            rs.getString("studio_name"),
            rs.getString("email"),
            rs.getString("mobile_number"),
            rs.getBoolean("email_verified"),
            rs.getBoolean("mobile_verified"),
            rs.getString("plan_code"),
            rs.getString("plan_name"),
            rs.getString("status"),
            ts(rs.getTimestamp("trial_ends_at")),
            ts(rs.getTimestamp("plan_expires_at")),
            rs.getString("billing_cycle"),
            ts(rs.getTimestamp("created_at")),
            ts(rs.getTimestamp("last_login_at")),
            rs.getLong("customers"),
            rs.getLong("events"),
            0L);

    @Transactional(readOnly = true)
    public Page<PhotographerRow> photographers(String query, String status, String planCode, String sort, int page, int size) {
        int pageSize = Math.min(Math.max(size, 1), 100);
        int pageNumber = Math.max(page, 0);
        StringBuilder sql = new StringBuilder("""
                SELECT b.*, count(*) OVER () AS total_rows,
                       (SELECT count(*) FROM fb_customer c WHERE c.photographer_id = b.id) AS customers,
                       (SELECT count(*) FROM fb_event_booking e WHERE e.photographer_id = b.id) AS events
                FROM (""").append(BASE).append(") b WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (query != null && !query.isBlank()) {
            String like = "%" + query.trim().toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
            sql.append(" AND (lower(b.full_name) LIKE ? OR lower(COALESCE(b.studio_name, '')) LIKE ? OR lower(b.email) LIKE ? OR b.mobile_number LIKE ?)");
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (status != null && !status.isBlank()) {
            if (!STATUSES.contains(status)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status filter.");
            }
            sql.append(" AND b.status = ?");
            params.add(status);
        }
        if (planCode != null && !planCode.isBlank()) {
            sql.append(" AND b.plan_code = ?");
            params.add(planCode);
        }
        sql.append(switch (sort == null ? "" : sort) {
            case "name" -> " ORDER BY lower(COALESCE(NULLIF(b.studio_name, ''), b.full_name))";
            case "lastLogin" -> " ORDER BY b.last_login_at DESC NULLS LAST";
            case "trialEnds" -> " ORDER BY b.trial_ends_at NULLS LAST";
            default -> " ORDER BY b.created_at DESC";
        });
        sql.append(" LIMIT ? OFFSET ?");
        params.add(pageSize);
        params.add(pageNumber * pageSize);
        long[] total = {0};
        List<PhotographerRow> rows = jdbc.query(sql.toString(), (rs, n) -> {
            total[0] = rs.getLong("total_rows");
            return ROW.mapRow(rs, n);
        }, params.toArray());
        Map<String, Long> storage = storageByPhotographer(rows.stream().map(r -> r.id().toString()).toList());
        List<PhotographerRow> withStorage = rows.stream().map(r -> new PhotographerRow(r.id(), r.fullName(), r.studioName(),
                r.email(), r.mobileNumber(), r.emailVerified(), r.mobileVerified(), r.planCode(), r.planName(), r.status(),
                r.trialEndsAt(), r.planExpiresAt(), r.billingCycle(), r.createdAt(), r.lastLoginAt(), r.customers(), r.events(),
                storage.getOrDefault(r.id().toString(), 0L))).toList();
        return new Page<>(withStorage, total[0], pageNumber, pageSize);
    }

    @Transactional(readOnly = true)
    public PhotographerDetail photographer(UUID id) {
        PhotographerRow row = jdbc.query("""
                        SELECT b.*, (SELECT count(*) FROM fb_customer c WHERE c.photographer_id = b.id) AS customers,
                               (SELECT count(*) FROM fb_event_booking e WHERE e.photographer_id = b.id) AS events
                        FROM (""" + BASE + ") b WHERE b.id = ?", ROW, id)
                .stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photographer not found."));
        Map<String, Object> notes = jdbc.queryForMap("SELECT admin_notes, suspended_reason FROM fb_user WHERE id = ?", id);
        var usage = entitlements.usage(id);
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("enquiries", count("SELECT count(*) FROM fb_enquiries WHERE photographer_id = ?", id));
        counts.put("customers", usage.customers());
        counts.put("events", row.events());
        counts.put("upcomingEvents", usage.upcomingEvents());
        counts.put("teamMembers", usage.teamMembers());
        counts.put("guestsRegistered", count("""
                SELECT count(*) FROM fb_event_guest g JOIN fb_event_booking e ON e.id = g.event_id WHERE e.photographer_id = ?
                """, id));
        counts.put("clientPaymentsRecorded", jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM fb_event_payment WHERE photographer_id = ?", BigDecimal.class, id));
        counts.put("expensesRecorded", jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM fb_expense WHERE photographer_id = ?", BigDecimal.class, id));
        counts.put("flipbooks", count("SELECT count(*) FROM fb_flipbook WHERE photographer_id = ?", id));
        counts.put("portfolioPhotos", count("SELECT count(*) FROM fb_portfolio_item WHERE photographer_id = ?", id));
        Map<String, Object> studio = jdbc.query("""
                        SELECT upi_id, portfolio_slug, portfolio_public, reminders_enabled
                        FROM fb_studio_profile WHERE photographer_id = ?
                        """, (rs, n) -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("upiConfigured", rs.getString("upi_id") != null);
                    map.put("portfolioSlug", rs.getString("portfolio_slug"));
                    map.put("portfolioPublic", rs.getBoolean("portfolio_public"));
                    map.put("remindersEnabled", rs.getBoolean("reminders_enabled"));
                    return map;
                }, id).stream().findFirst().orElse(Map.of());
        List<EventRow> events = jdbc.query("""
                        SELECT e.id, e.event_code, e.event_date, t.name AS event_type, c.name AS customer_name, e.price, e.created_at
                        FROM fb_event_booking e JOIN fb_event_list t ON t.id = e.event_type_id JOIN fb_customer c ON c.id = e.customer_id
                        WHERE e.photographer_id = ? ORDER BY e.event_date DESC LIMIT 15
                        """, (rs, n) -> new EventRow(rs.getObject("id", UUID.class), rs.getString("event_code"),
                        rs.getDate("event_date").toLocalDate(), rs.getString("event_type"), rs.getString("customer_name"),
                        rs.getString("price"), ts(rs.getTimestamp("created_at"))),
                id);
        List<PlanRequestView> requests = jdbc.query(subscriptionRequestSql() + " WHERE r.photographer_id = ? ORDER BY r.created_at DESC LIMIT 10",
                planRequestMapper(), id);
        PhotographerRow withStorage = new PhotographerRow(row.id(), row.fullName(), row.studioName(), row.email(),
                row.mobileNumber(), row.emailVerified(), row.mobileVerified(), row.planCode(), row.planName(), row.status(),
                row.trialEndsAt(), row.planExpiresAt(), row.billingCycle(), row.createdAt(), row.lastLoginAt(),
                row.customers(), row.events(), usage.storageBytes());
        return new PhotographerDetail(withStorage, entitlements.forPhotographer(id),
                new UsageView(usage.customers(), usage.upcomingEvents(), usage.teamMembers(), usage.storageBytes()),
                counts, studio, (String) notes.get("admin_notes"), (String) notes.get("suspended_reason"),
                events, subscriptions.history(id, 30), requests);
    }

    // ---- Actions -----------------------------------------------------------------------------------

    @Transactional
    public PhotographerDetail changePlan(String admin, UUID id, ChangePlanRequest request) {
        requireExists(id);
        Instant periodEnd = periodEnd(request.periodEnd(), request.months(), request.billingCycle(), request.planCode());
        String cycle = request.billingCycle() == null ? "MONTHLY" : request.billingCycle();
        subscriptions.changePlan(id, request.planCode(), cycle, periodEnd, request.amount(), request.reference(),
                request.note(), admin, "ADMIN_PLAN_CHANGE");
        jdbc.update("UPDATE fb_plan_request SET status = 'CANCELLED', handled_at = CURRENT_TIMESTAMP, handled_by = ?, admin_note = 'Superseded by an admin plan change' WHERE photographer_id = ? AND status = 'PENDING'",
                admin, id);
        audit.log(admin, "CHANGE_PLAN", "PHOTOGRAPHER", id, details("planCode", request.planCode(), "billingCycle", cycle,
                "periodEnd", periodEnd, "amount", request.amount(), "reference", request.reference()));
        return photographer(id);
    }

    @Transactional
    public PhotographerDetail extendTrial(String admin, UUID id, int days, String note) {
        requireExists(id);
        subscriptions.extendTrial(id, days, admin, note);
        audit.log(admin, "EXTEND_TRIAL", "PHOTOGRAPHER", id, details("days", days));
        return photographer(id);
    }

    @Transactional
    public PhotographerDetail suspend(String admin, UUID id, String reason) {
        String email = jdbc.query("SELECT email FROM fb_user WHERE id = ?", (rs, n) -> rs.getString(1), id).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photographer not found."));
        if (adminAccess.isAdmin(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Admin accounts can't be suspended from the portal.");
        }
        jdbc.update("UPDATE fb_user SET active = FALSE, suspended_reason = ? WHERE id = ?", reason.trim(), id);
        subscriptions.record(id, "SUSPENDED", subscriptions.currentPlanCode(id), null, null, null, null, null, reason, admin);
        accountStatus.forget(id);
        audit.log(admin, "SUSPEND", "PHOTOGRAPHER", id, details("reason", reason));
        return photographer(id);
    }

    @Transactional
    public PhotographerDetail reactivate(String admin, UUID id) {
        requireExists(id);
        jdbc.update("UPDATE fb_user SET active = TRUE, suspended_reason = NULL WHERE id = ?", id);
        subscriptions.record(id, "REACTIVATED", null, subscriptions.currentPlanCode(id), null, null, null, null, null, admin);
        accountStatus.forget(id);
        audit.log(admin, "REACTIVATE", "PHOTOGRAPHER", id, null);
        return photographer(id);
    }

    @Transactional
    public PhotographerDetail saveNotes(String admin, UUID id, String notes) {
        requireExists(id);
        jdbc.update("UPDATE fb_user SET admin_notes = ? WHERE id = ?", notes == null || notes.isBlank() ? null : notes, id);
        audit.log(admin, "UPDATE_NOTES", "PHOTOGRAPHER", id, null);
        return photographer(id);
    }

    // ---- Plan requests -------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PlanRequestView> planRequests(String status) {
        if (status == null || status.isBlank() || "ALL".equals(status)) {
            return jdbc.query(subscriptionRequestSql() + " ORDER BY r.created_at DESC LIMIT 200", planRequestMapper());
        }
        return jdbc.query(subscriptionRequestSql() + " WHERE r.status = ? ORDER BY r.created_at DESC LIMIT 200",
                planRequestMapper(), status);
    }

    @Transactional
    public PlanRequestView approve(String admin, UUID requestId, Integer months, BigDecimal amount, String reference, String note) {
        PlanRequestView request = pending(requestId);
        int period = months != null ? months : ("YEARLY".equals(request.billingCycle()) ? 12 : 1);
        Instant periodEnd = endOfDayAfterMonths(period);
        BigDecimal paid = amount != null ? amount : request.amount();
        subscriptions.changePlan(request.photographerId(), request.planCode(), request.billingCycle(), periodEnd, paid,
                reference, note, admin, "PLAN_REQUEST_APPROVED");
        jdbc.update("""
                UPDATE fb_plan_request SET status = 'APPROVED', handled_at = CURRENT_TIMESTAMP, handled_by = ?, admin_note = ?
                WHERE id = ?
                """, admin, note, requestId);
        audit.log(admin, "APPROVE_PLAN_REQUEST", "PHOTOGRAPHER", request.photographerId(),
                details("requestId", requestId, "planCode", request.planCode(), "months", period, "amount", paid, "reference", reference));
        notifyPhotographer(request.email(), "Your FrameBucket " + request.planName() + " plan is active",
                "Hello " + request.photographerName() + ",\n\nYour " + request.planName() + " plan is now active until "
                        + periodEnd.atZone(STUDIO_ZONE).toLocalDate() + ". Thank you for choosing FrameBucket!\n\nRegards,\nFrameBucket");
        return request(requestId);
    }

    @Transactional
    public PlanRequestView reject(String admin, UUID requestId, String note) {
        PlanRequestView request = pending(requestId);
        jdbc.update("""
                UPDATE fb_plan_request SET status = 'REJECTED', handled_at = CURRENT_TIMESTAMP, handled_by = ?, admin_note = ?
                WHERE id = ?
                """, admin, note, requestId);
        audit.log(admin, "REJECT_PLAN_REQUEST", "PHOTOGRAPHER", request.photographerId(), details("requestId", requestId, "note", note));
        notifyPhotographer(request.email(), "About your FrameBucket plan request",
                "Hello " + request.photographerName() + ",\n\nYour request for the " + request.planName()
                        + " plan was not approved." + (note == null || note.isBlank() ? "" : "\n\nNote: " + note)
                        + "\n\nReply to this email if you have questions.\n\nRegards,\nFrameBucket");
        return request(requestId);
    }

    // ---- helpers ----------------------------------------------------------------------------------------

    private PlanRequestView pending(UUID requestId) {
        PlanRequestView request = request(requestId);
        if (!"PENDING".equals(request.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This request has already been handled.");
        }
        return request;
    }

    private PlanRequestView request(UUID requestId) {
        return jdbc.query(subscriptionRequestSql() + " WHERE r.id = ?", planRequestMapper(), requestId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan request not found."));
    }

    private static String subscriptionRequestSql() {
        return SubscriptionManager.REQUEST_SELECT;
    }

    private static RowMapper<PlanRequestView> planRequestMapper() {
        return SubscriptionManager.REQUEST;
    }

    private void notifyPhotographer(String email, String subject, String body) {
        try {
            emailSender.send(email, subject, body);
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not email {} about their plan", email, exception);
        }
    }

    private Instant periodEnd(java.time.LocalDate periodEnd, Integer months, String billingCycle, String planCode) {
        if (periodEnd != null) {
            if (!periodEnd.isAfter(EntitlementService.today())) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "The paid-until date must be in the future.");
            }
            return ZonedDateTime.of(periodEnd, LocalTime.of(23, 59, 59), STUDIO_ZONE).toInstant();
        }
        if (months != null) {
            return endOfDayAfterMonths(months);
        }
        return null;
    }

    private static Instant endOfDayAfterMonths(int months) {
        return ZonedDateTime.of(EntitlementService.today().plusMonths(months), LocalTime.of(23, 59, 59), STUDIO_ZONE).toInstant();
    }

    private void requireExists(UUID id) {
        if (count("SELECT count(*) FROM fb_user WHERE id = ?", id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Photographer not found.");
        }
    }

    private long count(String sql, Object... params) {
        Long value = jdbc.queryForObject(sql, Long.class, params);
        return value == null ? 0 : value;
    }

    private BigDecimal money(String sql) {
        BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class);
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private long totalStorage() {
        if (!entitlements.dataServiceTablesExist()) {
            return 0;
        }
        try {
            Long value = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(COALESCE(size_bytes, 0) + COALESCE(processed_bytes, 0)), 0) FROM fb_data.media", Long.class);
            return value == null ? 0 : value;
        } catch (DataAccessException exception) {
            return 0;
        }
    }

    private Map<String, Long> storageByPhotographer(List<String> ids) {
        Map<String, Long> result = new HashMap<>();
        if (ids.isEmpty() || !entitlements.dataServiceTablesExist()) return result;
        try {
            jdbc.query(con -> {
                var ps = con.prepareStatement("""
                        SELECT e.photographer_id, COALESCE(SUM(COALESCE(m.size_bytes, 0) + COALESCE(m.processed_bytes, 0)), 0) AS bytes
                        FROM fb_data.media m JOIN fb_data.event e ON e.event_id = m.event_id
                        WHERE e.photographer_id = ANY (?) GROUP BY e.photographer_id
                        """);
                ps.setArray(1, con.createArrayOf("text", ids.toArray()));
                return ps;
            }, rs -> {
                result.put(rs.getString("photographer_id"), rs.getLong("bytes"));
            });
        } catch (DataAccessException exception) {
            LOGGER.debug("Storage usage unavailable", exception);
        }
        return result;
    }

    private static Map<String, Object> details(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (pairs[i + 1] != null) map.put(pairs[i].toString(), pairs[i + 1].toString());
        }
        return map;
    }

    private static Instant ts(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
