CREATE TABLE fb_enquiries
(
    id                   UUID PRIMARY KEY,
    enquiry_id           VARCHAR(256) NOT NULL,
    photographer_id      UUID NOT NULL REFERENCES fb_user (id) ON DELETE CASCADE,
    event_type_id        UUID NOT NULL REFERENCES fb_event_list (id),
    name                 VARCHAR(120) NOT NULL,
    mobile_number        VARCHAR(16) NOT NULL,
    email                VARCHAR(320) NOT NULL,
    quote_token_hash     VARCHAR(64) NOT NULL UNIQUE,
    converted_customer_id UUID,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ux_fb_enquiries_photographer_code UNIQUE (photographer_id, enquiry_id),
    CONSTRAINT fk_fb_enquiries_converted_customer_owner
        FOREIGN KEY (converted_customer_id, photographer_id)
        REFERENCES fb_customer (id, photographer_id)
);

CREATE INDEX ix_fb_enquiries_photographer_created_at
    ON fb_enquiries (photographer_id, created_at DESC);

CREATE TABLE fb_quotaion
(
    id                       UUID PRIMARY KEY,
    enquiry_id               UUID NOT NULL UNIQUE REFERENCES fb_enquiries (id) ON DELETE CASCADE,
    quote_number             VARCHAR(80) NOT NULL UNIQUE,
    customer_name            VARCHAR(120) NOT NULL,
    customer_mobile          VARCHAR(16) NOT NULL,
    customer_email           VARCHAR(320) NOT NULL,
    occasion                 VARCHAR(100) NOT NULL,
    venue                    VARCHAR(240),
    preferred_event_date     DATE,
    preferred_time           VARCHAR(40),
    event_count              INTEGER NOT NULL CHECK (event_count BETWEEN 1 AND 8),
    event_selections_json    TEXT NOT NULL,
    album_sheets             INTEGER NOT NULL DEFAULT 0 CHECK (album_sheets BETWEEN 0 AND 500),
    coupon_code              VARCHAR(40),
    final_price              NUMERIC(12, 2) NOT NULL CHECK (final_price >= 0),
    quote_pdf                BYTEA NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
