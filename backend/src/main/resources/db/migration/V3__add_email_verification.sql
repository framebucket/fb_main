ALTER TABLE fb_user
    ADD COLUMN email_verified BOOLEAN;

-- Keep accounts created before verification was introduced usable.
UPDATE fb_user
SET email_verified = TRUE;

ALTER TABLE fb_user
    ALTER COLUMN email_verified SET DEFAULT FALSE,
    ALTER COLUMN email_verified SET NOT NULL;

CREATE TABLE fb_email_verification_token
(
    id          UUID         PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES fb_user (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_fb_email_verification_user_id
    ON fb_email_verification_token (user_id);
