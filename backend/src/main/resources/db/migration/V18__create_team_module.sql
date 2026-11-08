CREATE TABLE fb_team (
    id UUID PRIMARY KEY,
    photographer_id UUID NOT NULL REFERENCES fb_user(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    age INTEGER,
    mobile_number VARCHAR(16) NOT NULL,
    languages TEXT NOT NULL DEFAULT '',
    member_type VARCHAR(24) NOT NULL CHECK (member_type IN ('TEAM_MEMBER', 'FREELANCER')),
    skills TEXT NOT NULL DEFAULT '',
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'ACTIVE')),
    invite_token_hash VARCHAR(64),
    invite_expires_at TIMESTAMPTZ,
    invite_accepted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_fb_team_photographer_mobile UNIQUE (photographer_id, mobile_number)
);

CREATE UNIQUE INDEX ux_fb_team_invite_token_hash
    ON fb_team (invite_token_hash)
    WHERE invite_token_hash IS NOT NULL;

CREATE INDEX ix_fb_team_photographer_status_created
    ON fb_team (photographer_id, status, created_at DESC);
