package com.framebucket.user.subscription.api;

import com.framebucket.user.subscription.api.SubscriptionDtos.PlanResponse;
import com.framebucket.user.subscription.service.SubscriptionPlanService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionPlanController {
    private final SubscriptionPlanService planService;

    public SubscriptionPlanController(SubscriptionPlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    public List<PlanResponse> getPlans() {
        return planService.getPaidPlans();
    }
}
