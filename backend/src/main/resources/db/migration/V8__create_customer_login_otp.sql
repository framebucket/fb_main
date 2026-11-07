CREATE TABLE fb_customer_login_otp
(
    id            UUID         PRIMARY KEY,
    mobile_number VARCHAR(16)  NOT NULL,
    code_hash     VARCHAR(64)  NOT NULL,
    expires_at    TIMESTAMPTZ  NOT NULL,
    consumed_at   TIMESTAMPTZ,
    attempt_count INTEGER      NOT NULL DEFAULT 0 CHECK (attempt_count BETWEEN 0 AND 5),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_fb_customer_login_otp_mobile_created
    ON fb_customer_login_otp (mobile_number, created_at DESC);
