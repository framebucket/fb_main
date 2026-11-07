package com.framebucket.enquiry.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
public class QuotationNotificationListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(QuotationNotificationListener.class);

    private final WhatsAppTemplateSender whatsAppSender;
    private final String submittedTemplate;

    public QuotationNotificationListener(
            WhatsAppTemplateSender whatsAppSender,
            @Value("${app.whatsapp.submitted-template:framebucket_quote_submitted}") String submittedTemplate
    ) {
        this.whatsAppSender = whatsAppSender;
        this.submittedTemplate = submittedTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onQuotationSubmitted(QuotationSubmittedNotice notice) {
        try {
            whatsAppSender.sendTemplate(
                    EnquiryNotificationListener.whatsappNumber(notice.photographerWhatsAppNumber()),
                    submittedTemplate,
                    List.of(notice.photographerName(), notice.customerName(), notice.quotationPdfLink())
            );
        } catch (RuntimeException exception) {
            LOGGER.error("Could not alert the photographer about the submitted quotation.", exception);
        }
    }
}
