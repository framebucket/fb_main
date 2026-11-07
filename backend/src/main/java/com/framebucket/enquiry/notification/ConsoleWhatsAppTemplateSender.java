package com.framebucket.enquiry.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "app.whatsapp.mode", havingValue = "console", matchIfMissing = true)
public class ConsoleWhatsAppTemplateSender implements WhatsAppTemplateSender {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleWhatsAppTemplateSender.class);

    @Override
    public void sendTemplate(String recipientNumber, String templateName, List<String> bodyParameters) {
        LOGGER.info("Local WhatsApp template '{}' for {} with parameters {}", templateName, recipientNumber, bodyParameters);
    }
}
