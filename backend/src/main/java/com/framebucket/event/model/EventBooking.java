package com.framebucket.event.model;

import com.framebucket.customer.model.Customer;
import com.framebucket.user.auth.model.PhotographerUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "fb_event_booking",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_fb_event_booking_photographer_code",
                columnNames = {"photographer_id", "event_code"}
        )
)
public class EventBooking {
    @Id
    private UUID id;

    @Column(name = "event_code", nullable = false, length = 200)
    private String eventCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "photographer_id", nullable = false)
    private PhotographerUser photographer;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_type_id", nullable = false)
    private EventType eventType;

    @Column(columnDefinition = "TEXT")
    private String price;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EventBooking() {
    }

    public EventBooking(
            String eventCode,
            PhotographerUser photographer,
            LocalDate eventDate,
            EventType eventType,
            String price,
            Customer customer
    ) {
        this.id = UUID.randomUUID();
        this.eventCode = eventCode;
        this.photographer = photographer;
        this.eventDate = eventDate;
        this.eventType = eventType;
        this.price = price;
        this.customer = customer;
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

    public String getEventCode() {
        return eventCode;
    }

    public PhotographerUser getPhotographer() {
        return photographer;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public EventType getEventType() {
        return eventType;
    }

    public String getPrice() {
        return price;
    }

    public Customer getCustomer() {
        return customer;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
