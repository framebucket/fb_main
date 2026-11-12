package com.framebucket.studio;

import com.framebucket.customer.model.CustomerPrincipal;
import com.framebucket.studio.StudioDtos.CustomerBillingResponse;
import com.framebucket.studio.StudioDtos.PaymentPurpose;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Customer portal: what is paid / due, receipts, UPI payment QR codes and shared e-albums. */
@RestController
@RequestMapping("/api/customer-portal/events/{eventId}")
public class CustomerStudioController {
    private final BillingService billing;

    public CustomerStudioController(BillingService billing) {
        this.billing = billing;
    }

    @GetMapping("/billing")
    public CustomerBillingResponse billing(@PathVariable UUID eventId, @AuthenticationPrincipal CustomerPrincipal customer) {
        return billing.customerBilling(eventId, customer.getCustomerIds());
    }

    @GetMapping(value = "/payments/{paymentId}/receipt.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> receipt(@PathVariable UUID eventId, @PathVariable UUID paymentId,
                                          @AuthenticationPrincipal CustomerPrincipal customer) {
        return StudioController.pdf(billing.receiptForCustomer(eventId, paymentId, customer.getCustomerIds()), paymentId);
    }

    @GetMapping(value = "/payment-qr.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> paymentQr(@PathVariable UUID eventId,
                                            @RequestParam(defaultValue = "BALANCE") PaymentPurpose purpose,
                                            @AuthenticationPrincipal CustomerPrincipal customer) {
        return StudioController.png(billing.paymentQrForCustomer(eventId, customer.getCustomerIds(), purpose));
    }
}
