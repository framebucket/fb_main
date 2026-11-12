-- Studio settings used by payment QR codes, receipts, the public portfolio and reminders.
CREATE TABLE fb_studio_profile
(
    photographer_id      UUID          PRIMARY KEY REFERENCES fb_user (id) ON DELETE CASCADE,
    upi_id               VARCHAR(100),
    upi_payee_name       VARCHAR(120),
    business_address     VARCHAR(500),
    gstin                VARCHAR(20),
    receipt_footer       VARCHAR(500),
    receipt_sequence     INTEGER       NOT NULL DEFAULT 1,
    portfolio_slug       VARCHAR(60)   UNIQUE,
    portfolio_headline   VARCHAR(160),
    portfolio_bio        VARCHAR(2000),
    portfolio_public     BOOLEAN       NOT NULL DEFAULT FALSE,
    reminders_enabled    BOOLEAN       NOT NULL DEFAULT TRUE,
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Money received from a customer against an event (advance, balance, paid downloads, ...).
CREATE TABLE fb_event_payment
(
    id               UUID           PRIMARY KEY,
    photographer_id  UUID           NOT NULL REFERENCES fb_user (id) ON DELETE CASCADE,
    event_id         UUID           NOT NULL REFERENCES fb_event_booking (id) ON DELETE CASCADE,
    receipt_number   VARCHAR(40)    NOT NULL,
    amount           NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    paid_on          DATE           NOT NULL,
    method           VARCHAR(20)    NOT NULL,
    purpose          VARCHAR(20)    NOT NULL,
    reference        VARCHAR(120),
    notes            VARCHAR(500),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_fb_event_payment_receipt UNIQUE (photographer_id, receipt_number)
);
CREATE INDEX ix_fb_event_payment_event ON fb_event_payment (event_id);

CREATE TABLE fb_expense
(
    id               UUID           PRIMARY KEY,
    photographer_id  UUID           NOT NULL REFERENCES fb_user (id) ON DELETE CASCADE,
    event_id         UUID           REFERENCES fb_event_booking (id) ON DELETE SET NULL,
    category         VARCHAR(40)    NOT NULL,
    amount           NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    spent_on         DATE           NOT NULL,
    vendor           VARCHAR(160),
    notes            VARCHAR(500),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_fb_expense_photographer_date ON fb_expense (photographer_id, spent_on);

-- Customer gallery download rules. Events without a row behave as FREE (the previous behaviour).
CREATE TABLE fb_event_download_policy
(
    event_id     UUID           PRIMARY KEY REFERENCES fb_event_booking (id) ON DELETE CASCADE,
    mode         VARCHAR(10)    NOT NULL DEFAULT 'FREE' CHECK (mode IN ('FREE', 'PAID', 'DISABLED')),
    price        NUMERIC(12, 2) CHECK (price IS NULL OR price > 0),
    unlocked_at  TIMESTAMPTZ,
    updated_at   TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE fb_portfolio_item
(
    id               UUID          PRIMARY KEY,
    photographer_id  UUID          NOT NULL REFERENCES fb_user (id) ON DELETE CASCADE,
    event_id         UUID          NOT NULL REFERENCES fb_event_booking (id) ON DELETE CASCADE,
    media_id         VARCHAR(128)  NOT NULL,
    caption          VARCHAR(240),
    sort_order       INTEGER       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_fb_portfolio_item_media UNIQUE (photographer_id, event_id, media_id)
);

CREATE TABLE fb_flipbook
(
    id               UUID          PRIMARY KEY,
    photographer_id  UUID          NOT NULL REFERENCES fb_user (id) ON DELETE CASCADE,
    event_id         UUID          NOT NULL REFERENCES fb_event_booking (id) ON DELETE CASCADE,
    title            VARCHAR(160)  NOT NULL,
    access_token     VARCHAR(64)   NOT NULL UNIQUE,
    media_ids        TEXT          NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_fb_flipbook_event ON fb_flipbook (event_id);

-- One row per reminder that was sent, so several app instances never send the same reminder twice.
CREATE TABLE fb_event_reminder
(
    event_id  UUID         NOT NULL REFERENCES fb_event_booking (id) ON DELETE CASCADE,
    kind      VARCHAR(30)  NOT NULL,
    sent_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id, kind)
);
