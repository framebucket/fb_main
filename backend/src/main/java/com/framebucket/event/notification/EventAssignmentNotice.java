package com.framebucket.event.notification;

public record EventAssignmentNotice(
        String customerWhatsAppNumber,
        String photographerName,
        String photographerNumber,
        String studioName,
        String assigneeWhatsAppNumber,
        String eventType,
        String eventLocation,
        String landmark,
        String eventTime,
        String assigneeName
) {
}
