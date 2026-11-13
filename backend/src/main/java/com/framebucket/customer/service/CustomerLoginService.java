package com.framebucket.customer.service;

import com.framebucket.customer.email.CustomerLoginOtpEmailSender;
import com.framebucket.customer.api.CustomerAuthDtos.CustomerPortalProfileResponse;
import com.framebucket.common.phone.IndianMobileNumber;
import com.framebucket.customer.model.Customer;
import com.framebucket.customer.model.CustomerLoginOtp;
import com.framebucket.customer.model.CustomerPrincipal;
import com.framebucket.customer.repository.CustomerLoginOtpRepository;
import com.framebucket.customer.repository.CustomerRepository;
import com.framebucket.user.auth.sms.MobileOtpSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

@Service
public class CustomerLoginService {
    public enum CodeRequestResult {
        SENT,
        RESEND_COOLDOWN,
        NOT_REGISTERED
    }

    private static final Duration CODE_LIFETIME = Duration.ofMinutes(10);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final CustomerRepository customerRepository;
    private final CustomerLoginOtpRepository otpRepository;
    private final CustomerLoginOtpEmailSender emailSender;
    private final MobileOtpSender mobileOtpSender;
    private final byte[] hmacSecret;

    public CustomerLoginService(
            CustomerRepository customerRepository,
            CustomerLoginOtpRepository otpRepository,
            CustomerLoginOtpEmailSender emailSender,
            MobileOtpSender mobileOtpSender,
            @Value("${app.verification.otp-hmac-secret}") String hmacSecret
    ) {
        this.customerRepository = customerRepository;
        this.otpRepository = otpRepository;
        this.emailSender = emailSender;
        this.mobileOtpSender = mobileOtpSender;
        this.hmacSecret = hmacSecret.getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public CodeRequestResult requestCode(String submittedMobileNumber) {
        String mobileNumber = normalizeMobile(submittedMobileNumber);
        var customers = customerRepository.findAllByMobileNumberOrderByCreatedAtDesc(mobileNumber);
        if (customers.isEmpty()) {
            return CodeRequestResult.NOT_REGISTERED;
        }

        Instant now = Instant.now();
        boolean recentlySent = otpRepository
                .findFirstByMobileNumberAndConsumedAtIsNullOrderByCreatedAtDesc(mobileNumber)
                .map(existing -> existing.getCreatedAt().isAfter(now.minus(RESEND_COOLDOWN)))
                .orElse(false);
        if (recentlySent) {
            return CodeRequestResult.RESEND_COOLDOWN;
        }

        otpRepository.deleteAllByMobileNumberAndConsumedAtIsNull(mobileNumber);
        String code = String.format(Locale.ROOT, "%06d", SECURE_RANDOM.nextInt(1_000_000));
        otpRepository.save(new CustomerLoginOtp(mobileNumber, hash(code), now.plus(CODE_LIFETIME)));

        Set<String> emailAddresses = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        customers.stream().map(Customer::getEmail).forEach(emailAddresses::add);
        emailAddresses.forEach(email -> emailSender.sendLoginCode(email, code));
        mobileOtpSender.sendVerificationCode(mobileNumber, code);
        return CodeRequestResult.SENT;
    }

    @Transactional
    public CustomerPrincipal verifyCode(String submittedMobileNumber, String submittedCode) {
        String mobileNumber = normalizeMobile(submittedMobileNumber);
        Instant now = Instant.now();
        CustomerLoginOtp otp = otpRepository
                .findFirstByMobileNumberAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(mobileNumber, now)
                .orElse(null);
        if (otp == null || otp.getAttemptCount() >= 5) {
            return null;
        }

        if (!MessageDigest.isEqual(
                hash(submittedCode).getBytes(StandardCharsets.UTF_8),
                otp.getCodeHash().getBytes(StandardCharsets.UTF_8)
        )) {
            otp.recordFailedAttempt();
            return null;
        }

        otp.consume(now);
        return principalFor(mobileNumber);
    }

    /** The signed-in customer for a registered mobile number (used to restore remembered app sessions). */
    @Transactional(readOnly = true)
    public CustomerPrincipal principalFor(String mobileNumber) {
        var customers = customerRepository.findAllByMobileNumberOrderByCreatedAtDesc(mobileNumber);
        if (customers.isEmpty()) {
            return null;
        }
        Customer displayCustomer = customers.get(0);
        return new CustomerPrincipal(
                displayCustomer.getName(),
                mobileNumber,
                customers.stream().map(Customer::getId).toList()
        );
    }

    public static CustomerPortalProfileResponse toProfile(CustomerPrincipal principal) {
        return new CustomerPortalProfileResponse(principal.getName(), principal.getMobileNumber());
    }

    private static String normalizeMobile(String mobileNumber) {
        return IndianMobileNumber.normalize(mobileNumber);
    }

    private String hash(String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(code.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable.", exception);
        }
    }
}
