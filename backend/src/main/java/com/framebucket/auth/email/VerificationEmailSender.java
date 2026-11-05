package com.framebucket.auth.email;

public interface VerificationEmailSender {
    void sendVerificationEmail(String recipient, String verificationUrl);
}
