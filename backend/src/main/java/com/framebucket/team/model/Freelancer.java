package com.framebucket.team.model;

import com.framebucket.user.auth.model.PhotographerUser;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.Instant;

@Entity
@Table(name = "fb_freelancer")
public class Freelancer extends TeamProfile {
    protected Freelancer() {
    }

    public Freelancer(
            PhotographerUser photographer,
            String name,
            Integer age,
            String mobileNumber,
            String languages,
            String skills,
            TeamMemberStatus status,
            String inviteTokenHash,
            Instant inviteExpiresAt
    ) {
        super(photographer, mobileNumber, name, age, languages, skills, status, inviteTokenHash, inviteExpiresAt);
    }

    @Override
    @Transient
    public MemberType getMemberType() {
        return MemberType.FREELANCER;
    }
}
