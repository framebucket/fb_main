package com.framebucket.enquiry.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.email.mode", havingValue = "smtp")
public class SmtpEnquiryEmailSender implements EnquiryEmailSender {
    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEnquiryEmailSender(
            JavaMailSender mailSender,
            @Value("${app.email.from}") String fromAddress
    ) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(String recipient, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
