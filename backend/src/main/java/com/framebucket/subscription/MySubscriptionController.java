package com.framebucket.subscription;

import com.framebucket.admin.AdminAccess;
import com.framebucket.enquiry.notification.EnquiryEmailSender;
import com.framebucket.subscription.SubscriptionDtos.MySubscriptionView;
import com.framebucket.subscription.SubscriptionDtos.PlanRequestCreate;
import com.framebucket.subscription.SubscriptionDtos.PlanRequestView;
import com.framebucket.subscription.SubscriptionDtos.UsageView;
import com.framebucket.user.auth.model.PhotographerPrincipal;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/** Photographer's own plan, usage and plan requests. */
@RestController
@RequestMapping("/api/subscriptions")
public class MySubscriptionController {
    private static final Logger LOGGER = LoggerFactory.getLogger(MySubscriptionController.class);

    private final EntitlementService entitlements;
    private final SubscriptionManager manager;
    private final PlanCatalogService catalog;
    private final JdbcTemplate jdbc;
    private final AdminAccess adminAccess;
    private final EnquiryEmailSender emailSender;
    private final String frontendBaseUrl;

    public MySubscriptionController(EntitlementService entitlements, SubscriptionManager manager, PlanCatalogService catalog,
                                    JdbcTemplate jdbc, AdminAccess adminAccess, EnquiryEmailSender emailSender,
                                    @Value("${app.frontend.base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.entitlements = entitlements;
        this.manager = manager;
        this.catalog = catalog;
        this.jdbc = jdbc;
        this.adminAccess = adminAccess;
        this.emailSender = emailSender;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    @GetMapping("/me")
    public MySubscriptionView mine(@AuthenticationPrincipal PhotographerPrincipal photographer) {
        UUID id = photographer.getId();
        var usage = entitlements.usage(id);
        return new MySubscriptionView(
                entitlements.forPhotographer(id),
                new UsageView(usage.customers(), usage.upcomingEvents(), usage.teamMembers(), usage.storageBytes()),
                manager.pendingRequest(id),
                manager.history(id, 10));
    }

    /** Ask for a plan. Until a payment gateway is integrated, the admin confirms payment and approves it. */
    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public PlanRequestView request(@AuthenticationPrincipal PhotographerPrincipal photographer,
                                   @Valid @RequestBody PlanRequestCreate request) {
        var plan = catalog.plan(request.planCode());
        if (!plan.active() || !plan.publicPlan() || plan.trialDays() > 0 || EntitlementService.FREE_PLAN.equals(plan.code())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Choose one of the paid plans.");
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO fb_plan_request (id, photographer_id, plan_code, billing_cycle, message)
                    VALUES (?, ?, ?, ?, ?)
                    """, id, photographer.getId(), plan.code(), request.billingCycle(),
                    request.message() == null || request.message().isBlank() ? null : request.message().trim());
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You already have a plan request waiting. Cancel it first to request a different plan.");
        }
        PlanRequestView view = manager.pendingRequest(photographer.getId());
        notifyAdmins(view);
        return view;
    }

    @DeleteMapping("/requests/pending")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal PhotographerPrincipal photographer) {
        int updated = jdbc.update("""
                UPDATE fb_plan_request SET status = 'CANCELLED', handled_at = CURRENT_TIMESTAMP, handled_by = ?
                WHERE photographer_id = ? AND status = 'PENDING'
                """, photographer.getUsername(), photographer.getId());
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No pending plan request.");
        }
    }

    private void notifyAdmins(PlanRequestView request) {
        if (request == null) return;
        String body = "New plan request\n\n"
                + "Photographer: " + request.photographerName()
                + (request.studioName() == null ? "" : " (" + request.studioName() + ")") + "\n"
                + "Email: " + request.email() + "\nMobile: " + request.mobileNumber() + "\n"
                + "Plan: " + request.planName() + " (" + request.billingCycle() + "), Rs. " + request.amount() + " + GST\n"
                + (request.message() == null ? "" : "Message: " + request.message() + "\n")
                + "\nReview it in the admin portal: " + frontendBaseUrl + "/admin";
        for (String admin : adminAccess.adminEmails()) {
            try {
                emailSender.send(admin, "FrameBucket plan request: " + request.planName(), body);
            } catch (RuntimeException exception) {
                LOGGER.warn("Could not email admin {} about a plan request", admin, exception);
            }
        }
    }
}
