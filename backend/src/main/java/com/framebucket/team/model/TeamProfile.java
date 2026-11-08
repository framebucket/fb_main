package com.framebucket.team.model;

import com.framebucket.user.auth.model.PhotographerUser;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.time.Instant;
import java.util.UUID;

@MappedSuperclass
public abstract class TeamProfile {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "photographer_id", nullable = false)
    private PhotographerUser photographer;

    @Column(name = "mobile_number", nullable = false, length = 16)
    private String mobileNumber;

    @Column(nullable = false, length = 120)
    private String name;

    private Integer age;

    @Column(nullable = false, columnDefinition = "text")
    private String languages;

    @Column(nullable = false, columnDefinition = "text")
    private String skills;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TeamMemberStatus status;

    @Column(name = "invite_token_hash", length = 64)
    private String inviteTokenHash;

    @Column(name = "invite_expires_at")
    private Instant inviteExpiresAt;

    @Column(name = "invite_accepted_at")
    private Instant inviteAcceptedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TeamProfile() {
    }

    protected TeamProfile(
            PhotographerUser photographer,
            String mobileNumber,
            String name,
            Integer age,
            String languages,
            String skills,
            TeamMemberStatus status,
            String inviteTokenHash,
            Instant inviteExpiresAt
    ) {
        this.id = UUID.randomUUID();
        this.photographer = photographer;
        this.mobileNumber = mobileNumber;
        this.name = name.trim();
        this.age = age;
        this.languages = languages;
        this.skills = skills;
        this.status = status;
        this.inviteTokenHash = inviteTokenHash;
        this.inviteExpiresAt = inviteExpiresAt;
    }

    @PrePersist
    void setCreationTimes() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }

    public void completeProfile(String name, int age, String languages, String skills, Instant acceptedAt) {
        this.name = name.trim();
        this.age = age;
        this.languages = languages;
        this.skills = skills;
        this.status = TeamMemberStatus.ACTIVE;
        this.inviteTokenHash = null;
        this.inviteExpiresAt = null;
        this.inviteAcceptedAt = acceptedAt;
    }

    public void issueInvite(String tokenHash, Instant expiresAt) {
        this.inviteTokenHash = tokenHash;
        this.inviteExpiresAt = expiresAt;
    }

    public abstract MemberType getMemberType();

    public UUID getId() { return id; }
    public PhotographerUser getPhotographer() { return photographer; }
    public String getMobileNumber() { return mobileNumber; }
    public String getName() { return name; }
    public Integer getAge() { return age; }
    public String getLanguages() { return languages; }
    public String getSkills() { return skills; }
    public TeamMemberStatus getStatus() { return status; }
    public Instant getInviteExpiresAt() { return inviteExpiresAt; }
    public Instant getCreatedAt() { return createdAt; }
}
