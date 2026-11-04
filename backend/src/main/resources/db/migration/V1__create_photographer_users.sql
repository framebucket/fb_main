CREATE TABLE photographer_users
(
    id            UUID         PRIMARY KEY,
    full_name     VARCHAR(120) NOT NULL,
    studio_name   VARCHAR(160),
    email         VARCHAR(320) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX ux_photographer_users_email_ci
    ON photographer_users (LOWER(email));
