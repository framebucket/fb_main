package com.framebucket.subscription;

import com.framebucket.subscription.SubscriptionDtos.PlanRequestView;
import com.framebucket.subscription.SubscriptionDtos.SubscriptionEventView;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Changes a photographer's plan and keeps the subscription history and plan requests. */
@Service
public class SubscriptionManager {
    public static final RowMapper<SubscriptionEventView> EVENT = (rs, n) -> new SubscriptionEventView(
            rs.getObject("id", UUID.class),
            rs.getString("action"),
            rs.getString("from_plan_code"),
            rs.getString("to_plan_code"),
            rs.getString("billing_cycle"),
            ts(rs.getTimestamp("period_end")),
            rs.getBigDecimal("amount"),
            rs.getString("reference"),
            rs.getString("note"),
            rs.getString("actor"),
            ts(rs.getTimestamp("created_at")));

    public static final String REQUEST_SELECT = """
            SELECT r.*, u.full_name, u.studio_name, u.email, u.mobile_number, p.plan_name,
                   CASE WHEN r.billing_cycle = 'YEARLY' THEN COALESCE(p.yearly_price_amount, p.price_amount * 10)
                        ELSE p.price_amount END AS amount,
                   cur.plan_code AS current_plan_code
            FROM fb_plan_request r
            JOIN fb_user u ON u.id = r.photographer_id
            LEFT JOIN fb_user_subscription p ON p.plan_code = r.plan_code
            LEFT JOIN fb_user_subscription cur ON cur.id = u.subscription_id
            """;

    public static final RowMapper<PlanRequestView> REQUEST = (rs, n) -> new PlanRequestView(
            rs.getObject("id", UUID.class),
            rs.getObject("photographer_id", UUID.class),
            rs.getString("full_name"),
            rs.getString("studio_name"),
            rs.getString("email"),
            rs.getString("mobile_number"),
            rs.getString("plan_code"),
            rs.getString("plan_name"),
            rs.getString("billing_cycle"),
            rs.getBigDecimal("amount"),
            rs.getString("status"),
            rs.getString("message"),
            rs.getString("admin_note"),
            rs.getString("handled_by"),
            ts(rs.getTimestamp("handled_at")),
            ts(rs.getTimestamp("created_at")),
            rs.getString("current_plan_code"));

    private final JdbcTemplate jdbc;

    public SubscriptionManager(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Puts the photographer on a plan.
     *
     * @param periodEnd paid-through date (null = no end date). Ignored for the trial and Free plans.
     */
    @Transactional
    public void changePlan(UUID photographerId, String planCode, String billingCycle, Instant periodEnd,
                           BigDecimal amount, String reference, String note, String actor, String action) {
        var plan = jdbc.query("SELECT id, trial_days, active FROM fb_user_subscription WHERE plan_code = ?",
                        (rs, n) -> new Object[]{rs.getObject("id", UUID.class), rs.getInt("trial_days"), rs.getBoolean("active")},
                        planCode)
                .stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found."));
        if (!(Boolean) plan[2]) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "That plan is no longer offered.");
        }
        boolean trialPlan = (Integer) plan[1] > 0;
        boolean freePlan = EntitlementService.FREE_PLAN.equals(planCode);
        String fromPlan = currentPlanCode(photographerId);
        Instant recordedEnd;
        if (trialPlan) {
            int days = (Integer) plan[1];
            recordedEnd = periodEnd != null ? periodEnd : Instant.now().plusSeconds(days * 86400L);
            jdbc.update("""
                            UPDATE fb_user SET subscription_id = ?, trial_ends_at = ?, plan_expires_at = NULL, billing_cycle = NULL
                            WHERE id = ?
                            """, plan[0], Timestamp.from(recordedEnd), photographerId);
        } else {
            recordedEnd = freePlan ? null : periodEnd;
            jdbc.update("UPDATE fb_user SET subscription_id = ?, plan_expires_at = ?, billing_cycle = ? WHERE id = ?",
                    plan[0], recordedEnd == null ? null : Timestamp.from(recordedEnd),
                    freePlan ? null : billingCycle, photographerId);
        }
        record(photographerId, action, fromPlan, planCode, trialPlan || freePlan ? null : billingCycle,
                recordedEnd, amount, reference, note, actor);
    }

    @Transactional
    public void extendTrial(UUID photographerId, int days, String actor, String note) {
        if (days < 1 || days > 90) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Extend the trial by 1 to 90 days.");
        }
        String trialCode = jdbc.query("SELECT plan_code FROM fb_user_subscription WHERE trial_days > 0 AND active ORDER BY sort_order LIMIT 1",
                (rs, n) -> rs.getString(1)).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No active trial plan is configured."));
        String fromPlan = currentPlanCode(photographerId);
        jdbc.update("""
                        UPDATE fb_user SET subscription_id = (SELECT id FROM fb_user_subscription WHERE plan_code = ?),
                            trial_ends_at = GREATEST(COALESCE(trial_ends_at, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP) + make_interval(days => ?),
                            plan_expires_at = NULL, billing_cycle = NULL
                        WHERE id = ?
                        """, trialCode, days, photographerId);
        Timestamp end = jdbc.queryForObject("SELECT trial_ends_at FROM fb_user WHERE id = ?", Timestamp.class, photographerId);
        record(photographerId, "TRIAL_EXTENDED", fromPlan, trialCode, null, ts(end), null, null,
                note == null || note.isBlank() ? "Trial extended by " + days + " days" : note, actor);
    }

    public void record(UUID photographerId, String action, String fromPlan, String toPlan, String billingCycle,
                       Instant periodEnd, BigDecimal amount, String reference, String note, String actor) {
        jdbc.update("""
                        INSERT INTO fb_subscription_event (id, photographer_id, action, from_plan_code, to_plan_code,
                            billing_cycle, period_end, amount, reference, note, actor)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, UUID.randomUUID(), photographerId, action, fromPlan, toPlan, billingCycle,
                periodEnd == null ? null : Timestamp.from(periodEnd), amount, blankToNull(reference), blankToNull(note), actor);
    }

    public List<SubscriptionEventView> history(UUID photographerId, int limit) {
        return jdbc.query("SELECT * FROM fb_subscription_event WHERE photographer_id = ? ORDER BY created_at DESC LIMIT ?",
                EVENT, photographerId, limit);
    }

    public PlanRequestView pendingRequest(UUID photographerId) {
        return jdbc.query(REQUEST_SELECT + " WHERE r.photographer_id = ? AND r.status = 'PENDING'", REQUEST, photographerId)
                .stream().findFirst().orElse(null);
    }

    public String currentPlanCode(UUID photographerId) {
        return jdbc.query("""
                        SELECT s.plan_code FROM fb_user u LEFT JOIN fb_user_subscription s ON s.id = u.subscription_id
                        WHERE u.id = ?
                        """, (rs, n) -> rs.getString(1), photographerId)
                .stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photographer not found."));
    }

    static Instant ts(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
