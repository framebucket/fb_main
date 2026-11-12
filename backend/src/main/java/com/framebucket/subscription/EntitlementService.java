package com.framebucket.subscription;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framebucket.subscription.Entitlements.Limits;
import com.framebucket.subscription.Entitlements.SubscriptionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Works out a photographer's effective plan from the database on every call (so admin changes apply
 * immediately, without a new login) and enforces features and limits.
 *
 * <ul>
 *   <li>Trial plan: its features until trial_ends_at, then the Free plan.</li>
 *   <li>Paid plan: its features until plan_expires_at (+ grace period), then the Free plan.
 *       A null plan_expires_at means no end date (set manually by an admin).</li>
 *   <li>Suspended accounts get no features.</li>
 * </ul>
 */
@Service
public class EntitlementService {
    public static final String FREE_PLAN = "FREE";
    public static final Duration GRACE_PERIOD = Duration.ofDays(3);
    private static final ZoneId STUDIO_ZONE = ZoneId.of("Asia/Kolkata");
    private static final Logger LOGGER = LoggerFactory.getLogger(EntitlementService.class);
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public EntitlementService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public record Usage(long customers, long upcomingEvents, long teamMembers, long storageBytes) {
    }

    private record PlanRow(String code, String name, int trialDays, Map<String, Object> entitlements) {
    }

    public Entitlements forPhotographer(UUID photographerId) {
        var rows = jdbc.query("""
                        SELECT u.active, u.trial_ends_at, u.plan_expires_at, u.billing_cycle,
                               s.plan_code, s.plan_name, s.trial_days, s.entitlements::text AS entitlements
                        FROM fb_user u LEFT JOIN fb_user_subscription s ON s.id = u.subscription_id
                        WHERE u.id = ?
                        """, (rs, n) -> new Object[]{
                        rs.getBoolean("active"),
                        toInstant(rs.getTimestamp("trial_ends_at")),
                        toInstant(rs.getTimestamp("plan_expires_at")),
                        rs.getString("billing_cycle"),
                        rs.getString("plan_code") == null ? null
                                : new PlanRow(rs.getString("plan_code"), rs.getString("plan_name"), rs.getInt("trial_days"),
                                parse(rs.getString("entitlements")))},
                photographerId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Photographer account not found.");
        }
        Object[] row = rows.get(0);
        boolean active = (Boolean) row[0];
        Instant trialEndsAt = (Instant) row[1];
        Instant planExpiresAt = (Instant) row[2];
        String billingCycle = (String) row[3];
        PlanRow subscribed = (PlanRow) row[4];
        Instant now = Instant.now();

        if (!active) {
            return new Entitlements(subscribed == null ? FREE_PLAN : subscribed.code(),
                    subscribed == null ? "Free" : subscribed.name(), subscribed == null ? null : subscribed.code(),
                    SubscriptionStatus.SUSPENDED, trialEndsAt, planExpiresAt, billingCycle,
                    Collections.emptySet(), new Limits(0, 0, 0, 0, 0));
        }

        SubscriptionStatus status;
        PlanRow effective;
        if (subscribed == null) {
            status = SubscriptionStatus.FREE;
            effective = null;
        } else if (subscribed.trialDays() > 0) {
            boolean running = trialEndsAt != null && trialEndsAt.isAfter(now);
            status = running ? SubscriptionStatus.TRIAL : SubscriptionStatus.TRIAL_ENDED;
            effective = running ? subscribed : null;
        } else if (FREE_PLAN.equals(subscribed.code())) {
            status = SubscriptionStatus.FREE;
            effective = subscribed;
        } else if (planExpiresAt == null || planExpiresAt.isAfter(now)) {
            status = SubscriptionStatus.ACTIVE;
            effective = subscribed;
        } else if (planExpiresAt.plus(GRACE_PERIOD).isAfter(now)) {
            status = SubscriptionStatus.GRACE;
            effective = subscribed;
        } else {
            status = SubscriptionStatus.LAPSED;
            effective = null;
        }
        if (effective == null) {
            effective = freePlan();
        }
        return new Entitlements(effective.code(), effective.name(), subscribed == null ? null : subscribed.code(),
                status, trialEndsAt, planExpiresAt, billingCycle,
                features(effective.entitlements()), limits(effective.entitlements()));
    }

    /** Throws {@link PlanRestrictionException} unless the photographer's plan includes the feature. */
    public Entitlements require(UUID photographerId, Feature feature) {
        Entitlements entitlements = forPhotographer(photographerId);
        if (entitlements.status() == SubscriptionStatus.SUSPENDED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account is suspended. Contact FrameBucket support.");
        }
        if (!entitlements.has(feature)) {
            throw PlanRestrictionException.missingFeature(feature);
        }
        return entitlements;
    }

    public boolean has(UUID photographerId, Feature feature) {
        try {
            return forPhotographer(photographerId).has(feature);
        } catch (ResponseStatusException exception) {
            return false;
        }
    }

    // ---- Limits ------------------------------------------------------------------------------

    public void requireCanAddCustomer(UUID photographerId) {
        Integer max = forPhotographer(photographerId).limits().customers();
        if (max != null && countCustomers(photographerId) >= max) {
            throw PlanRestrictionException.limitReached("customers",
                    "Your plan allows " + max + " customers. Upgrade your plan to add more.");
        }
    }

    public void requireCanAddUpcomingEvent(UUID photographerId, LocalDate eventDate) {
        if (eventDate != null && eventDate.isBefore(today())) {
            return;
        }
        Integer max = forPhotographer(photographerId).limits().upcomingEvents();
        if (max != null && countUpcomingEvents(photographerId) >= max) {
            throw PlanRestrictionException.limitReached("upcomingEvents",
                    "Your plan allows " + max + " upcoming events at a time. Upgrade your plan to book more.");
        }
    }

    public void requireCanAddTeamMember(UUID photographerId) {
        Entitlements entitlements = require(photographerId, Feature.TEAM);
        Integer max = entitlements.limits().teamMembers();
        if (max != null && countTeamMembers(photographerId) >= max) {
            throw PlanRestrictionException.limitReached("teamMembers",
                    "Your plan allows " + max + (max == 1 ? " team member" : " team members") + ". Upgrade your plan to add more.");
        }
    }

    public void requireStorageFor(UUID photographerId, long additionalBytes) {
        Integer maxGb = forPhotographer(photographerId).limits().storageGb();
        if (maxGb == null) {
            return;
        }
        long limitBytes = maxGb.longValue() * 1024L * 1024L * 1024L;
        if (storageBytes(photographerId) + Math.max(0, additionalBytes) > limitBytes) {
            throw PlanRestrictionException.limitReached("storageGb",
                    "This upload would go over your plan's " + maxGb + " GB of photo storage. Upgrade your plan or delete old galleries.");
        }
    }

    // ---- Usage ---------------------------------------------------------------------------------

    public Usage usage(UUID photographerId) {
        return new Usage(countCustomers(photographerId), countUpcomingEvents(photographerId),
                countTeamMembers(photographerId), storageBytes(photographerId));
    }

    public long countCustomers(UUID photographerId) {
        return count("SELECT count(*) FROM fb_customer WHERE photographer_id = ?", photographerId);
    }

    public long countUpcomingEvents(UUID photographerId) {
        Long value = jdbc.queryForObject(
                "SELECT count(*) FROM fb_event_booking WHERE photographer_id = ? AND event_date >= ?",
                Long.class, photographerId, Date.valueOf(today()));
        return value == null ? 0 : value;
    }

    public long countTeamMembers(UUID photographerId) {
        return count("SELECT count(*) FROM fb_team_member WHERE photographer_id = ?", photographerId)
                + count("SELECT count(*) FROM fb_freelancer WHERE photographer_id = ?", photographerId);
    }

    /** Originals + processed copies stored by the data service for this photographer. */
    public long storageBytes(UUID photographerId) {
        if (!dataServiceTablesExist()) {
            return 0;
        }
        try {
            Long value = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(COALESCE(m.size_bytes, 0) + COALESCE(m.processed_bytes, 0)), 0)
                    FROM fb_data.media m JOIN fb_data.event e ON e.event_id = m.event_id
                    WHERE e.photographer_id = ?
                    """, Long.class, photographerId.toString());
            return value == null ? 0 : value;
        } catch (DataAccessException exception) {
            // The fb_data schema is created by the data service; it may not exist yet locally.
            LOGGER.debug("Could not read storage usage", exception);
            return 0;
        }
    }

    /**
     * The fb_data schema belongs to the Python data service (same database). Check it exists before
     * querying it: a failed query would abort the surrounding PostgreSQL transaction.
     */
    public boolean dataServiceTablesExist() {
        Boolean exists = jdbc.queryForObject("SELECT to_regclass('fb_data.media') IS NOT NULL AND to_regclass('fb_data.event') IS NOT NULL",
                Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    // ---- helpers -------------------------------------------------------------------------------

    public static LocalDate today() {
        return LocalDate.now(STUDIO_ZONE);
    }

    private long count(String sql, UUID photographerId) {
        Long value = jdbc.queryForObject(sql, Long.class, photographerId);
        return value == null ? 0 : value;
    }

    private PlanRow freePlan() {
        return jdbc.query("""
                        SELECT plan_code, plan_name, trial_days, entitlements::text AS entitlements
                        FROM fb_user_subscription WHERE plan_code = ?
                        """, (rs, n) -> new PlanRow(rs.getString("plan_code"), rs.getString("plan_name"), 0,
                        parse(rs.getString("entitlements"))), FREE_PLAN)
                .stream().findFirst()
                .orElse(new PlanRow(FREE_PLAN, "Free", 0, Map.of(
                        "features", List.of("CRM", "QUOTATIONS", "TEAM", "GALLERY"),
                        "limits", Map.of("customers", 25, "upcomingEvents", 3, "teamMembers", 1, "storageGb", 5))));
    }

    Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP);
        } catch (Exception exception) {
            LOGGER.warn("Ignoring unreadable plan entitlements: {}", json);
            return Map.of();
        }
    }

    static Set<Feature> features(Map<String, Object> entitlements) {
        EnumSet<Feature> features = EnumSet.noneOf(Feature.class);
        if (entitlements.get("features") instanceof List<?> list) {
            for (Object item : list) {
                Feature feature = item == null ? null : Feature.parse(item.toString());
                if (feature != null) features.add(feature);
            }
        }
        return Collections.unmodifiableSet(features);
    }

    @SuppressWarnings("unchecked")
    static Limits limits(Map<String, Object> entitlements) {
        Object raw = entitlements.get("limits");
        return Limits.from(raw instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of());
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
