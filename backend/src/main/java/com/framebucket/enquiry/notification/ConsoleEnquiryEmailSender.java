package com.framebucket.enquiry.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.email.mode", havingValue = "console", matchIfMissing = true)
public class ConsoleEnquiryEmailSender implements EnquiryEmailSender {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleEnquiryEmailSender.class);

    @Override
    public void send(String recipient, String subject, String body) {
        LOGGER.info("Local enquiry email to {} | {}\n{}", recipient, subject, body);
    }
}
