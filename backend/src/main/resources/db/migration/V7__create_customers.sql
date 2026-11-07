CREATE TABLE fb_customer
(
    id                       UUID           PRIMARY KEY,
    photographer_id          UUID           NOT NULL REFERENCES fb_user (id) ON DELETE CASCADE,
    name                     VARCHAR(120)   NOT NULL,
    email                    VARCHAR(320)   NOT NULL,
    mobile_number            VARCHAR(16)    NOT NULL,
    date_of_birth            DATE           NOT NULL,
    address                  VARCHAR(1000)  NOT NULL,
    events_booked            INTEGER        NOT NULL DEFAULT 0 CHECK (events_booked >= 0),
    total_revenue_generated  NUMERIC(12, 2) NOT NULL DEFAULT 0.00 CHECK (total_revenue_generated >= 0),
    created_at               TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ux_fb_customer_photographer_email UNIQUE (photographer_id, email)
);

CREATE INDEX ix_fb_customer_photographer_created_at
    ON fb_customer (photographer_id, created_at DESC);
