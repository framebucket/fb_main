package com.framebucket.subscription.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fb_subscription")
public class SubscriptionPlan {
    @Id
    private UUID id;

    @Column(name = "plan_code", nullable = false, length = 40, unique = true)
    private String planCode;

    @Column(name = "plan_name", nullable = false, length = 100)
    private String planName;

    @Column(length = 500)
    private String description;

    @Column(name = "price_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceAmount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 20)
    private BillingCycle billingCycle;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SubscriptionPlan() {
    }

    public SubscriptionPlan(
            String planCode,
            String planName,
            String description,
            BigDecimal priceAmount,
            String currencyCode,
            BillingCycle billingCycle
    ) {
        this.id = UUID.randomUUID();
        this.planCode = planCode;
        this.planName = planName;
        this.description = description;
        this.priceAmount = priceAmount;
        this.currencyCode = currencyCode;
        this.billingCycle = billingCycle;
        this.active = true;
    }

    @PrePersist
    void setCreatedAtIfMissing() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public String getPlanCode() {
        return planCode;
    }

    public String getPlanName() {
        return planName;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPriceAmount() {
        return priceAmount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public BillingCycle getBillingCycle() {
        return billingCycle;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public enum BillingCycle {
        MONTHLY,
        YEARLY,
        LIFETIME
    }
}
