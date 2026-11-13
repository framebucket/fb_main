package com.framebucket.subscription;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framebucket.subscription.Entitlements.Limits;
import com.framebucket.subscription.SubscriptionDtos.PlanUpdateRequest;
import com.framebucket.subscription.SubscriptionDtos.PlanView;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Plan catalogue: public pricing list and admin editing. */
@Service
public class PlanCatalogService {
    private static final String SELECT = """
            SELECT s.*, s.entitlements::text AS entitlements_json,
                   (SELECT count(*) FROM fb_user u WHERE u.subscription_id = s.id) AS subscriber_count
            FROM fb_user_subscription s
            """;

    private final JdbcTemplate jdbc;
    private final EntitlementService entitlements;
    private final ObjectMapper objectMapper;

    public PlanCatalogService(JdbcTemplate jdbc, EntitlementService entitlements, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.entitlements = entitlements;
        this.objectMapper = objectMapper;
    }

    private RowMapper<PlanView> mapper() {
        return (rs, n) -> {
            Map<String, Object> raw = entitlements.parse(rs.getString("entitlements_json"));
            Set<Feature> features = EntitlementService.features(raw);
            BigDecimal monthly = rs.getBigDecimal("price_amount").setScale(2, RoundingMode.HALF_UP);
            BigDecimal yearly = rs.getBigDecimal("yearly_price_amount");
            if (yearly == null) {
                yearly = monthly.multiply(BigDecimal.TEN).setScale(2, RoundingMode.HALF_UP);
            }
            BigDecimal savings = monthly.multiply(BigDecimal.valueOf(12)).subtract(yearly).max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
            return new PlanView(
                    rs.getObject("id", java.util.UUID.class),
                    rs.getString("plan_code"),
                    rs.getString("plan_name"),
                    rs.getString("tagline"),
                    rs.getString("description"),
                    monthly,
                    yearly.setScale(2, RoundingMode.HALF_UP),
                    savings,
                    rs.getString("currency_code"),
                    rs.getBoolean("highlighted"),
                    rs.getBoolean("public_plan"),
                    rs.getBoolean("active"),
                    rs.getInt("trial_days"),
                    rs.getInt("sort_order"),
                    features.stream().map(Enum::name).toList(),
                    features.stream().map(Feature::label).toList(),
                    EntitlementService.limits(raw),
                    rs.getLong("subscriber_count"));
        };
    }

    @Transactional(readOnly = true)
    public List<PlanView> publicPlans() {
        return jdbc.query(SELECT + " WHERE s.active AND s.public_plan ORDER BY s.sort_order, s.price_amount", mapper())
                .stream().map(PlanView::withoutSubscriberCount).toList();
    }

    @Transactional(readOnly = true)
    public List<PlanView> allPlans() {
        return jdbc.query(SELECT + " ORDER BY s.active DESC, s.sort_order, s.price_amount", mapper());
    }

    @Transactional(readOnly = true)
    public PlanView plan(String code) {
        return jdbc.query(SELECT + " WHERE s.plan_code = ?", mapper(), code).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found."));
    }

    @Transactional
    public PlanView update(String code, PlanUpdateRequest request) {
        PlanView existing = plan(code);
        Set<String> features = new LinkedHashSet<>();
        for (String value : request.features()) {
            Feature feature = Feature.parse(value);
            if (feature == null) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Unknown feature: " + value);
            }
            features.add(feature.name());
        }
        Limits limits = request.limits() == null ? new Limits(null, null, null, null, null, null, null) : request.limits();
        Map<String, Object> limitMap = new LinkedHashMap<>();
        limitMap.put("customers", limits.customers());
        limitMap.put("upcomingEvents", limits.upcomingEvents());
        limitMap.put("teamMembers", limits.teamMembers());
        limitMap.put("storageGb", limits.storageGb());
        limitMap.put("whatsappMessagesPerMonth", limits.whatsappMessagesPerMonth());
        limitMap.put("editingPresets", limits.editingPresets());
        limitMap.put("albumReferences", limits.albumReferences());
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("features", new ArrayList<>(features));
        json.put("limits", limitMap);
        String entitlementsJson;
        try {
            entitlementsJson = objectMapper.writeValueAsString(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
        if (existing.trialDays() > 0 && request.trialDays() <= 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "The trial plan must keep at least one trial day.");
        }
        if (EntitlementService.FREE_PLAN.equals(code) && !request.active()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The Free plan can't be deactivated: accounts fall back to it when a trial or paid plan ends.");
        }
        jdbc.update("""
                        UPDATE fb_user_subscription SET plan_name = ?, tagline = ?, description = ?, price_amount = ?,
                            yearly_price_amount = ?, highlighted = ?, public_plan = ?, active = ?, sort_order = ?,
                            trial_days = ?, entitlements = CAST(? AS jsonb),
                            data_module_enabled = ?, max_storage_gb = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE plan_code = ?
                        """,
                request.name().trim(), blankToNull(request.tagline()), blankToNull(request.description()),
                request.monthlyPrice(), request.yearlyPrice(), request.highlighted(), request.publicPlan(),
                request.active(), request.sortOrder(), existing.trialDays() > 0 ? request.trialDays() : 0,
                entitlementsJson, features.contains(Feature.GALLERY.name()),
                limits.storageGb() == null ? null : limits.storageGb().longValue(), code);
        return plan(code);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
