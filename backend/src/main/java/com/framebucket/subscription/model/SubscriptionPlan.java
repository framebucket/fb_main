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

    @Column(name = "trial_days", nullable = false)
    private int trialDays;

    @Column(name = "customer_order_tracking", nullable = false)
    private boolean customerOrderTracking;

    @Column(name = "ai_image_delivery", nullable = false)
    private boolean aiImageDelivery;

    @Column(name = "quotation_calendar", nullable = false)
    private boolean quotationCalendar;

    @Column(name = "data_module_enabled", nullable = false)
    private boolean dataModuleEnabled;

    @Column(name = "raw_photo_selection", nullable = false)
    private boolean rawPhotoSelection;

    @Column(name = "max_storage_gb")
    private Long maxStorageGb;

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
            BillingCycle billingCycle,
            int trialDays,
            boolean customerOrderTracking,
            boolean aiImageDelivery,
            boolean quotationCalendar,
            boolean dataModuleEnabled,
            boolean rawPhotoSelection,
            Long maxStorageGb
    ) {
        this.id = UUID.randomUUID();
        this.planCode = planCode;
        this.planName = planName;
        this.description = description;
        this.priceAmount = priceAmount;
        this.currencyCode = currencyCode;
        this.billingCycle = billingCycle;
        this.trialDays = trialDays;
        this.customerOrderTracking = customerOrderTracking;
        this.aiImageDelivery = aiImageDelivery;
        this.quotationCalendar = quotationCalendar;
        this.dataModuleEnabled = dataModuleEnabled;
        this.rawPhotoSelection = rawPhotoSelection;
        this.maxStorageGb = maxStorageGb;
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

    public int getTrialDays() {
        return trialDays;
    }

    public boolean isCustomerOrderTracking() {
        return customerOrderTracking;
    }

    public boolean isAiImageDelivery() {
        return aiImageDelivery;
    }

    public boolean isQuotationCalendar() {
        return quotationCalendar;
    }

    public boolean isDataModuleEnabled() {
        return dataModuleEnabled;
    }

    public boolean isRawPhotoSelection() {
        return rawPhotoSelection;
    }

    public Long getMaxStorageGb() {
        return maxStorageGb;
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
