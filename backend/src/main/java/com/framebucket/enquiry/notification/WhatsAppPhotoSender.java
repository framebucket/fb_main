package com.framebucket.enquiry.notification;

import java.util.List;

public interface WhatsAppPhotoSender {
    void sendPhotoTemplate(String recipientNumber, String templateName, String languageCode,
                           byte[] jpegBytes, String filename, List<String> bodyParameters);
}
