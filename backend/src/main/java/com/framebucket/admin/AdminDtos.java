package com.framebucket.admin;

import com.framebucket.subscription.Entitlements;
import com.framebucket.subscription.SubscriptionDtos.PlanRequestView;
import com.framebucket.subscription.SubscriptionDtos.SubscriptionEventView;
import com.framebucket.subscription.SubscriptionDtos.UsageView;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AdminDtos {
    private AdminDtos() {
    }

    public record Overview(
            long photographers,
            long verifiedPhotographers,
            long newLast7Days,
            long newLast30Days,
            Map<String, Long> byStatus,
            Map<String, Long> payingByPlan,
            BigDecimal monthlyRecurringRevenue,
            long trialsEndingIn7Days,
            long pendingPlanRequests,
            long customers,
            long enquiries,
            long events,
            long upcomingEvents,
            long guestsRegistered,
            BigDecimal clientPaymentsRecorded,
            long storageBytes,
            BigDecimal subscriptionRevenueLast30Days,
            List<DailyCount> signupsLast30Days
    ) {
    }

    public record DailyCount(LocalDate day, long count) {
    }

    public record PhotographerRow(
            UUID id,
            String fullName,
            String studioName,
            String email,
            String mobileNumber,
            boolean emailVerified,
            boolean mobileVerified,
            String planCode,
            String planName,
            String status,
            Instant trialEndsAt,
            Instant planExpiresAt,
            String billingCycle,
            Instant createdAt,
            Instant lastLoginAt,
            long customers,
            long events,
            long storageBytes
    ) {
    }

    public record Page<T>(List<T> items, long total, int page, int size) {
    }

    public record EventRow(UUID id, String eventCode, LocalDate eventDate, String eventType, String customerName,
                           String price, Instant createdAt) {
    }

    public record PhotographerDetail(
            PhotographerRow photographer,
            Entitlements entitlements,
            UsageView usage,
            Map<String, Object> counts,
            Map<String, Object> studio,
            String adminNotes,
            String suspendedReason,
            List<EventRow> recentEvents,
            List<SubscriptionEventView> history,
            List<PlanRequestView> planRequests
    ) {
    }

    public record AuditEntry(UUID id, String adminEmail, String action, String targetType, String targetId,
                             String targetName, String details, Instant createdAt) {
    }

    // ---- Requests --------------------------------------------------------------------------------

    public record ChangePlanRequest(
            @NotBlank @Size(max = 40) String planCode,
            @Pattern(regexp = "MONTHLY|YEARLY") String billingCycle,
            @Min(1) @Max(60) Integer months,
            LocalDate periodEnd,
            @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal amount,
            @Size(max = 120) String reference,
            @Size(max = 500) String note
    ) {
    }

    public record ExtendTrialRequest(@Min(1) @Max(90) int days, @Size(max = 500) String note) {
    }

    public record SuspendRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record NotesRequest(@Size(max = 5000) String notes) {
    }

    public record ApproveRequest(
            @Min(1) @Max(60) Integer months,
            @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal amount,
            @Size(max = 120) String reference,
            @Size(max = 500) String note
    ) {
    }

    public record RejectRequest(@Size(max = 500) String note) {
    }
}
