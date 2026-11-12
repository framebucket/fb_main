package com.framebucket.bookings;

import com.framebucket.bookings.BookingDtos.AdvanceReportRequest;
import com.framebucket.bookings.BookingDtos.PublicContractView;
import com.framebucket.bookings.BookingDtos.PublicEnquiryForm;
import com.framebucket.bookings.BookingDtos.PublicEnquiryRequest;
import com.framebucket.bookings.BookingDtos.PublicEnquiryResponse;
import com.framebucket.bookings.BookingDtos.SignContractRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PublicBookingController {
    private final LeadService leads;
    private final ContractService contracts;

    public PublicBookingController(LeadService leads, ContractService contracts) {
        this.leads = leads;
        this.contracts = contracts;
    }

    @GetMapping("/api/public/studios/{slug}/enquiry-form")
    public PublicEnquiryForm form(@PathVariable String slug) {
        return leads.form(slug);
    }

    @PostMapping("/api/public/studios/{slug}/enquiries")
    @ResponseStatus(HttpStatus.CREATED)
    public PublicEnquiryResponse submit(@PathVariable String slug, @Valid @RequestBody PublicEnquiryRequest request,
                                        HttpServletRequest http) {
        return leads.submit(slug, request, clientIp(http));
    }

    @PostMapping("/api/public/enquiries/quote/{token}/contract/sign")
    public PublicContractView sign(@PathVariable String token, @Valid @RequestBody SignContractRequest request,
                                   HttpServletRequest http) {
        return contracts.sign(token, request, clientIp(http), http.getHeader(HttpHeaders.USER_AGENT));
    }

    @PostMapping("/api/public/enquiries/quote/{token}/contract/advance")
    public PublicContractView reportAdvance(@PathVariable String token, @Valid @RequestBody AdvanceReportRequest request) {
        return contracts.reportAdvance(token, request);
    }

    @GetMapping(value = "/api/public/enquiries/quote/{token}/contract/advance-qr.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> advanceQr(@PathVariable String token) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(contracts.advanceQr(token));
    }

    @GetMapping(value = "/api/public/enquiries/quote/{token}/contract.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable String token) {
        return BookingController.pdf(contracts.pdfForClient(token));
    }

    static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
