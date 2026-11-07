ALTER TABLE fb_customer
    ALTER COLUMN customer_code TYPE VARCHAR(200);

WITH numbered_customers AS (
    SELECT id,
           photographer_id,
           ROW_NUMBER() OVER (PARTITION BY photographer_id ORDER BY created_at, id) - 1 AS sequence_number
    FROM fb_customer
)
UPDATE fb_customer customer_row
SET customer_code = COALESCE(NULLIF(BTRIM(photographer.studio_name), ''), photographer.full_name)
                     || '_' || LPAD(numbered_customers.sequence_number::TEXT, 3, '0')
FROM numbered_customers
JOIN fb_user photographer ON photographer.id = numbered_customers.photographer_id
WHERE numbered_customers.id = customer_row.id;

UPDATE fb_user photographer
SET customer_sequence = customer_counts.customer_count
FROM (
    SELECT photographer_id, COUNT(*)::INTEGER AS customer_count
    FROM fb_customer
    GROUP BY photographer_id
) customer_counts
WHERE customer_counts.photographer_id = photographer.id;

ALTER TABLE fb_event_booking
    DROP CONSTRAINT IF EXISTS fb_event_booking_event_code_key,
    ALTER COLUMN event_code TYPE VARCHAR(200);

WITH numbered_events AS (
    SELECT id,
           photographer_id,
           ROW_NUMBER() OVER (PARTITION BY photographer_id ORDER BY created_at, id) - 1 AS sequence_number
    FROM fb_event_booking
)
UPDATE fb_event_booking booking_row
SET event_code = COALESCE(NULLIF(BTRIM(photographer.studio_name), ''), photographer.full_name)
                  || '_' || LPAD(numbered_events.sequence_number::TEXT, 5, '0')
FROM numbered_events
JOIN fb_user photographer ON photographer.id = numbered_events.photographer_id
WHERE numbered_events.id = booking_row.id;

UPDATE fb_user photographer
SET event_sequence = event_counts.event_count
FROM (
    SELECT photographer_id, COUNT(*)::INTEGER AS event_count
    FROM fb_event_booking
    GROUP BY photographer_id
) event_counts
WHERE event_counts.photographer_id = photographer.id;

ALTER TABLE fb_event_booking
    ADD CONSTRAINT ux_fb_event_booking_photographer_code UNIQUE (photographer_id, event_code);
