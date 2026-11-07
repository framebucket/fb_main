package com.framebucket.enquiry.notification;

public interface EnquiryEmailSender {
    void send(String recipient, String subject, String body);
}
