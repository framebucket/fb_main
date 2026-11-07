package com.framebucket.customer.model;

import com.framebucket.user.auth.model.PhotographerUser;
import com.framebucket.common.phone.IndianMobileNumber;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "fb_customer")
public class Customer {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "photographer_id", nullable = false)
    private PhotographerUser photographer;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "customer_code", nullable = false, length = 200)
    private String customerCode;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "mobile_number", nullable = false, length = 16)
    private String mobileNumber;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(nullable = false, length = 1000)
    private String address;

    @Column(name = "events_booked", nullable = false)
    private int eventsBooked;

    @Column(name = "total_revenue_generated", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalRevenueGenerated;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Customer() {
    }

    public Customer(
            PhotographerUser photographer,
            String customerCode,
            String name,
            String email,
            String mobileNumber,
            LocalDate dateOfBirth,
            String address
    ) {
        this.id = UUID.randomUUID();
        this.photographer = photographer;
        this.customerCode = customerCode;
        this.name = name.trim();
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.mobileNumber = IndianMobileNumber.normalize(mobileNumber);
        this.dateOfBirth = dateOfBirth;
        this.address = address.trim();
        this.eventsBooked = 0;
        this.totalRevenueGenerated = BigDecimal.ZERO.setScale(2);
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

    public String getName() {
        return name;
    }

    public String getCustomerCode() {
        return customerCode;
    }

    public PhotographerUser getPhotographer() {
        return photographer;
    }

    public void recordEventBooking() {
        eventsBooked++;
    }

    public String getEmail() {
        return email;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public String getAddress() {
        return address;
    }

    public int getEventsBooked() {
        return eventsBooked;
    }

    public BigDecimal getTotalRevenueGenerated() {
        return totalRevenueGenerated;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
