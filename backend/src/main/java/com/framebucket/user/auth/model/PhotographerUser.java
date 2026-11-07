package com.framebucket.user.auth.model;

import com.framebucket.user.subscription.model.SubscriptionPlan;
import com.framebucket.common.phone.IndianMobileNumber;
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

    @Column(name = "sub_id", length = 512)
    private String subId;

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

    @Column(name = "customer_sequence", nullable = false)
    private int customerSequence;

    @Column(name = "event_sequence", nullable = false)
    private int eventSequence;

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
        this.mobileNumber = mobileNumber == null ? null : IndianMobileNumber.normalize(mobileNumber);
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

    public int allocateCustomerSequence() {
        if (customerSequence > 999) {
            throw new IllegalStateException("This photographer has reached the customer ID limit.");
        }
        return customerSequence++;
    }

    public int allocateEventSequence() {
        if (eventSequence > 99999) {
            throw new IllegalStateException("This photographer has reached the event ID limit.");
        }
        return eventSequence++;
    }

    public int getEventSequence() {
        return eventSequence;
    }

    public String getFullName() {
        return fullName;
    }

    public String getStudioName() {
        return studioName;
    }

    public String getSubId() {
        return subId;
    }

    public void setSubId(String subId) {
        this.subId = subId;
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
