package com.framebucket.team.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class TeamContactId implements Serializable {
    @Column(name = "photographer_id", nullable = false)
    private UUID photographerId;

    @Column(name = "mobile_number", nullable = false, length = 16)
    private String mobileNumber;

    protected TeamContactId() {
    }

    public TeamContactId(UUID photographerId, String mobileNumber) {
        this.photographerId = photographerId;
        this.mobileNumber = mobileNumber;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TeamContactId that)) return false;
        return Objects.equals(photographerId, that.photographerId)
                && Objects.equals(mobileNumber, that.mobileNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(photographerId, mobileNumber);
    }
}
