package com.framebucket.user.auth.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.email.mode", havingValue = "console", matchIfMissing = true)
public class ConsoleVerificationEmailSender implements VerificationEmailSender {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleVerificationEmailSender.class);

    @Override
    public void sendVerificationEmail(String recipient, String verificationUrl) {
        LOGGER.info("Local email verification link for {}: {}", recipient, verificationUrl);
    }
}
