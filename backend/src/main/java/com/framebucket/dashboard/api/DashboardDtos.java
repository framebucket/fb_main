package com.framebucket.dashboard.api;

import java.math.BigDecimal;
import java.util.List;

public final class DashboardDtos {
    private DashboardDtos() {
    }

    public record ActivitySummary(long enquiries, long customers, long events) {
    }

    public record RevenueByEventType(String eventTypeName, BigDecimal revenue) {
    }

    public record DashboardSummaryResponse(
            String monthLabel,
            String financialYearLabel,
            ActivitySummary activityThisMonth,
            BigDecimal revenueThisMonth,
            BigDecimal revenueThisFinancialYear,
            List<RevenueByEventType> revenueByEventTypeFinancialYear
    ) {
    }
}
