-- Passwordless sign-in: photographers get a one-time code on WhatsApp (copy by email).
CREATE TABLE IF NOT EXISTS fb_photographer_login_otp (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES fb_user(id) ON DELETE CASCADE,
    code_hash VARCHAR(128) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_fb_photographer_login_otp_user ON fb_photographer_login_otp (user_id, created_at DESC);

-- Remembered devices for the Windows/Mac, Android and iOS apps: they stay signed in until the app is
-- updated (the app version is part of the session) or the user signs out.
CREATE TABLE IF NOT EXISTS fb_device_session (
    id UUID PRIMARY KEY,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    principal_kind VARCHAR(20) NOT NULL CHECK (principal_kind IN ('PHOTOGRAPHER', 'CUSTOMER')),
    user_id UUID REFERENCES fb_user(id) ON DELETE CASCADE,
    customer_mobile VARCHAR(20),
    app_name VARCHAR(40) NOT NULL,
    app_version VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_fb_device_session_user ON fb_device_session (user_id) WHERE revoked_at IS NULL;
