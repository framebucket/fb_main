package com.framebucket.user.auth.email;

public interface VerificationEmailSender {
    void sendVerificationEmail(String recipient, String verificationUrl);
}
