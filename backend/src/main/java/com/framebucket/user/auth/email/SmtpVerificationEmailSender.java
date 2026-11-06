package com.framebucket.user.auth.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.email.mode", havingValue = "smtp")
public class SmtpVerificationEmailSender implements VerificationEmailSender {
    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpVerificationEmailSender(
            JavaMailSender mailSender,
            @Value("${app.email.from}") String fromAddress
    ) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void sendVerificationEmail(String recipient, String verificationUrl) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(recipient);
        message.setSubject("Verify your FrameBucket email");
        message.setText("Welcome to FrameBucket. Confirm your email using this link (valid for 24 hours):\n\n"
                + verificationUrl + "\n\nIf you did not create this account, you can ignore this email.");
        mailSender.send(message);
    }
}
