ALTER TABLE fb_user
    ADD COLUMN customer_sequence INTEGER NOT NULL DEFAULT 0 CHECK (customer_sequence >= 0),
    ADD COLUMN event_sequence INTEGER NOT NULL DEFAULT 0 CHECK (event_sequence >= 0);

ALTER TABLE fb_customer
    ADD COLUMN customer_code VARCHAR(80);

WITH numbered_customers AS (
    SELECT id,
           photographer_id,
           ROW_NUMBER() OVER (PARTITION BY photographer_id ORDER BY created_at, id) - 1 AS sequence_number
    FROM fb_customer
)
UPDATE fb_customer customer
SET customer_code = numbered_customers.photographer_id::TEXT || '_' || LPAD(numbered_customers.sequence_number::TEXT, 3, '0')
FROM numbered_customers
WHERE numbered_customers.id = customer.id;

UPDATE fb_user photographer
SET customer_sequence = customer_counts.customer_count
FROM (
    SELECT photographer_id, COUNT(*)::INTEGER AS customer_count
    FROM fb_customer
    GROUP BY photographer_id
) customer_counts
WHERE customer_counts.photographer_id = photographer.id;

ALTER TABLE fb_customer
    ALTER COLUMN customer_code SET NOT NULL,
    ADD CONSTRAINT ux_fb_customer_id_photographer UNIQUE (id, photographer_id),
    ADD CONSTRAINT ux_fb_customer_photographer_code UNIQUE (photographer_id, customer_code);

CREATE TABLE fb_event_list
(
    id              UUID PRIMARY KEY,
    photographer_id UUID REFERENCES fb_user (id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    is_default      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_fb_event_list_default_owner CHECK (photographer_id IS NOT NULL OR is_default)
);

CREATE UNIQUE INDEX ux_fb_event_list_default_name
    ON fb_event_list (LOWER(name))
    WHERE photographer_id IS NULL;

CREATE UNIQUE INDEX ux_fb_event_list_photographer_name
    ON fb_event_list (photographer_id, LOWER(name))
    WHERE photographer_id IS NOT NULL;

INSERT INTO fb_event_list (id, photographer_id, name, is_default)
VALUES
    ('00000000-0000-4000-8000-000000000001', NULL, 'Wedding', TRUE),
    ('00000000-0000-4000-8000-000000000002', NULL, 'Engagement', TRUE),
    ('00000000-0000-4000-8000-000000000003', NULL, 'Naming Ceremony', TRUE);

CREATE TABLE fb_event_booking
(
    id              UUID PRIMARY KEY,
    event_code      VARCHAR(100) NOT NULL UNIQUE,
    photographer_id UUID NOT NULL REFERENCES fb_user (id) ON DELETE CASCADE,
    event_date      DATE NOT NULL,
    event_type_id   UUID NOT NULL REFERENCES fb_event_list (id),
    customer_id     UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ux_fb_event_booking_photographer_date UNIQUE (photographer_id, event_date),
    CONSTRAINT fk_fb_event_booking_customer_owner
        FOREIGN KEY (customer_id, photographer_id)
        REFERENCES fb_customer (id, photographer_id)
        ON DELETE CASCADE
);

CREATE INDEX ix_fb_event_booking_photographer_event_date
    ON fb_event_booking (photographer_id, event_date);

CREATE INDEX ix_fb_event_booking_customer_date
    ON fb_event_booking (customer_id, event_date DESC);
