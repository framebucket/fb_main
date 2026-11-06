package com.framebucket.user.auth.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.sms.mode", havingValue = "console", matchIfMissing = true)
public class ConsoleMobileOtpSender implements MobileOtpSender {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleMobileOtpSender.class);

    @Override
    public void sendVerificationCode(String mobileNumber, String code) {
        LOGGER.info("Local mobile verification code for {}: {}", mobileNumber, code);
    }
}
