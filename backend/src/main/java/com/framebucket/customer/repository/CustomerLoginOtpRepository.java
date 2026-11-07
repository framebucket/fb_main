package com.framebucket.customer.repository;

import com.framebucket.customer.model.CustomerLoginOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CustomerLoginOtpRepository extends JpaRepository<CustomerLoginOtp, UUID> {
    void deleteAllByMobileNumberAndConsumedAtIsNull(String mobileNumber);

    Optional<CustomerLoginOtp> findFirstByMobileNumberAndConsumedAtIsNullOrderByCreatedAtDesc(String mobileNumber);

    Optional<CustomerLoginOtp> findFirstByMobileNumberAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            String mobileNumber,
            Instant now
    );
}
