package com.framebucket.event.model;

import com.framebucket.customer.model.Customer;
import com.framebucket.team.model.Freelancer;
import com.framebucket.team.model.TeamMember;
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
import java.time.LocalTime;
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

    @Column(name = "event_time")
    private LocalTime eventTime;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_type_id", nullable = false)
    private EventType eventType;

    @Column(columnDefinition = "TEXT")
    private String price;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_member_assignee_id")
    private TeamMember teamMemberAssignee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "freelancer_assignee_id")
    private Freelancer freelancerAssignee;

    @Column(name = "location_name", length = 240)
    private String locationName;

    @Column(name = "location_map_url", length = 2048)
    private String locationMapUrl;

    @Column(name = "location_landmark", length = 240)
    private String locationLandmark;

    @Column(name = "location_notes", length = 2000)
    private String locationNotes;

    @Column(name = "location_updated_at")
    private Instant locationUpdatedAt;

    @Column(name = "event_gallery", columnDefinition = "TEXT")
    private String eventGallery;

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

    public LocalTime getEventTime() { return eventTime; }

    public EventType getEventType() {
        return eventType;
    }

    public String getPrice() {
        return price;
    }

    public Customer getCustomer() {
        return customer;
    }

    public TeamMember getTeamMemberAssignee() { return teamMemberAssignee; }

    public Freelancer getFreelancerAssignee() { return freelancerAssignee; }

    public String getLocationName() { return locationName; }

    public String getLocationMapUrl() { return locationMapUrl; }

    public String getLocationLandmark() { return locationLandmark; }

    public String getLocationNotes() { return locationNotes; }

    public Instant getLocationUpdatedAt() { return locationUpdatedAt; }

    public String getEventGallery() { return eventGallery; }

    public void setEventGallery(String eventGallery) { this.eventGallery = eventGallery; }

    public void assign(TeamMember teamMember, Freelancer freelancer) {
        this.teamMemberAssignee = teamMember;
        this.freelancerAssignee = freelancer;
    }

    public void updateLocation(String name, String mapUrl, String landmark, String notes, LocalTime time, Instant updatedAt) {
        this.locationName = name;
        this.locationMapUrl = mapUrl;
        this.locationLandmark = landmark;
        this.locationNotes = notes;
        this.eventTime = time;
        this.locationUpdatedAt = updatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
