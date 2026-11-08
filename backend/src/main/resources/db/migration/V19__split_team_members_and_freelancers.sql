CREATE TABLE fb_team_contact (
    photographer_id UUID NOT NULL REFERENCES fb_user(id) ON DELETE CASCADE,
    mobile_number VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (photographer_id, mobile_number)
);

INSERT INTO fb_team_contact (photographer_id, mobile_number, created_at)
SELECT photographer_id, mobile_number, MIN(created_at)
FROM fb_team
GROUP BY photographer_id, mobile_number;

CREATE TABLE fb_team_member (
    id UUID PRIMARY KEY,
    photographer_id UUID NOT NULL REFERENCES fb_user(id) ON DELETE CASCADE,
    mobile_number VARCHAR(16) NOT NULL,
    name VARCHAR(120) NOT NULL,
    age INTEGER,
    languages TEXT NOT NULL DEFAULT '',
    skills TEXT NOT NULL DEFAULT '',
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'ACTIVE')),
    invite_token_hash VARCHAR(64),
    invite_expires_at TIMESTAMPTZ,
    invite_accepted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_fb_team_member_photographer_mobile UNIQUE (photographer_id, mobile_number),
    CONSTRAINT fk_fb_team_member_contact FOREIGN KEY (photographer_id, mobile_number)
        REFERENCES fb_team_contact (photographer_id, mobile_number) ON DELETE CASCADE
);

CREATE TABLE fb_freelancer (
    id UUID PRIMARY KEY,
    photographer_id UUID NOT NULL REFERENCES fb_user(id) ON DELETE CASCADE,
    mobile_number VARCHAR(16) NOT NULL,
    name VARCHAR(120) NOT NULL,
    age INTEGER,
    languages TEXT NOT NULL DEFAULT '',
    skills TEXT NOT NULL DEFAULT '',
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'ACTIVE')),
    invite_token_hash VARCHAR(64),
    invite_expires_at TIMESTAMPTZ,
    invite_accepted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_fb_freelancer_photographer_mobile UNIQUE (photographer_id, mobile_number),
    CONSTRAINT fk_fb_freelancer_contact FOREIGN KEY (photographer_id, mobile_number)
        REFERENCES fb_team_contact (photographer_id, mobile_number) ON DELETE CASCADE
);

INSERT INTO fb_team_member (
    id, photographer_id, mobile_number, name, age, languages, skills, status,
    invite_token_hash, invite_expires_at, invite_accepted_at, created_at, updated_at
)
SELECT id, photographer_id, mobile_number, name, age, languages, skills, status,
       invite_token_hash, invite_expires_at, invite_accepted_at, created_at, updated_at
FROM fb_team
WHERE member_type = 'TEAM_MEMBER';

INSERT INTO fb_freelancer (
    id, photographer_id, mobile_number, name, age, languages, skills, status,
    invite_token_hash, invite_expires_at, invite_accepted_at, created_at, updated_at
)
SELECT id, photographer_id, mobile_number, name, age, languages, skills, status,
       invite_token_hash, invite_expires_at, invite_accepted_at, created_at, updated_at
FROM fb_team
WHERE member_type = 'FREELANCER';

CREATE UNIQUE INDEX ux_fb_team_member_invite_token_hash
    ON fb_team_member (invite_token_hash) WHERE invite_token_hash IS NOT NULL;
CREATE INDEX ix_fb_team_member_photographer_status_created
    ON fb_team_member (photographer_id, status, created_at DESC);

CREATE UNIQUE INDEX ux_fb_freelancer_invite_token_hash
    ON fb_freelancer (invite_token_hash) WHERE invite_token_hash IS NOT NULL;
CREATE INDEX ix_fb_freelancer_photographer_status_created
    ON fb_freelancer (photographer_id, status, created_at DESC);

DROP TABLE fb_team;
