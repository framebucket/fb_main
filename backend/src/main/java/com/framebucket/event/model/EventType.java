package com.framebucket.event.model;

import com.framebucket.user.auth.model.PhotographerUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fb_event_list")
public class EventType {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "photographer_id")
    private PhotographerUser photographer;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "is_default", nullable = false)
    private boolean defaultType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EventType() {
    }

    public EventType(PhotographerUser photographer, String name) {
        this.id = UUID.randomUUID();
        this.photographer = photographer;
        this.name = name.trim();
        this.defaultType = false;
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

    public PhotographerUser getPhotographer() {
        return photographer;
    }

    public String getName() {
        return name;
    }
}
