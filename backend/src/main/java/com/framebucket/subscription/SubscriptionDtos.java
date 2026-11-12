package com.framebucket.subscription;

import com.framebucket.subscription.Entitlements.Limits;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SubscriptionDtos {
    private SubscriptionDtos() {
    }

    public record PlanView(
            UUID id,
            String code,
            String name,
            String tagline,
            String description,
            BigDecimal monthlyPrice,
            BigDecimal yearlyPrice,
            BigDecimal yearlySavings,
            String currencyCode,
            boolean highlighted,
            boolean publicPlan,
            boolean active,
            int trialDays,
            int sortOrder,
            List<String> features,
            List<String> featureLabels,
            Limits limits,
            Long subscriberCount
    ) {
        PlanView withoutSubscriberCount() {
            return new PlanView(id, code, name, tagline, description, monthlyPrice, yearlyPrice, yearlySavings,
                    currencyCode, highlighted, publicPlan, active, trialDays, sortOrder, features, featureLabels,
                    limits, null);
        }
    }

    public record PlanUpdateRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 160) String tagline,
            @Size(max = 500) String description,
            @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal monthlyPrice,
            @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal yearlyPrice,
            boolean highlighted,
            boolean publicPlan,
            boolean active,
            @Min(0) @Max(1000) int sortOrder,
            @Min(0) @Max(365) int trialDays,
            @NotNull @Size(max = 30) List<@NotBlank String> features,
            Limits limits
    ) {
    }

    public record FeatureView(String code, String label) {
    }

    public record UsageView(long customers, long upcomingEvents, long teamMembers, long storageBytes) {
    }

    public record PlanRequestView(
            UUID id,
            UUID photographerId,
            String photographerName,
            String studioName,
            String email,
            String mobileNumber,
            String planCode,
            String planName,
            String billingCycle,
            BigDecimal amount,
            String status,
            String message,
            String adminNote,
            String handledBy,
            Instant handledAt,
            Instant createdAt,
            String currentPlanCode
    ) {
    }

    public record SubscriptionEventView(
            UUID id,
            String action,
            String fromPlanCode,
            String toPlanCode,
            String billingCycle,
            Instant periodEnd,
            BigDecimal amount,
            String reference,
            String note,
            String actor,
            Instant createdAt
    ) {
    }

    /** What the photographer sees on their "Plan & usage" card. */
    public record MySubscriptionView(
            Entitlements entitlements,
            UsageView usage,
            PlanRequestView pendingRequest,
            List<SubscriptionEventView> history
    ) {
    }

    public record PlanRequestCreate(
            @NotBlank @Size(max = 40) String planCode,
            @NotNull @Pattern(regexp = "MONTHLY|YEARLY") String billingCycle,
            @Size(max = 500) String message
    ) {
    }
}
