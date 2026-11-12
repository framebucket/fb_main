package com.framebucket.event.notification;

import java.util.List;

/**
 * Published after a customer changes the event location or time from the customer portal.
 * Every recipient (assigned photographer, crew and, with the dedicated template, the studio owner)
 * receives a WhatsApp alert with the latest details.
 */
public record EventDetailsUpdatedNotice(
        List<Recipient> crewRecipients,
        Recipient studioOwner,
        String studioName,
        String customerName,
        String eventType,
        String eventDate,
        String eventLocation,
        String landmark,
        String eventTime
) {
    public record Recipient(String name, String whatsAppNumber) {
    }
}
