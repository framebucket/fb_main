package com.framebucket.bookings;

import com.framebucket.bookings.BookingDtos.CatalogResponse;
import com.framebucket.bookings.BookingDtos.CatalogSettingsRequest;
import com.framebucket.bookings.BookingDtos.ConfirmAdvanceRequest;
import com.framebucket.bookings.BookingDtos.ContractDraft;
import com.framebucket.bookings.BookingDtos.ContractSendResponse;
import com.framebucket.bookings.BookingDtos.ContractSummary;
import com.framebucket.bookings.BookingDtos.EnquiryTracking;
import com.framebucket.bookings.BookingDtos.ItemRequest;
import com.framebucket.bookings.BookingDtos.LeadCaptureResponse;
import com.framebucket.bookings.BookingDtos.PackageRequest;
import com.framebucket.bookings.BookingDtos.SendContractRequest;
import com.framebucket.user.auth.model.PhotographerPrincipal;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/studio")
public class BookingController {
    private final CatalogAdminService catalog;
    private final ContractService contracts;
    private final FollowUpService followUps;
    private final LeadService leads;

    public BookingController(CatalogAdminService catalog, ContractService contracts, FollowUpService followUps, LeadService leads) {
        this.catalog = catalog;
        this.contracts = contracts;
        this.followUps = followUps;
        this.leads = leads;
    }

    @GetMapping("/quote-catalog")
    public CatalogResponse catalog(@AuthenticationPrincipal PhotographerPrincipal p) {
        return catalog.catalog(p.getId());
    }

    @PutMapping("/quote-catalog/settings")
    public CatalogResponse settings(@AuthenticationPrincipal PhotographerPrincipal p, @Valid @RequestBody CatalogSettingsRequest request) {
        return catalog.saveSettings(p.getId(), request);
    }

    @PostMapping("/quote-catalog/items")
    public CatalogResponse addItem(@AuthenticationPrincipal PhotographerPrincipal p, @Valid @RequestBody ItemRequest request) {
        return catalog.saveItem(p.getId(), null, request);
    }

    @PutMapping("/quote-catalog/items/{itemId}")
    public CatalogResponse updateItem(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID itemId,
                                      @Valid @RequestBody ItemRequest request) {
        return catalog.saveItem(p.getId(), itemId, request);
    }

    @DeleteMapping("/quote-catalog/items/{itemId}")
    public CatalogResponse removeItem(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID itemId) {
        return catalog.deactivateItem(p.getId(), itemId);
    }

    @PostMapping("/quote-catalog/packages")
    public CatalogResponse addPackage(@AuthenticationPrincipal PhotographerPrincipal p, @Valid @RequestBody PackageRequest request) {
        return catalog.savePackage(p.getId(), null, request);
    }

    @PutMapping("/quote-catalog/packages/{packageId}")
    public CatalogResponse updatePackage(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID packageId,
                                         @Valid @RequestBody PackageRequest request) {
        return catalog.savePackage(p.getId(), packageId, request);
    }

    @DeleteMapping("/quote-catalog/packages/{packageId}")
    public CatalogResponse deletePackage(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID packageId) {
        return catalog.deletePackage(p.getId(), packageId);
    }

    @GetMapping("/lead-capture")
    public LeadCaptureResponse leadCapture(@AuthenticationPrincipal PhotographerPrincipal p) {
        return leads.links(p.getId());
    }

    @GetMapping("/enquiries/tracking")
    public List<EnquiryTracking> tracking(@AuthenticationPrincipal PhotographerPrincipal p) {
        return followUps.tracking(p.getId());
    }

    @PostMapping("/enquiries/{enquiryId}/remind")
    public EnquiryTracking remind(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID enquiryId) {
        return followUps.remindNow(p.getId(), enquiryId);
    }

    @GetMapping("/enquiries/{enquiryId}/contract")
    public ContractDraft contractDraft(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID enquiryId) {
        return contracts.draft(p.getId(), enquiryId);
    }

    @PostMapping("/enquiries/{enquiryId}/contract")
    public ContractSendResponse sendContract(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID enquiryId,
                                             @Valid @RequestBody SendContractRequest request) {
        return contracts.send(p.getId(), enquiryId, request);
    }

    @DeleteMapping("/enquiries/{enquiryId}/contract")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelContract(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID enquiryId) {
        contracts.cancel(p.getId(), enquiryId);
    }

    @PostMapping("/enquiries/{enquiryId}/contract/advance")
    public ContractSummary confirmAdvance(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID enquiryId,
                                          @Valid @RequestBody(required = false) ConfirmAdvanceRequest request) {
        return contracts.confirmAdvance(p.getId(), enquiryId, request);
    }

    @GetMapping(value = "/enquiries/{enquiryId}/contract.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> contractPdf(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID enquiryId) {
        return pdf(contracts.pdfForPhotographer(p.getId(), enquiryId));
    }

    static ResponseEntity<byte[]> pdf(byte[] body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=booking-agreement.pdf")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(body);
    }
}
