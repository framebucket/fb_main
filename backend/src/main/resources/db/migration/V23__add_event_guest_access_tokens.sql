ALTER TABLE fb_event_booking
    ADD COLUMN guest_access_token VARCHAR(64);

UPDATE fb_event_booking
SET guest_access_token = REPLACE(gen_random_uuid()::TEXT, '-', '')
WHERE guest_access_token IS NULL;

ALTER TABLE fb_event_booking
    ALTER COLUMN guest_access_token SET NOT NULL;

CREATE UNIQUE INDEX ux_fb_event_booking_guest_access_token
    ON fb_event_booking (guest_access_token);
