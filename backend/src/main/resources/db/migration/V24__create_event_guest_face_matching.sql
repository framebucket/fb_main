CREATE TABLE fb_event_guest
(
    id                    UUID         PRIMARY KEY,
    event_id              UUID         NOT NULL REFERENCES fb_event_booking (id) ON DELETE CASCADE,
    full_name             VARCHAR(120) NOT NULL,
    mobile_number         VARCHAR(13)  NOT NULL,
    face_consent_at       TIMESTAMPTZ  NOT NULL,
    whatsapp_consent_at   TIMESTAMPTZ  NOT NULL,
    deletion_token_hash   VARCHAR(64)  NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_fb_event_guest_event_mobile UNIQUE (event_id, mobile_number)
);

CREATE TABLE fb_event_guest_photo_delivery
(
    id               UUID         PRIMARY KEY,
    guest_id         UUID         NOT NULL REFERENCES fb_event_guest (id) ON DELETE CASCADE,
    photo_id         VARCHAR(128) NOT NULL,
    photo_number     INTEGER,
    delivery_status  VARCHAR(20)  NOT NULL,
    failure_detail   VARCHAR(500),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at          TIMESTAMPTZ,
    attempt_count    INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_fb_guest_photo_delivery_guest_photo UNIQUE (guest_id, photo_id)
);
