ALTER TABLE fb_enquiries
    ADD COLUMN finalized_quote NUMERIC(12, 2) CHECK (finalized_quote >= 0),
    ADD COLUMN created_event_id UUID REFERENCES fb_event_booking (id) ON DELETE SET NULL,
    ADD COLUMN created_event_code VARCHAR(200);
