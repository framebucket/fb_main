package com.framebucket.enquiry.api;

import com.framebucket.enquiry.api.EnquiryDtos.PublicQuoteContext;
import com.framebucket.enquiry.api.EnquiryDtos.QuoteCalculationResponse;
import com.framebucket.enquiry.api.EnquiryDtos.QuoteSubmissionRequest;
import com.framebucket.enquiry.api.EnquiryDtos.QuoteSubmissionResponse;
import com.framebucket.enquiry.service.EnquiryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/enquiries/quote/{token}")
public class PublicQuoteController {
    private final EnquiryService enquiryService;

    public PublicQuoteController(EnquiryService enquiryService) {
        this.enquiryService = enquiryService;
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.framebucket.bookings.BookingHooks bookingHooks;

    @GetMapping
    public PublicQuoteContext context(@PathVariable String token) {
        PublicQuoteContext context = enquiryService.publicQuoteContext(token);
        bookingHooks.markOpened(token);
        return context;
    }

    @PostMapping("/calculate")
    public QuoteCalculationResponse calculate(
            @PathVariable String token,
            @Valid @RequestBody QuoteSubmissionRequest request
    ) {
        return enquiryService.calculate(token, request);
    }

    @PostMapping("/submit")
    @ResponseStatus(HttpStatus.CREATED)
    public QuoteSubmissionResponse submit(
            @PathVariable String token,
            @Valid @RequestBody QuoteSubmissionRequest request
    ) {
        return enquiryService.submitQuotation(token, request);
    }

    @GetMapping("/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable String token) {
        byte[] pdf = enquiryService.publicQuotationPdf(token);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=quotation.pdf")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(pdf);
    }
}
