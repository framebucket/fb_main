package com.framebucket.user.subscription.api;

import com.framebucket.subscription.PlanCatalogService;
import com.framebucket.subscription.SubscriptionDtos.PlanView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionPlanController {
    private final PlanCatalogService catalog;

    public SubscriptionPlanController(PlanCatalogService catalog) {
        this.catalog = catalog;
    }

    /** Public pricing: active, public plans (Free and paid) with their features and limits. */
    @GetMapping
    public List<PlanView> getPlans() {
        return catalog.publicPlans();
    }
}
