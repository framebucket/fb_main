package com.framebucket.subscription.service;

import com.framebucket.subscription.api.SubscriptionDtos.PlanResponse;
import com.framebucket.subscription.model.SubscriptionPlan;
import com.framebucket.subscription.repository.SubscriptionPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class SubscriptionPlanService {
    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);
    private static final BigDecimal YEARLY_FACTOR = new BigDecimal("0.90");

    private final SubscriptionPlanRepository planRepository;

    public SubscriptionPlanService(SubscriptionPlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> getPaidPlans() {
        return planRepository.findAllByActiveTrueAndTrialDaysEqualsOrderByPriceAmountAsc(0)
                .stream()
                .map(SubscriptionPlanService::toResponse)
                .toList();
    }

    private static PlanResponse toResponse(SubscriptionPlan plan) {
        BigDecimal monthlyPrice = plan.getPriceAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal fullYearPrice = monthlyPrice.multiply(MONTHS_PER_YEAR);
        BigDecimal yearlyPrice = fullYearPrice.multiply(YEARLY_FACTOR).setScale(2, RoundingMode.HALF_UP);
        BigDecimal yearlySavings = fullYearPrice.subtract(yearlyPrice).setScale(2, RoundingMode.HALF_UP);

        List<String> features = new ArrayList<>();
        if (plan.isCustomerOrderTracking()) {
            features.add("Photoshoot order tracking with customer alerts");
        }
        if (plan.isAiImageDelivery()) {
            features.add("AI image scanning and photo delivery");
        }
        if (plan.isQuotationCalendar()) {
            features.add("Quotation generator and calendar booking");
        }
        if (plan.isRawPhotoSelection()) {
            features.add("Upload RAW photos for album selection up to " + plan.getMaxStorageGb() + " GB");
        }

        return new PlanResponse(
                plan.getId(),
                plan.getPlanCode(),
                plan.getPlanName(),
                plan.getDescription(),
                monthlyPrice,
                yearlyPrice,
                yearlySavings,
                plan.getCurrencyCode(),
                List.copyOf(features),
                plan.isCustomerOrderTracking(),
                plan.isAiImageDelivery(),
                plan.isQuotationCalendar(),
                plan.isDataModuleEnabled(),
                plan.isRawPhotoSelection(),
                plan.getMaxStorageGb()
        );
    }
}
