ALTER TABLE fb_event_booking
    ADD COLUMN team_member_assignee_id UUID REFERENCES fb_team_member(id) ON DELETE SET NULL,
    ADD COLUMN freelancer_assignee_id UUID REFERENCES fb_freelancer(id) ON DELETE SET NULL,
    ADD COLUMN location_name VARCHAR(240),
    ADD COLUMN location_map_url VARCHAR(2048),
    ADD COLUMN location_landmark VARCHAR(240),
    ADD COLUMN location_notes VARCHAR(2000),
    ADD COLUMN location_updated_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_fb_event_booking_single_assignee
        CHECK (team_member_assignee_id IS NULL OR freelancer_assignee_id IS NULL);

CREATE INDEX ix_fb_event_booking_team_assignee
    ON fb_event_booking (team_member_assignee_id)
    WHERE team_member_assignee_id IS NOT NULL;

CREATE INDEX ix_fb_event_booking_freelancer_assignee
    ON fb_event_booking (freelancer_assignee_id)
    WHERE freelancer_assignee_id IS NOT NULL;
