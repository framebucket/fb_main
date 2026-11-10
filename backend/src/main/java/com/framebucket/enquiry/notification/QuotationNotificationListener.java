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
    private final String submittedTemplateLanguage;

    public QuotationNotificationListener(
            WhatsAppTemplateSender whatsAppSender,
            @Value("${app.whatsapp.submitted-template:framebucket_quote_submitted}") String submittedTemplate,
            @Value("${app.whatsapp.submitted-language:${app.whatsapp.language:en}}") String submittedTemplateLanguage
    ) {
        this.whatsAppSender = whatsAppSender;
        this.submittedTemplate = submittedTemplate;
        this.submittedTemplateLanguage = submittedTemplateLanguage;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onQuotationSubmitted(QuotationSubmittedNotice notice) {
        try {
            whatsAppSender.sendTemplate(
                    EnquiryNotificationListener.whatsappNumber(notice.photographerWhatsAppNumber()),
                    submittedTemplate,
                    submittedTemplateLanguage,
                    List.of(notice.photographerName(), notice.customerName(), notice.quotationPdfLink())
            );
        } catch (RuntimeException exception) {
            LOGGER.error("Could not alert the photographer about the submitted quotation.", exception);
        }
    }
}
