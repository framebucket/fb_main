package com.framebucket.user.subscription.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class SubscriptionDtos {
    private SubscriptionDtos() {
    }

    public record PlanResponse(
            UUID id,
            String code,
            String name,
            String description,
            BigDecimal monthlyPrice,
            BigDecimal yearlyPrice,
            BigDecimal yearlySavings,
            String currencyCode,
            List<String> features,
            boolean customerOrderTracking,
            boolean aiImageDelivery,
            boolean quotationCalendar,
            boolean dataModuleEnabled,
            boolean rawPhotoSelectionEnabled,
            Long maxStorageGb
    ) {
    }
}
