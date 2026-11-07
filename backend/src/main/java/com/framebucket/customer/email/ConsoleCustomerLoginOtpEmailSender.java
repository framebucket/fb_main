package com.framebucket.customer.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.email.mode", havingValue = "console", matchIfMissing = true)
public class ConsoleCustomerLoginOtpEmailSender implements CustomerLoginOtpEmailSender {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleCustomerLoginOtpEmailSender.class);

    @Override
    public void sendLoginCode(String recipient, String code) {
        LOGGER.info("Local customer sign-in code for {}: {}", recipient, code);
    }
}
