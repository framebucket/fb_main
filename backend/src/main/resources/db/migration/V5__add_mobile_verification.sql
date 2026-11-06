ALTER TABLE fb_user
    ADD COLUMN mobile_number VARCHAR(16),
    ADD COLUMN mobile_verified BOOLEAN;

-- Existing accounts predate mobile verification and remain sign-in enabled.
UPDATE fb_user
SET mobile_verified = TRUE;

ALTER TABLE fb_user
    ALTER COLUMN mobile_verified SET DEFAULT FALSE,
    ALTER COLUMN mobile_verified SET NOT NULL;

CREATE TABLE fb_mobile_verification_code
(
    id          UUID         PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES fb_user (id) ON DELETE CASCADE,
    code_hash   VARCHAR(64)  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    consumed_at TIMESTAMPTZ,
    attempt_count INTEGER    NOT NULL DEFAULT 0 CHECK (attempt_count BETWEEN 0 AND 5),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_fb_mobile_verification_user_id
    ON fb_mobile_verification_code (user_id);
