ALTER TABLE fb_user
    ADD COLUMN sub_id VARCHAR(512);

WITH numbered_customers AS (
    SELECT id,
           photographer_id,
           ROW_NUMBER() OVER (PARTITION BY photographer_id ORDER BY created_at, id) - 1 AS sequence_number
    FROM fb_customer
)
UPDATE fb_customer customer_row
SET customer_code = COALESCE(
                         NULLIF(UPPER(REGEXP_REPLACE(NULLIF(BTRIM(photographer.studio_name), ''), '[^A-Za-z0-9]', '', 'g')), ''),
                         NULLIF(UPPER(REGEXP_REPLACE(photographer.full_name, '[^A-Za-z0-9]', '', 'g')), ''),
                         'STUDIO'
                     ) || '_' || LPAD(numbered_customers.sequence_number::TEXT, 3, '0')
FROM numbered_customers
JOIN fb_user photographer ON photographer.id = numbered_customers.photographer_id
WHERE numbered_customers.id = customer_row.id;

WITH numbered_events AS (
    SELECT id,
           photographer_id,
           ROW_NUMBER() OVER (PARTITION BY photographer_id ORDER BY created_at, id) - 1 AS sequence_number
    FROM fb_event_booking
)
UPDATE fb_event_booking booking_row
SET event_code = COALESCE(
                     NULLIF(UPPER(REGEXP_REPLACE(NULLIF(BTRIM(photographer.studio_name), ''), '[^A-Za-z0-9]', '', 'g')), ''),
                     NULLIF(UPPER(REGEXP_REPLACE(photographer.full_name, '[^A-Za-z0-9]', '', 'g')), ''),
                     'STUDIO'
                 ) || '_' || LPAD(numbered_events.sequence_number::TEXT, 5, '0')
FROM numbered_events
JOIN fb_user photographer ON photographer.id = numbered_events.photographer_id
WHERE numbered_events.id = booking_row.id;
