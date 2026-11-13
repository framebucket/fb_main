package com.framebucket.subscription;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * What a photographer can do right now: the effective plan (after trial/expiry rules), its features
 * and its limits. Limits are null when unlimited.
 */
public record Entitlements(
        String planCode,
        String planName,
        String subscribedPlanCode,
        SubscriptionStatus status,
        Instant trialEndsAt,
        Instant planExpiresAt,
        String billingCycle,
        Set<Feature> features,
        Limits limits
) {
    public boolean has(Feature feature) {
        return features.contains(feature);
    }

    public record Limits(
            Integer customers,
            Integer upcomingEvents,
            Integer teamMembers,
            Integer storageGb,
            Integer whatsappMessagesPerMonth,
            Integer editingPresets,
            Integer albumReferences
    ) {
        public static Limits from(Map<String, Object> raw) {
            return new Limits(
                    number(raw, "customers"),
                    number(raw, "upcomingEvents"),
                    number(raw, "teamMembers"),
                    number(raw, "storageGb"),
                    number(raw, "whatsappMessagesPerMonth"),
                    number(raw, "editingPresets"),
                    number(raw, "albumReferences"));
        }

        private static Integer number(Map<String, Object> raw, String key) {
            Object value = raw == null ? null : raw.get(key);
            return value instanceof Number n ? n.intValue() : null;
        }
    }

    /** Effective subscription state, shown to photographers and admins. */
    public enum SubscriptionStatus {
        /** Free trial running. */
        TRIAL,
        /** Paid plan active. */
        ACTIVE,
        /** Paid period ended less than the grace period ago; still has the paid features. */
        GRACE,
        /** On the Free plan by choice. */
        FREE,
        /** Trial ended without upgrading; on Free. */
        TRIAL_ENDED,
        /** Paid plan expired; on Free until renewed. */
        LAPSED,
        /** Disabled by an admin. */
        SUSPENDED
    }
}
