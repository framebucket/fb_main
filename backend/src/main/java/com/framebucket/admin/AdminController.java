package com.framebucket.admin;

import com.framebucket.admin.AdminDtos.ApproveRequest;
import com.framebucket.admin.AdminDtos.AuditEntry;
import com.framebucket.admin.AdminDtos.ChangePlanRequest;
import com.framebucket.admin.AdminDtos.ExtendTrialRequest;
import com.framebucket.admin.AdminDtos.NotesRequest;
import com.framebucket.admin.AdminDtos.Overview;
import com.framebucket.admin.AdminDtos.Page;
import com.framebucket.admin.AdminDtos.PhotographerDetail;
import com.framebucket.admin.AdminDtos.PhotographerRow;
import com.framebucket.admin.AdminDtos.RejectRequest;
import com.framebucket.admin.AdminDtos.SuspendRequest;
import com.framebucket.subscription.Feature;
import com.framebucket.subscription.PlanCatalogService;
import com.framebucket.subscription.SubscriptionDtos.FeatureView;
import com.framebucket.subscription.SubscriptionDtos.PlanRequestView;
import com.framebucket.subscription.SubscriptionDtos.PlanUpdateRequest;
import com.framebucket.subscription.SubscriptionDtos.PlanView;
import com.framebucket.user.auth.model.PhotographerPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** FrameBucket owner/admin API. Every route requires ROLE_ADMIN (see SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService admin;
    private final PlanCatalogService plans;
    private final AdminAuditService audit;

    public AdminController(AdminService admin, PlanCatalogService plans, AdminAuditService audit) {
        this.admin = admin;
        this.plans = plans;
        this.audit = audit;
    }

    @GetMapping("/overview")
    public Overview overview() {
        return admin.overview();
    }

    @GetMapping("/photographers")
    public Page<PhotographerRow> photographers(@RequestParam(required = false) String query,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) String plan,
                                               @RequestParam(required = false) String sort,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "25") int size) {
        return admin.photographers(query, status, plan, sort, page, size);
    }

    @GetMapping("/photographers/{id}")
    public PhotographerDetail photographer(@PathVariable UUID id) {
        return admin.photographer(id);
    }

    @PutMapping("/photographers/{id}/plan")
    public PhotographerDetail changePlan(@AuthenticationPrincipal PhotographerPrincipal principal, @PathVariable UUID id,
                                         @Valid @RequestBody ChangePlanRequest request) {
        return admin.changePlan(principal.getUsername(), id, request);
    }

    @PostMapping("/photographers/{id}/trial")
    public PhotographerDetail extendTrial(@AuthenticationPrincipal PhotographerPrincipal principal, @PathVariable UUID id,
                                          @Valid @RequestBody ExtendTrialRequest request) {
        return admin.extendTrial(principal.getUsername(), id, request.days(), request.note());
    }

    @PostMapping("/photographers/{id}/suspend")
    public PhotographerDetail suspend(@AuthenticationPrincipal PhotographerPrincipal principal, @PathVariable UUID id,
                                      @Valid @RequestBody SuspendRequest request) {
        return admin.suspend(principal.getUsername(), id, request.reason());
    }

    @PostMapping("/photographers/{id}/reactivate")
    public PhotographerDetail reactivate(@AuthenticationPrincipal PhotographerPrincipal principal, @PathVariable UUID id) {
        return admin.reactivate(principal.getUsername(), id);
    }

    @PutMapping("/photographers/{id}/notes")
    public PhotographerDetail notes(@AuthenticationPrincipal PhotographerPrincipal principal, @PathVariable UUID id,
                                    @Valid @RequestBody NotesRequest request) {
        return admin.saveNotes(principal.getUsername(), id, request.notes());
    }

    // ---- Plans ----

    @GetMapping("/plans")
    public List<PlanView> plans() {
        return plans.allPlans();
    }

    @GetMapping("/features")
    public List<FeatureView> features() {
        return Arrays.stream(Feature.values()).map(f -> new FeatureView(f.name(), f.label())).toList();
    }

    @PutMapping("/plans/{code}")
    public PlanView updatePlan(@AuthenticationPrincipal PhotographerPrincipal principal, @PathVariable String code,
                               @Valid @RequestBody PlanUpdateRequest request) {
        PlanView updated = plans.update(code, request);
        audit.log(principal.getUsername(), "UPDATE_PLAN", "PLAN", code, Map.of(
                "monthlyPrice", String.valueOf(request.monthlyPrice()),
                "yearlyPrice", String.valueOf(request.yearlyPrice()),
                "active", String.valueOf(request.active()),
                "features", String.join(",", request.features())));
        return updated;
    }

    // ---- Plan requests ----

    @GetMapping("/plan-requests")
    public List<PlanRequestView> planRequests(@RequestParam(defaultValue = "PENDING") String status) {
        return admin.planRequests(status);
    }

    @PostMapping("/plan-requests/{id}/approve")
    public PlanRequestView approve(@AuthenticationPrincipal PhotographerPrincipal principal, @PathVariable UUID id,
                                   @Valid @RequestBody ApproveRequest request) {
        return admin.approve(principal.getUsername(), id, request.months(), request.amount(), request.reference(), request.note());
    }

    @PostMapping("/plan-requests/{id}/reject")
    public PlanRequestView reject(@AuthenticationPrincipal PhotographerPrincipal principal, @PathVariable UUID id,
                                  @Valid @RequestBody RejectRequest request) {
        return admin.reject(principal.getUsername(), id, request.note());
    }

    // ---- Audit ----

    @GetMapping("/audit")
    public List<AuditEntry> audit(@RequestParam(defaultValue = "100") int limit) {
        return audit.recent(limit);
    }
}
