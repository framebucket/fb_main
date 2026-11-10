package com.framebucket.gallery;

import com.framebucket.event.model.EventBooking;
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
import java.util.UUID;

@Entity
@Table(name = "fb_event_guest", uniqueConstraints = @UniqueConstraint(
        name = "ux_fb_event_guest_event_mobile", columnNames = {"event_id", "mobile_number"}
))
public class EventGuest {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private EventBooking event;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "mobile_number", nullable = false, length = 13)
    private String mobileNumber;

    @Column(name = "face_consent_at", nullable = false)
    private Instant faceConsentAt;

    @Column(name = "whatsapp_consent_at", nullable = false)
    private Instant whatsappConsentAt;

    @Column(name = "deletion_token_hash", nullable = false, length = 64)
    private String deletionTokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EventGuest() {
    }

    public EventGuest(EventBooking event, String fullName, String mobileNumber, Instant consentAt) {
        this.id = UUID.randomUUID();
        this.event = event;
        this.fullName = fullName;
        this.mobileNumber = mobileNumber;
        this.faceConsentAt = consentAt;
        this.whatsappConsentAt = consentAt;
    }

    public void updateConsentAndName(String fullName, Instant consentAt) {
        this.fullName = fullName;
        this.faceConsentAt = consentAt;
        this.whatsappConsentAt = consentAt;
    }

    public void setDeletionTokenHash(String deletionTokenHash) {
        this.deletionTokenHash = deletionTokenHash;
    }

    public boolean hasDeletionTokenHash(String value) {
        return deletionTokenHash != null && deletionTokenHash.equals(value);
    }

    @PrePersist
    void setCreatedAtIfMissing() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public EventBooking getEvent() { return event; }
    public String getFullName() { return fullName; }
    public String getMobileNumber() { return mobileNumber; }
    public Instant getFaceConsentAt() { return faceConsentAt; }
    public Instant getWhatsappConsentAt() { return whatsappConsentAt; }
    public String getDeletionTokenHash() { return deletionTokenHash; }
}
