package com.framebucket.enquiry.notification;

public record EnquiryCreatedNotice(
        String customerName,
        String customerEmail,
        String customerWhatsAppNumber,
        String studioName,
        String quotationLink
) {
}
