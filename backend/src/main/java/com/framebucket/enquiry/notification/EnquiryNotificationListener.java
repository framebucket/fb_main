package com.framebucket.enquiry.notification;

import com.framebucket.common.phone.IndianMobileNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
public class EnquiryNotificationListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnquiryNotificationListener.class);

    private final EnquiryEmailSender emailSender;
    private final WhatsAppTemplateSender whatsAppSender;
    private final String enquiryTemplate;

    public EnquiryNotificationListener(
            EnquiryEmailSender emailSender,
            WhatsAppTemplateSender whatsAppSender,
            @Value("${app.whatsapp.enquiry-template:framebucket_enquiry_quote_link}") String enquiryTemplate
    ) {
        this.emailSender = emailSender;
        this.whatsAppSender = whatsAppSender;
        this.enquiryTemplate = enquiryTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEnquiryCreated(EnquiryCreatedNotice notice) {
        String body = "Hi " + notice.customerName() + ",\n\n"
                + notice.studioName() + " received your photography enquiry. Use this secure link to build your quotation:\n\n"
                + notice.quotationLink() + "\n\nWe look forward to helping with your celebration.";
        try {
            emailSender.send(notice.customerEmail(), "Your photography quotation link", body);
        } catch (RuntimeException exception) {
            LOGGER.error("Could not email the quotation link for a new enquiry.", exception);
        }
        try {
            whatsAppSender.sendTemplate(
                    whatsappNumber(notice.customerWhatsAppNumber()),
                    enquiryTemplate,
                    List.of(notice.customerName(), notice.studioName(), notice.quotationLink())
            );
        } catch (RuntimeException exception) {
            LOGGER.error("Could not send a WhatsApp quotation link for a new enquiry.", exception);
        }
    }

    static String whatsappNumber(String mobileNumber) {
        return "91" + IndianMobileNumber.normalize(mobileNumber);
    }
}
