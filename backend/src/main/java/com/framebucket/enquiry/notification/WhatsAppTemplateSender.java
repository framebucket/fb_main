package com.framebucket.enquiry.notification;

import java.util.List;

public interface WhatsAppTemplateSender {
    void sendTemplate(String recipientNumber, String templateName, String languageCode, List<String> bodyParameters);
}
