package com.framebucket.event.notification;

import com.framebucket.enquiry.notification.WhatsAppTemplateSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
public class EventAssignmentNotificationListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventAssignmentNotificationListener.class);

    private final WhatsAppTemplateSender whatsAppSender;
    private final String customerTemplate;
    private final String assigneeTemplate;

    public EventAssignmentNotificationListener(
            WhatsAppTemplateSender whatsAppSender,
            @Value("${app.whatsapp.assignment-customer-template:framebucket_event_assignment_customer}") String customerTemplate,
            @Value("${app.whatsapp.assignment-assignee-template:framebucket_event_assignment_assignee}") String assigneeTemplate
    ) {
        this.whatsAppSender = whatsAppSender;
        this.customerTemplate = customerTemplate;
        this.assigneeTemplate = assigneeTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendAssignmentMessages(EventAssignmentNotice notice) {
        try {
            whatsAppSender.sendTemplate(
                    notice.customerWhatsAppNumber(),
                    customerTemplate,
                    List.of(notice.photographerName(), notice.photographerNumber(), notice.studioName())
            );
        } catch (RuntimeException exception) {
            LOGGER.error("Could not send the event assignment WhatsApp to the customer.", exception);
        }

        try {
            whatsAppSender.sendTemplate(
                    notice.assigneeWhatsAppNumber(),
                    assigneeTemplate,
                    List.of(notice.eventType(), notice.eventLocation(), notice.landmark(), notice.eventTime(), notice.studioName())
            );
        } catch (RuntimeException exception) {
            LOGGER.error("Could not send the event assignment WhatsApp to {}.", notice.assigneeName(), exception);
        }
    }
}
