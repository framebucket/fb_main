-- Bookings (lead capture, per-studio packages, quote follow-ups, e-signed contracts, date holds),
-- delivery (checklist, films, album proofing, watermark & branding, custom domains),
-- crew scheduling & payouts, gear checklists, and plan entitlements for all of it.

-- ---------------------------------------------------------------- branding & watermark
CREATE TABLE fb_studio_brand
(
    photographer_id          UUID         PRIMARY KEY REFERENCES fb_user (id) ON DELETE CASCADE,
    brand_color              VARCHAR(7)   CHECK (brand_color IS NULL OR brand_color ~ '^#[0-9A-Fa-f]{6}$'),
    logo                     BYTEA,
    logo_content_type        VARCHAR(40),
    logo_updated_at          TIMESTAMPTZ,
    watermark_mode           VARCHAR(12)  NOT NULL DEFAULT 'OFF' CHECK (watermark_mode IN ('OFF', 'ALWAYS', 'UNTIL_PAID')),
    watermark_text           VARCHAR(60),
    watermark_use_logo       BOOLEAN      NOT NULL DEFAULT FALSE,
    watermark_position       VARCHAR(12)  NOT NULL DEFAULT 'TILED' CHECK (watermark_position IN ('CENTER', 'BOTTOM_RIGHT', 'TILED')),
    watermark_opacity        INTEGER      NOT NULL DEFAULT 30 CHECK (watermark_opacity BETWEEN 5 AND 80),
    custom_domain            VARCHAR(253) UNIQUE,
    custom_domain_verified_at TIMESTAMPTZ,
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---------------------------------------------------------------- quote catalogue per studio
CREATE TABLE fb_quote_settings
(
    photographer_id    UUID           PRIMARY KEY REFERENCES fb_user (id) ON DELETE CASCADE,
    album_sheet_price  NUMERIC(10, 2) NOT NULL DEFAULT 400 CHECK (album_sheet_price >= 0),
    discount_percent   NUMERIC(5, 2)  NOT NULL DEFAULT 5 CHECK (discount_percent BETWEEN 0 AND 90),
    advance_percent    NUMERIC(5, 2)  NOT NULL DEFAULT 30 CHECK (advance_percent BETWEEN 0 AND 100),
    contract_terms     TEXT,
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE fb_quote_item
(
    id               UUID           PRIMARY KEY,
    photographer_id  UUID           NOT NULL REFERENCES fb_user (id) ON DELETE CASCADE,
    code             VARCHAR(80)    NOT NULL,
    kind             VARCHAR(10)    NOT NULL CHECK (kind IN ('SERVICE', 'ADDON')),
    name             VARCHAR(120)   NOT NULL,
    description      VARCHAR(300),
    price            NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    active           BOOLEAN        NOT NULL DEFAULT TRUE,
    sort_order       INTEGER        NOT NULL DEFAULT 100,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_fb_quote_item_code UNIQUE (photographer_id, code)
);

CREATE TABLE fb_quote_package
(
    id               UUID           PRIMARY KEY,
    photographer_id  UUID           NOT NULL REFERENCES fb_user (id) ON DELETE CASCADE,
    name             VARCHAR(120)   NOT NULL,
    description      VARCHAR(500),
    inclusions       TEXT,
    price            NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    highlighted      BOOLEAN        NOT NULL DEFAULT FALSE,
    active           BOOLEAN        NOT NULL DEFAULT TRUE,
    sort_order       INTEGER        NOT NULL DEFAULT 100,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_fb_quote_package_photographer ON fb_quote_package (photographer_id);

-- Existing studios start with the catalogue FrameBucket used to hard-code (same codes and prices).
INSERT INTO fb_quote_item (id, photographer_id, code, kind, name, description, price, sort_order)
SELECT gen_random_uuid(), u.id, d.code, d.kind, d.name, d.description, d.price, d.sort_order
FROM fb_user u
CROSS JOIN (VALUES
    ('photo', 'SERVICE', 'Conventional Photography', 'Traditional event coverage', 7000, 10),
    ('video', 'SERVICE', 'Conventional Videography', 'Event video coverage', 8000, 20),
    ('candid', 'SERVICE', 'Candid Photography', 'Natural, candid moments', 20000, 30),
    ('candidvideo', 'SERVICE', 'Candid Videography', 'Cinematic candid film', 25000, 40),
    ('drone', 'SERVICE', 'Drone Videography', 'Aerial coverage where permitted', 8000, 50),
    ('led', 'SERVICE', 'LED Wall', 'On-site display setup', 8000, 60),
    ('mixing', 'SERVICE', 'Spot Mixing', 'Live video mixing setup', 20000, 70),
    ('qr', 'SERVICE', 'Live Photo QR Scanner', 'Guest photo access by QR', 3000, 80),
    ('youtube', 'SERVICE', 'YouTube Live', 'Live streaming coverage', 6000, 90),
    ('prewedding', 'ADDON', 'Pre-wedding shoot', 'Half-day couple shoot at one location', 25000, 100),
    ('sde', 'ADDON', 'Same-day edit reel', 'Short reel delivered on the event day', 12000, 110),
    ('parentsalbum', 'ADDON', 'Extra album for parents', 'Smaller copy of the main album', 9000, 120)
) AS d (code, kind, name, description, price, sort_order)
ON CONFLICT (photographer_id, code) DO NOTHING;

ALTER TABLE fb_quotaion
    ADD COLUMN package_id   UUID,
    ADD COLUMN package_name VARCHAR(120),
    ADD COLUMN addon_codes  TEXT;

-- ---------------------------------------------------------------- lead capture & quote follow-up
ALTER TABLE fb_enquiries
    ADD COLUMN source                 VARCHAR(20)  NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN message                VARCHAR(1000),
    ADD COLUMN preferred_date         DATE,
    ADD COLUMN quote_token_enc        VARCHAR(512),
    ADD COLUMN quote_opened_at        TIMESTAMPTZ,
    ADD COLUMN quote_reminder_sent_at TIMESTAMPTZ;
CREATE INDEX ix_fb_enquiries_follow_up ON fb_enquiries (created_at) WHERE quote_opened_at IS NULL AND quote_reminder_sent_at IS NULL;

-- ---------------------------------------------------------------- contracts & date holds
CREATE TABLE fb_contract
(
    id                      UUID           PRIMARY KEY,
    enquiry_id              UUID           NOT NULL UNIQUE REFERENCES fb_enquiries (id) ON DELETE CASCADE,
    photographer_id         UUID           NOT NULL REFERENCES fb_user (id) ON DELETE CASCADE,
    status                  VARCHAR(12)    NOT NULL CHECK (status IN ('SENT', 'SIGNED', 'CANCELLED')),
    terms                   TEXT           NOT NULL,
    total_amount            NUMERIC(12, 2) NOT NULL CHECK (total_amount >= 0),
    advance_amount          NUMERIC(12, 2) NOT NULL CHECK (advance_amount >= 0),
    event_date              DATE,
    sent_at                 TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    signed_at               TIMESTAMPTZ,
    signer_name             VARCHAR(120),
    signature_png           BYTEA,
    signer_ip               VARCHAR(64),
    signer_user_agent       VARCHAR(400),
    content_sha256          VARCHAR(64),
    advance_reference       VARCHAR(120),
    advance_reported_at     TIMESTAMPTZ,
    advance_confirmed_at    TIMESTAMPTZ,
    advance_method          VARCHAR(20),
    advance_paid_on         DATE,
    advance_payment_recorded BOOLEAN       NOT NULL DEFAULT FALSE,
    cancelled_at            TIMESTAMPTZ
);
CREATE INDEX ix_fb_contract_photographer ON fb_contract (photographer_id);

CREATE TABLE fb_date_hold
(
    id               UUID        PRIMARY KEY,
    photographer_id  UUID        NOT NULL REFERENCES fb_user (id) ON DELETE CASCADE,
    hold_date        DATE        NOT NULL,
    enquiry_id       UUID        NOT NULL UNIQUE REFERENCES fb_enquiries (id) ON DELETE CASCADE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_fb_date_hold_date UNIQUE (photographer_id, hold_date)
);

-- ---------------------------------------------------------------- delivery checklist
CREATE TABLE fb_delivery_step
(
    id               UUID         PRIMARY KEY,
    photographer_id  UUID         NOT NULL REFERENCES fb_user (id) ON DELETE CASCADE,
    event_id         UUID         NOT NULL REFERENCES fb_event_booking (id) ON DELETE CASCADE,
    title            VARCHAR(120) NOT NULL,
    kind             VARCHAR(20)  NOT NULL DEFAULT 'OTHER'
        CHECK (kind IN ('TEASER', 'HIGHLIGHTS', 'GALLERY', 'ALBUM_DESIGN', 'PRINTED_ALBUM', 'FULL_FILM', 'OTHER')),
    status           VARCHAR(12)  NOT NULL DEFAULT 'NOT_STARTED' CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'DONE')),
    due_date         DATE,
    completed_at     TIMESTAMPTZ,
    note             VARCHAR(500),
    sort_order       INTEGER      NOT NULL DEFAULT 100,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_fb_delivery_step_event ON fb_delivery_step (event_id, sort_order);

-- ---------------------------------------------------------------- films
CREATE TABLE fb_event_film
(
    id               UUID          PRIMARY KEY,
    photographer_id  UUID          NOT NULL REFERENCES fb_user (id) ON DELETE CASCADE,
    event_id         UUID          NOT NULL REFERENCES fb_event_booking (id) ON DELETE CASCADE,
    title            VARCHAR(120)  NOT NULL,
    kind             VARCHAR(12)   NOT NULL CHECK (kind IN ('TEASER', 'HIGHLIGHTS', 'FULL_FILM', 'REEL', 'OTHER')),
    media_id         VARCHAR(128),
    external_url     VARCHAR(2048),
    sort_order       INTEGER       NOT NULL DEFAULT 100,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_fb_event_film_source CHECK ((media_id IS NULL) <> (external_url IS NULL))
);
CREATE INDEX ix_fb_event_film_event ON fb_event_film (event_id, sort_order);

-- ---------------------------------------------------------------- album proofing
CREATE TABLE fb_album_proof
(
    id               UUID          PRIMARY KEY,
    photographer_id  UUID          NOT NULL REFERENCES fb_user (id) ON DELETE CASCADE,
    event_id         UUID          NOT NULL REFERENCES fb_event_booking (id) ON DELETE CASCADE,
    version          INTEGER       NOT NULL CHECK (version > 0),
    status           VARCHAR(20)   NOT NULL CHECK (status IN ('SENT', 'CHANGES_REQUESTED', 'APPROVED', 'SUPERSEDED')),
    media_ids        TEXT          NOT NULL,
    message          VARCHAR(1000),
    customer_note    VARCHAR(2000),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at     TIMESTAMPTZ,
    CONSTRAINT ux_fb_album_proof_version UNIQUE (event_id, version)
);

CREATE TABLE fb_album_proof_comment
(
    id            UUID          PRIMARY KEY,
    proof_id      UUID          NOT NULL REFERENCES fb_album_proof (id) ON DELETE CASCADE,
    spread_index  INTEGER       NOT NULL CHECK (spread_index >= 0),
    author        VARCHAR(12)   NOT NULL CHECK (author IN ('CUSTOMER', 'PHOTOGRAPHER')),
    body          VARCHAR(2000) NOT NULL,
    resolved      BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_fb_album_proof_comment_proof ON fb_album_proof_comment (proof_id, spread_index, created_at);

-- ---------------------------------------------------------------- crew & payouts
CREATE TABLE fb_event_crew
(
    id               UUID           PRIMARY KEY,
    photographer_id  UUID           NOT NULL REFERENCES fb_user (id) ON DELETE CASCADE,
    event_id         UUID           NOT NULL REFERENCES fb_event_booking (id) ON DELETE CASCADE,
    team_member_id   UUID           REFERENCES fb_team_member (id) ON DELETE CASCADE,
    freelancer_id    UUID           REFERENCES fb_freelancer (id) ON DELETE CASCADE,
    role             VARCHAR(80)    NOT NULL,
    call_time        TIME,
    fee              NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (fee >= 0),
    notes            VARCHAR(500),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_fb_event_crew_member CHECK ((team_member_id IS NULL) <> (freelancer_id IS NULL))
);
CREATE UNIQUE INDEX ux_fb_event_crew_team ON fb_event_crew (event_id, team_member_id) WHERE team_member_id IS NOT NULL;
CREATE UNIQUE INDEX ux_fb_event_crew_freelancer ON fb_event_crew (event_id, freelancer_id) WHERE freelancer_id IS NOT NULL;

CREATE TABLE fb_crew_unavailable
(
    id                UUID         PRIMARY KEY,
    photographer_id   UUID         NOT NULL REFERENCES fb_user (id) ON DELETE CASCADE,
    team_member_id    UUID         REFERENCES fb_team_member (id) ON DELETE CASCADE,
    freelancer_id     UUID         REFERENCES fb_freelancer (id) ON DELETE CASCADE,
    unavailable_date  DATE         NOT NULL,
    note              VARCHAR(200),
    CONSTRAINT ck_fb_crew_unavailable_member CHECK ((team_member_id IS NULL) <> (freelancer_id IS NULL))
);
CREATE INDEX ix_fb_crew_unavailable_date ON fb_crew_unavailable (photographer_id, unavailable_date);

CREATE TABLE fb_crew_payout
(
    id               UUID           PRIMARY KEY,
    photographer_id  UUID           NOT NULL REFERENCES fb_user (id) ON DELETE CASCADE,
    team_member_id   UUID           REFERENCES fb_team_member (id) ON DELETE CASCADE,
    freelancer_id    UUID           REFERENCES fb_freelancer (id) ON DELETE CASCADE,
    event_id         UUID           REFERENCES fb_event_booking (id) ON DELETE SET NULL,
    amount           NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    paid_on          DATE           NOT NULL,
    method           VARCHAR(20)    NOT NULL,
    reference        VARCHAR(120),
    notes            VARCHAR(500),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_fb_crew_payout_member CHECK ((team_member_id IS NULL) <> (freelancer_id IS NULL))
);
CREATE INDEX ix_fb_crew_payout_photographer ON fb_crew_payout (photographer_id, paid_on);

-- ---------------------------------------------------------------- gear
CREATE TABLE fb_gear_item
(
    id               UUID         PRIMARY KEY,
    photographer_id  UUID         NOT NULL REFERENCES fb_user (id) ON DELETE CASCADE,
    name             VARCHAR(120) NOT NULL,
    category         VARCHAR(40)  NOT NULL DEFAULT 'Other',
    serial_number    VARCHAR(80),
    in_default_kit   BOOLEAN      NOT NULL DEFAULT FALSE,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    notes            VARCHAR(300),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_fb_gear_item_photographer ON fb_gear_item (photographer_id);

CREATE TABLE fb_event_gear
(
    event_id   UUID    NOT NULL REFERENCES fb_event_booking (id) ON DELETE CASCADE,
    gear_id    UUID    NOT NULL REFERENCES fb_gear_item (id) ON DELETE CASCADE,
    packed     BOOLEAN NOT NULL DEFAULT FALSE,
    returned   BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (event_id, gear_id)
);

-- ---------------------------------------------------------------- plan entitlements
UPDATE fb_user_subscription
SET entitlements = jsonb_set(entitlements, '{features}',
        (entitlements -> 'features') || '["LEAD_CAPTURE","CUSTOM_PACKAGES","DELIVERY_TRACKER"]'::jsonb),
    updated_at = CURRENT_TIMESTAMP
WHERE plan_code IN ('FREE', 'SOLO', 'STUDIO', 'BUSINESS', 'FREE_TRIAL');

UPDATE fb_user_subscription
SET entitlements = jsonb_set(entitlements, '{features}',
        (entitlements -> 'features') || '["CONTRACTS","FILMS","ALBUM_PROOFING","WATERMARK","CREW_PAYOUTS","GEAR_COSTS"]'::jsonb)
WHERE plan_code IN ('SOLO', 'STUDIO', 'BUSINESS', 'FREE_TRIAL');

UPDATE fb_user_subscription
SET entitlements = jsonb_set(entitlements, '{features}', (entitlements -> 'features') || '["INSIGHTS"]'::jsonb)
WHERE plan_code IN ('STUDIO', 'BUSINESS', 'FREE_TRIAL');

UPDATE fb_user_subscription
SET entitlements = jsonb_set(entitlements, '{features}', (entitlements -> 'features') || '["CUSTOM_DOMAIN"]'::jsonb)
WHERE plan_code = 'BUSINESS';
