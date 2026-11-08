package com.framebucket.team.model;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "fb_team_contact")
public class TeamContact {
    @EmbeddedId
    private TeamContactId id;

    protected TeamContact() {
    }

    public TeamContact(TeamContactId id) {
        this.id = id;
    }
}
