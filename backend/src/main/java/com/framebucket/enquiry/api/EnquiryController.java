package com.framebucket.enquiry.api;

import com.framebucket.enquiry.api.EnquiryDtos.ConvertEnquiryRequest;
import com.framebucket.enquiry.api.EnquiryDtos.CreateEventFromEnquiryRequest;
import com.framebucket.enquiry.api.EnquiryDtos.CreateEventFromEnquiryResponse;
import com.framebucket.enquiry.api.EnquiryDtos.CreateEnquiryRequest;
import com.framebucket.enquiry.api.EnquiryDtos.CustomerConversionResponse;
import com.framebucket.enquiry.api.EnquiryDtos.EnquiryEventSetupResponse;
import com.framebucket.enquiry.api.EnquiryDtos.EnquiryResponse;
import com.framebucket.enquiry.api.EnquiryDtos.FinalizedQuoteRequest;
import com.framebucket.enquiry.api.EnquiryDtos.FinalizedQuoteResponse;
import com.framebucket.enquiry.service.EnquiryService;
import com.framebucket.user.auth.model.PhotographerPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/api/enquiries")
public class EnquiryController {
    private final EnquiryService enquiryService;

    public EnquiryController(EnquiryService enquiryService) {
        this.enquiryService = enquiryService;
    }

    @GetMapping
    public List<EnquiryResponse> list(@AuthenticationPrincipal PhotographerPrincipal photographer) {
        return enquiryService.listForPhotographer(photographer);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EnquiryResponse create(
            @AuthenticationPrincipal PhotographerPrincipal photographer,
            @Valid @RequestBody CreateEnquiryRequest request
    ) {
        return enquiryService.create(photographer, request);
    }

    @PostMapping("/{enquiryId}/convert")
    public CustomerConversionResponse convert(
            @AuthenticationPrincipal PhotographerPrincipal photographer,
            @PathVariable UUID enquiryId,
            @Valid @RequestBody ConvertEnquiryRequest request
    ) {
        return enquiryService.convertToCustomer(photographer, enquiryId, request);
    }

    @PutMapping("/{enquiryId}/finalized-quote")
    public FinalizedQuoteResponse saveFinalizedQuote(
            @AuthenticationPrincipal PhotographerPrincipal photographer,
            @PathVariable UUID enquiryId,
            @Valid @RequestBody FinalizedQuoteRequest request
    ) {
        return enquiryService.saveFinalizedQuote(photographer, enquiryId, request);
    }

    @GetMapping("/{enquiryId}/event-setup")
    public EnquiryEventSetupResponse eventSetup(
            @AuthenticationPrincipal PhotographerPrincipal photographer,
            @PathVariable UUID enquiryId
    ) {
        return enquiryService.eventSetup(photographer, enquiryId);
    }

    @PostMapping("/{enquiryId}/create-event")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateEventFromEnquiryResponse createEventFromEnquiry(
            @AuthenticationPrincipal PhotographerPrincipal photographer,
            @PathVariable UUID enquiryId,
            @Valid @RequestBody CreateEventFromEnquiryRequest request
    ) {
        return enquiryService.createEventFromEnquiry(photographer, enquiryId, request);
    }

    @GetMapping("/{enquiryId}/quotation.pdf")
    public ResponseEntity<byte[]> quotationPdf(
            @AuthenticationPrincipal PhotographerPrincipal photographer,
            @PathVariable UUID enquiryId
    ) {
        byte[] pdf = enquiryService.photographerQuotationPdf(photographer, enquiryId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=quotation.pdf")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(pdf);
    }

    @GetMapping("/{enquiryId}/finalized-quotation.pdf")
    public ResponseEntity<byte[]> finalizedQuotationPdf(
            @AuthenticationPrincipal PhotographerPrincipal photographer,
            @PathVariable UUID enquiryId
    ) {
        byte[] pdf = enquiryService.photographerFinalizedQuotationPdf(photographer, enquiryId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=finalized-quotation.pdf")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(pdf);
    }
}
