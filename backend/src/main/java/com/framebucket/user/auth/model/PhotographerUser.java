package com.framebucket.user.auth.model;

import com.framebucket.user.subscription.model.SubscriptionPlan;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "fb_user")
public class PhotographerUser {
    @Id
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "studio_name", length = 160)
    private String studioName;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "mobile_number", length = 16)
    private String mobileNumber;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "mobile_verified", nullable = false)
    private boolean mobileVerified;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "subscription_id")
    private SubscriptionPlan subscription;

    protected PhotographerUser() {
    }

    public PhotographerUser(String fullName, String studioName, String email, String mobileNumber, String passwordHash) {
        this.id = UUID.randomUUID();
        this.fullName = fullName.trim();
        this.studioName = normalizeOptional(studioName);
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.mobileNumber = mobileNumber.trim();
        this.passwordHash = passwordHash;
        this.active = true;
        this.emailVerified = false;
        this.mobileVerified = false;
    }

    @PrePersist
    void setCreatedAtIfMissing() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getStudioName() {
        return studioName;
    }

    public String getEmail() {
        return email;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public boolean isMobileVerified() {
        return mobileVerified;
    }

    public void verifyEmail() {
        this.emailVerified = true;
    }

    public void verifyMobileNumber() {
        this.mobileVerified = true;
    }

    public void startFreeTrial(SubscriptionPlan plan) {
        if (plan.getTrialDays() <= 0) {
            throw new IllegalArgumentException("The selected subscription is not a trial plan.");
        }
        this.subscription = plan;
        this.trialEndsAt = Instant.now().plus(Duration.ofDays(plan.getTrialDays()));
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public SubscriptionPlan getSubscription() {
        return subscription;
    }

    public Instant getTrialEndsAt() {
        return trialEndsAt;
    }
}
