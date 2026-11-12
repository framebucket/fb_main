package com.framebucket.studio;

import com.framebucket.studio.StudioDtos.DownloadPolicyRequest;
import com.framebucket.studio.StudioDtos.DownloadPolicyResponse;
import com.framebucket.studio.StudioDtos.EventBillingResponse;
import com.framebucket.studio.StudioDtos.ExpenseRequest;
import com.framebucket.studio.StudioDtos.ExpenseResponse;
import com.framebucket.studio.StudioDtos.FinanceSummaryResponse;
import com.framebucket.studio.StudioDtos.FlipbookRequest;
import com.framebucket.studio.StudioDtos.FlipbookResponse;
import com.framebucket.studio.StudioDtos.PaymentCreateRequest;
import com.framebucket.studio.StudioDtos.PaymentResponse;
import com.framebucket.studio.StudioDtos.PortfolioItemRequest;
import com.framebucket.studio.StudioDtos.PortfolioItemResponse;
import com.framebucket.studio.StudioDtos.PortfolioItemUpdateRequest;
import com.framebucket.studio.StudioDtos.StudioProfileRequest;
import com.framebucket.studio.StudioDtos.StudioProfileResponse;
import com.framebucket.user.auth.model.PhotographerPrincipal;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Photographer endpoints for studio settings, billing, expenses, portfolio and e-album flipbooks. */
@RestController
public class StudioController {
    private final StudioProfileService profiles;
    private final BillingService billing;
    private final PortfolioService portfolio;
    private final FlipbookService flipbooks;

    public StudioController(StudioProfileService profiles, BillingService billing, PortfolioService portfolio,
                            FlipbookService flipbooks) {
        this.profiles = profiles;
        this.billing = billing;
        this.portfolio = portfolio;
        this.flipbooks = flipbooks;
    }

    // ---- Studio settings ----

    @GetMapping("/api/studio/profile")
    public StudioProfileResponse profile(@AuthenticationPrincipal PhotographerPrincipal photographer) {
        return profiles.response(photographer.getId());
    }

    @PutMapping("/api/studio/profile")
    public StudioProfileResponse updateProfile(@AuthenticationPrincipal PhotographerPrincipal photographer,
                                               @Valid @RequestBody StudioProfileRequest request) {
        return profiles.update(photographer.getId(), request);
    }

    // ---- Billing, payments and receipts ----

    @GetMapping("/api/studio/finance/summary")
    public FinanceSummaryResponse financeSummary(@AuthenticationPrincipal PhotographerPrincipal photographer) {
        return billing.financeSummary(photographer.getId());
    }

    @GetMapping("/api/events/{eventId}/billing")
    public EventBillingResponse eventBilling(@AuthenticationPrincipal PhotographerPrincipal photographer,
                                             @PathVariable UUID eventId) {
        return billing.eventBilling(photographer.getId(), eventId);
    }

    @PostMapping("/api/events/{eventId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse recordPayment(@AuthenticationPrincipal PhotographerPrincipal photographer,
                                         @PathVariable UUID eventId,
                                         @Valid @RequestBody PaymentCreateRequest request) {
        return billing.recordPayment(photographer.getId(), eventId, request);
    }

    @DeleteMapping("/api/events/{eventId}/payments/{paymentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePayment(@AuthenticationPrincipal PhotographerPrincipal photographer,
                              @PathVariable UUID eventId, @PathVariable UUID paymentId) {
        billing.deletePayment(photographer.getId(), eventId, paymentId);
    }

    @GetMapping(value = "/api/events/{eventId}/payments/{paymentId}/receipt.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> receipt(@AuthenticationPrincipal PhotographerPrincipal photographer,
                                          @PathVariable UUID eventId, @PathVariable UUID paymentId) {
        return pdf(billing.receiptForPhotographer(photographer.getId(), eventId, paymentId), paymentId);
    }

    @GetMapping(value = "/api/events/{eventId}/payment-qr.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> paymentQr(@AuthenticationPrincipal PhotographerPrincipal photographer,
                                            @PathVariable UUID eventId,
                                            @RequestParam(required = false) BigDecimal amount) {
        return png(billing.paymentQrForPhotographer(photographer.getId(), eventId, amount));
    }

    @PutMapping("/api/events/{eventId}/download-policy")
    public DownloadPolicyResponse updateDownloadPolicy(@AuthenticationPrincipal PhotographerPrincipal photographer,
                                                       @PathVariable UUID eventId,
                                                       @Valid @RequestBody DownloadPolicyRequest request) {
        return billing.updateDownloadPolicy(photographer.getId(), eventId, request);
    }

    // ---- Expenses ----

    @GetMapping("/api/studio/expenses")
    public List<ExpenseResponse> expenses(@AuthenticationPrincipal PhotographerPrincipal photographer,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return billing.expenses(photographer.getId(), from, to);
    }

    @PostMapping("/api/studio/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse createExpense(@AuthenticationPrincipal PhotographerPrincipal photographer,
                                         @Valid @RequestBody ExpenseRequest request) {
        return billing.saveExpense(photographer.getId(), null, request);
    }

    @PutMapping("/api/studio/expenses/{expenseId}")
    public ExpenseResponse updateExpense(@AuthenticationPrincipal PhotographerPrincipal photographer,
                                         @PathVariable UUID expenseId, @Valid @RequestBody ExpenseRequest request) {
        return billing.saveExpense(photographer.getId(), expenseId, request);
    }

    @DeleteMapping("/api/studio/expenses/{expenseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExpense(@AuthenticationPrincipal PhotographerPrincipal photographer, @PathVariable UUID expenseId) {
        billing.deleteExpense(photographer.getId(), expenseId);
    }

    // ---- Portfolio ----

    @GetMapping("/api/studio/portfolio")
    public List<PortfolioItemResponse> portfolioItems(@AuthenticationPrincipal PhotographerPrincipal photographer) {
        return portfolio.items(photographer.getId());
    }

    @PostMapping("/api/studio/portfolio")
    public List<PortfolioItemResponse> addPortfolioItems(@AuthenticationPrincipal PhotographerPrincipal photographer,
                                                         @Valid @RequestBody PortfolioItemRequest request) {
        requireDataModule(photographer);
        return portfolio.add(photographer.getId(), request);
    }

    @PutMapping("/api/studio/portfolio/{itemId}")
    public List<PortfolioItemResponse> updatePortfolioItem(@AuthenticationPrincipal PhotographerPrincipal photographer,
                                                           @PathVariable UUID itemId,
                                                           @Valid @RequestBody PortfolioItemUpdateRequest request) {
        return portfolio.update(photographer.getId(), itemId, request);
    }

    @DeleteMapping("/api/studio/portfolio/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removePortfolioItem(@AuthenticationPrincipal PhotographerPrincipal photographer, @PathVariable UUID itemId) {
        portfolio.remove(photographer.getId(), itemId);
    }

    // ---- E-album flipbooks ----

    @GetMapping("/api/events/{eventId}/flipbooks")
    public List<FlipbookResponse> flipbooks(@AuthenticationPrincipal PhotographerPrincipal photographer,
                                            @PathVariable UUID eventId) {
        return flipbooks.list(photographer.getId(), eventId);
    }

    @GetMapping("/api/events/{eventId}/flipbooks/customer-selection")
    public List<String> customerSelection(@AuthenticationPrincipal PhotographerPrincipal photographer,
                                          @PathVariable UUID eventId) {
        requireDataModule(photographer);
        return flipbooks.customerSelection(photographer.getId(), eventId);
    }

    @PostMapping("/api/events/{eventId}/flipbooks")
    @ResponseStatus(HttpStatus.CREATED)
    public FlipbookResponse createFlipbook(@AuthenticationPrincipal PhotographerPrincipal photographer,
                                           @PathVariable UUID eventId, @Valid @RequestBody FlipbookRequest request) {
        requireDataModule(photographer);
        return flipbooks.create(photographer.getId(), eventId, request);
    }

    @PutMapping("/api/events/{eventId}/flipbooks/{flipbookId}")
    public FlipbookResponse updateFlipbook(@AuthenticationPrincipal PhotographerPrincipal photographer,
                                           @PathVariable UUID eventId, @PathVariable UUID flipbookId,
                                           @Valid @RequestBody FlipbookRequest request) {
        requireDataModule(photographer);
        return flipbooks.update(photographer.getId(), eventId, flipbookId, request);
    }

    @DeleteMapping("/api/events/{eventId}/flipbooks/{flipbookId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFlipbook(@AuthenticationPrincipal PhotographerPrincipal photographer,
                               @PathVariable UUID eventId, @PathVariable UUID flipbookId) {
        flipbooks.delete(photographer.getId(), eventId, flipbookId);
    }

    private static void requireDataModule(PhotographerPrincipal photographer) {
        if (!photographer.isDataModuleEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The Data module is not included with your current plan.");
        }
    }

    static ResponseEntity<byte[]> pdf(byte[] body, UUID paymentId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"FrameBucket-receipt-" + paymentId + ".pdf\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(MediaType.APPLICATION_PDF)
                .body(body);
    }

    static ResponseEntity<byte[]> png(byte[] body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(MediaType.IMAGE_PNG)
                .body(body);
    }
}
