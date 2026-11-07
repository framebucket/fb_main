package com.framebucket.enquiry.notification;

public record QuotationSubmittedNotice(
        String photographerName,
        String photographerWhatsAppNumber,
        String customerName,
        String quotationPdfLink
) {
}
