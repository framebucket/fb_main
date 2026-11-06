package com.framebucket.user.subscription.repository;

import com.framebucket.user.subscription.model.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {
    Optional<SubscriptionPlan> findByPlanCodeAndActiveTrue(String planCode);

    List<SubscriptionPlan> findAllByActiveTrueAndTrialDaysEqualsOrderByPriceAmountAsc(int trialDays);
}
