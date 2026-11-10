package com.framebucket.gallery;

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
import java.time.Duration;

@Entity
@Table(name = "fb_event_guest_photo_delivery", uniqueConstraints = @UniqueConstraint(
        name = "ux_fb_guest_photo_delivery_guest_photo", columnNames = {"guest_id", "photo_id"}
))
public class EventGuestPhotoDelivery {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guest_id", nullable = false)
    private EventGuest guest;

    @Column(name = "photo_id", nullable = false, length = 128)
    private String photoId;

    @Column(name = "photo_number")
    private Integer photoNumber;

    @Column(name = "delivery_status", nullable = false, length = 20)
    private String status;

    @Column(name = "failure_detail", length = 500)
    private String failureDetail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    protected EventGuestPhotoDelivery() {
    }

    public EventGuestPhotoDelivery(EventGuest guest, String photoId, Integer photoNumber) {
        this.id = UUID.randomUUID();
        this.guest = guest;
        this.photoId = photoId;
        this.photoNumber = photoNumber;
        this.status = "PENDING";
    }

    @PrePersist
    void setCreatedAtIfMissing() {
        if (createdAt == null) createdAt = Instant.now();
        if (nextAttemptAt == null) nextAttemptAt = Instant.now();
    }

    public void markSending() {
        status = "SENDING";
        nextAttemptAt = Instant.now().plus(Duration.ofMinutes(10));
    }

    public void markSent() {
        status = "SENT";
        failureDetail = null;
        sentAt = Instant.now();
    }

    public void markFailed(String detail) {
        attemptCount++;
        status = attemptCount >= 5 ? "FAILED_FINAL" : "FAILED";
        nextAttemptAt = Instant.now().plus(Duration.ofMinutes(Math.min(60, 1L << attemptCount)));
        failureDetail = detail == null ? "WhatsApp delivery failed." : detail.substring(0, Math.min(500, detail.length()));
    }

    public void retry() {
        status = "PENDING";
        failureDetail = null;
        nextAttemptAt = Instant.now();
    }

    public UUID getId() { return id; }
    public EventGuest getGuest() { return guest; }
    public String getPhotoId() { return photoId; }
    public Integer getPhotoNumber() { return photoNumber; }
    public String getStatus() { return status; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
}
