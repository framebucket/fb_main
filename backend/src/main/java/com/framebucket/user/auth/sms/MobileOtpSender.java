package com.framebucket.user.auth.sms;

public interface MobileOtpSender {
    void sendVerificationCode(String mobileNumber, String code);
}
