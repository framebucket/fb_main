package com.framebucket.team.model;

import com.framebucket.user.auth.model.PhotographerUser;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.Instant;

@Entity
@Table(name = "fb_team_member")
public class TeamMember extends TeamProfile {
    protected TeamMember() {
    }

    public TeamMember(
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
        return MemberType.TEAM_MEMBER;
    }
}
