ALTER TABLE photographer_users RENAME TO fb_user;

ALTER INDEX ux_photographer_users_email_ci RENAME TO ux_fb_user_email_ci;

CREATE TABLE fb_subscription
(
    id             UUID            PRIMARY KEY,
    plan_code      VARCHAR(40)     NOT NULL,
    plan_name      VARCHAR(100)    NOT NULL,
    description    VARCHAR(500),
    price_amount   NUMERIC(10, 2)  NOT NULL CHECK (price_amount >= 0),
    currency_code  VARCHAR(3)      NOT NULL,
    billing_cycle  VARCHAR(20)     NOT NULL CHECK (billing_cycle IN ('MONTHLY', 'YEARLY', 'LIFETIME')),
    active         BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ux_fb_subscription_plan_code UNIQUE (plan_code)
);

ALTER TABLE fb_user
    ADD COLUMN subscription_id UUID,
    ADD CONSTRAINT fk_fb_user_subscription
        FOREIGN KEY (subscription_id)
        REFERENCES fb_subscription (id)
        ON DELETE SET NULL;

CREATE INDEX ix_fb_user_subscription_id
    ON fb_user (subscription_id);
