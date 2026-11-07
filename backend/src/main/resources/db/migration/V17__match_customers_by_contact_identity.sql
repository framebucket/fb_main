ALTER TABLE fb_customer
    DROP CONSTRAINT ux_fb_customer_photographer_email;

CREATE UNIQUE INDEX ux_fb_customer_photographer_contact_identity
    ON fb_customer (photographer_id, lower(name), lower(email), mobile_number);
