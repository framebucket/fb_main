package com.framebucket.customer.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.email.mode", havingValue = "smtp")
public class SmtpCustomerLoginOtpEmailSender implements CustomerLoginOtpEmailSender {
    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpCustomerLoginOtpEmailSender(
            JavaMailSender mailSender,
            @Value("${app.email.from}") String fromAddress
    ) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void sendLoginCode(String recipient, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(recipient);
        message.setSubject("Your FrameBucket customer sign-in code");
        message.setText("Your FrameBucket sign-in code is " + code
                + ". It expires in 10 minutes. If you did not request this code, you can ignore this email.");
        mailSender.send(message);
    }
}
