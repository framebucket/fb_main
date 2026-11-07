package com.framebucket.dashboard.api;

import com.framebucket.dashboard.api.DashboardDtos.DashboardSummaryResponse;
import com.framebucket.dashboard.service.DashboardService;
import com.framebucket.user.auth.model.PhotographerPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public DashboardSummaryResponse summary(@AuthenticationPrincipal PhotographerPrincipal photographer) {
        return dashboardService.summaryForPhotographer(photographer);
    }
}
