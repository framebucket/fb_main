package com.framebucket.event.notification;

import com.framebucket.enquiry.notification.WhatsAppTemplateSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Sends a WhatsApp alert every time a customer updates the event location or time.
 *
 * <p>With {@code WHATSAPP_EVENT_UPDATE_TEMPLATE} set (see WHATSAPP_TEMPLATES.md), the dedicated update template
 * goes to the assigned photographer, the event crew and the studio owner. Until that template is approved, the
 * existing assignee template is re-sent to the assigned photographer and crew so they still get the new details.</p>
 */
@Component
public class EventDetailsUpdateNotificationListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventDetailsUpdateNotificationListener.class);

    private final WhatsAppTemplateSender whatsAppSender;
    private final String updateTemplate;
    private final String updateTemplateLanguage;
    private final String assigneeTemplate;
    private final String assigneeTemplateLanguage;

    public EventDetailsUpdateNotificationListener(
            WhatsAppTemplateSender whatsAppSender,
            @Value("${app.whatsapp.event-update-template:}") String updateTemplate,
            @Value("${app.whatsapp.event-update-language:${app.whatsapp.language:en}}") String updateTemplateLanguage,
            @Value("${app.whatsapp.assignment-assignee-template:framebucket_event_assignment_assignee}") String assigneeTemplate,
            @Value("${app.whatsapp.assignment-assignee-language:${app.whatsapp.language:en}}") String assigneeTemplateLanguage
    ) {
        this.whatsAppSender = whatsAppSender;
        this.updateTemplate = updateTemplate == null ? "" : updateTemplate.trim();
        this.updateTemplateLanguage = updateTemplateLanguage;
        this.assigneeTemplate = assigneeTemplate;
        this.assigneeTemplateLanguage = assigneeTemplateLanguage;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendUpdateMessages(EventDetailsUpdatedNotice notice) {
        if (!updateTemplate.isEmpty()) {
            List<String> parameters = List.of(
                    notice.studioName(), notice.customerName(), notice.eventType(), notice.eventDate(),
                    notice.eventLocation(), notice.landmark(), notice.eventTime());
            for (EventDetailsUpdatedNotice.Recipient recipient : notice.crewRecipients()) {
                send(recipient, updateTemplate, updateTemplateLanguage, parameters);
            }
            if (notice.studioOwner() != null) {
                send(notice.studioOwner(), updateTemplate, updateTemplateLanguage, parameters);
            }
            return;
        }
        // Fallback until the dedicated template is approved: the assignee template carries the same details.
        List<String> parameters = List.of(
                notice.eventType(), notice.eventLocation(), notice.landmark(),
                notice.eventDate() + ", " + notice.eventTime(), notice.studioName());
        for (EventDetailsUpdatedNotice.Recipient recipient : notice.crewRecipients()) {
            send(recipient, assigneeTemplate, assigneeTemplateLanguage, parameters);
        }
    }

    private void send(EventDetailsUpdatedNotice.Recipient recipient, String template, String language, List<String> parameters) {
        try {
            whatsAppSender.sendTemplate(recipient.whatsAppNumber(), template, language, parameters);
        } catch (RuntimeException exception) {
            LOGGER.error("Could not send the event update WhatsApp to {}.", recipient.name(), exception);
        }
    }
}
