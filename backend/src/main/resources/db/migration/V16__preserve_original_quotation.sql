ALTER TABLE fb_quotaion
    ADD COLUMN original_price NUMERIC(12, 2),
    ADD COLUMN original_quote_pdf BYTEA;

UPDATE fb_quotaion
SET original_price = final_price,
    original_quote_pdf = quote_pdf;

ALTER TABLE fb_quotaion
    ALTER COLUMN original_price SET NOT NULL,
    ALTER COLUMN original_quote_pdf SET NOT NULL,
    ADD CONSTRAINT ck_fb_quotaion_original_price_nonnegative CHECK (original_price >= 0);
