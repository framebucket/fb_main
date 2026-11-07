package com.framebucket.customer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fb_customer_login_otp")
public class CustomerLoginOtp {
    @Id
    private UUID id;

    @Column(name = "mobile_number", nullable = false, length = 16)
    private String mobileNumber;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CustomerLoginOtp() {
    }

    public CustomerLoginOtp(String mobileNumber, String codeHash, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.mobileNumber = mobileNumber;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void setCreatedAtIfMissing() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void recordFailedAttempt() {
        if (attemptCount < 5) {
            attemptCount++;
        }
    }

    public void consume(Instant at) {
        consumedAt = at;
    }
}
