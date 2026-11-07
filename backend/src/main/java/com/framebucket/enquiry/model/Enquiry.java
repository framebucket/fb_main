package com.framebucket.enquiry.model;

import com.framebucket.customer.model.Customer;
import com.framebucket.event.model.EventType;
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
import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(
        name = "fb_enquiries",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_fb_enquiries_photographer_code",
                columnNames = {"photographer_id", "enquiry_id"}
        )
)
public class Enquiry {
    @Id
    private UUID id;

    @Column(name = "enquiry_id", nullable = false, length = 256)
    private String enquiryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "photographer_id", nullable = false)
    private PhotographerUser photographer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_type_id", nullable = false)
    private EventType eventType;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "mobile_number", nullable = false, length = 16)
    private String mobileNumber;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "quote_token_hash", nullable = false, unique = true, length = 64)
    private String quoteTokenHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "converted_customer_id")
    private Customer convertedCustomer;

    @Column(name = "finalized_quote", precision = 12, scale = 2)
    private BigDecimal finalizedQuote;

    @Column(name = "created_event_id")
    private UUID createdEventId;

    @Column(name = "created_event_code", length = 200)
    private String createdEventCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Enquiry() {
    }

    public Enquiry(
            String enquiryId,
            PhotographerUser photographer,
            EventType eventType,
            String name,
            String mobileNumber,
            String email,
            String quoteTokenHash
    ) {
        this.id = UUID.randomUUID();
        this.enquiryId = enquiryId;
        this.photographer = photographer;
        this.eventType = eventType;
        this.name = name.trim();
        this.mobileNumber = mobileNumber.trim();
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.quoteTokenHash = quoteTokenHash;
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

    public String getEnquiryId() {
        return enquiryId;
    }

    public PhotographerUser getPhotographer() {
        return photographer;
    }

    public EventType getEventType() {
        return eventType;
    }

    public String getName() {
        return name;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public String getEmail() {
        return email;
    }

    public String getQuoteTokenHash() {
        return quoteTokenHash;
    }

    public Customer getConvertedCustomer() {
        return convertedCustomer;
    }

    public void convertToCustomer(Customer customer) {
        this.convertedCustomer = customer;
    }

    public BigDecimal getFinalizedQuote() {
        return finalizedQuote;
    }

    public void setFinalizedQuote(BigDecimal finalizedQuote) {
        this.finalizedQuote = finalizedQuote;
    }

    public UUID getCreatedEventId() {
        return createdEventId;
    }

    public String getCreatedEventCode() {
        return createdEventCode;
    }

    public void setCreatedEvent(UUID eventId, String eventCode) {
        this.createdEventId = eventId;
        this.createdEventCode = eventCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
