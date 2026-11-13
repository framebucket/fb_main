-- Quotes: customers now tick "Album" instead of choosing a sheet count. Each event then includes
-- album_sheets_per_event sheets at album_sheet_price (default 30 × ₹400 = ₹12,000 per event).
ALTER TABLE fb_quote_settings ADD COLUMN IF NOT EXISTS album_sheets_per_event INTEGER NOT NULL DEFAULT 30;
ALTER TABLE fb_quote_settings DROP CONSTRAINT IF EXISTS fb_quote_settings_album_sheets_per_event_check;
ALTER TABLE fb_quote_settings ADD CONSTRAINT fb_quote_settings_album_sheets_per_event_check
    CHECK (album_sheets_per_event BETWEEN 1 AND 200);
