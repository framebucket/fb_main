package com.framebucket.customer.email;

public interface CustomerLoginOtpEmailSender {
    void sendLoginCode(String recipient, String code);
}
