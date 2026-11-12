-- Subscription plans v2: data-driven entitlements, subscription status tracking, plan requests,
-- and an admin audit trail.

ALTER TABLE fb_user_subscription
    ADD COLUMN tagline             VARCHAR(160),
    ADD COLUMN yearly_price_amount NUMERIC(10, 2) CHECK (yearly_price_amount IS NULL OR yearly_price_amount >= 0),
    ADD COLUMN sort_order          INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN highlighted         BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN public_plan         BOOLEAN     NOT NULL DEFAULT TRUE,
    ADD COLUMN entitlements        JSONB       NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Feature codes understood by the backend (com.framebucket.subscription.Feature):
-- CRM, QUOTATIONS, TEAM, GALLERY, BILLING, REMINDERS, PORTFOLIO, E_ALBUM, FACE_RECOGNITION,
-- PAID_DOWNLOADS, CUSTOM_BRANDING, WHATSAPP_AUTOMATION, PRIORITY_SUPPORT.
-- Limits: null = unlimited.
INSERT INTO fb_user_subscription
    (id, plan_code, plan_name, description, tagline, price_amount, yearly_price_amount, currency_code, billing_cycle,
     active, public_plan, highlighted, sort_order, trial_days, customer_order_tracking, ai_image_delivery,
     quotation_calendar, data_module_enabled, raw_photo_selection, max_storage_gb, entitlements)
VALUES
    ('10000000-0000-4000-8000-000000000010', 'FREE', 'Free',
     'Run enquiries, quotations and bookings for a handful of clients.', 'For getting started', 0.00, 0.00, 'INR', 'MONTHLY',
     TRUE, TRUE, FALSE, 10, 0, TRUE, FALSE, TRUE, TRUE, TRUE, 5,
     '{"features":["CRM","QUOTATIONS","TEAM","GALLERY"],
       "limits":{"customers":25,"upcomingEvents":3,"teamMembers":1,"storageGb":5,"whatsappMessagesPerMonth":0}}'),
    ('10000000-0000-4000-8000-000000000011', 'SOLO', 'Solo',
     'Everything a solo photographer needs: unlimited bookings, billing, galleries and e-albums.', 'For solo photographers', 799.00, 7990.00, 'INR', 'MONTHLY',
     TRUE, TRUE, FALSE, 20, 0, TRUE, FALSE, TRUE, TRUE, TRUE, 100,
     '{"features":["CRM","QUOTATIONS","TEAM","GALLERY","BILLING","REMINDERS","PORTFOLIO","E_ALBUM","WHATSAPP_AUTOMATION"],
       "limits":{"customers":null,"upcomingEvents":null,"teamMembers":3,"storageGb":100,"whatsappMessagesPerMonth":100}}'),
    ('10000000-0000-4000-8000-000000000012', 'STUDIO', 'Studio',
     'For growing studios: AI face recognition, paid downloads, your own branding and a bigger team.', 'Most popular', 1699.00, 16990.00, 'INR', 'MONTHLY',
     TRUE, TRUE, TRUE, 30, 0, TRUE, TRUE, TRUE, TRUE, TRUE, 500,
     '{"features":["CRM","QUOTATIONS","TEAM","GALLERY","BILLING","REMINDERS","PORTFOLIO","E_ALBUM","WHATSAPP_AUTOMATION","FACE_RECOGNITION","PAID_DOWNLOADS","CUSTOM_BRANDING"],
       "limits":{"customers":null,"upcomingEvents":null,"teamMembers":10,"storageGb":500,"whatsappMessagesPerMonth":500}}'),
    ('10000000-0000-4000-8000-000000000013', 'BUSINESS', 'Business',
     'For busy wedding studios and agencies: 2 TB, large teams and priority support.', 'For large studios', 3499.00, 34990.00, 'INR', 'MONTHLY',
     TRUE, TRUE, FALSE, 40, 0, TRUE, TRUE, TRUE, TRUE, TRUE, 2048,
     '{"features":["CRM","QUOTATIONS","TEAM","GALLERY","BILLING","REMINDERS","PORTFOLIO","E_ALBUM","WHATSAPP_AUTOMATION","FACE_RECOGNITION","PAID_DOWNLOADS","CUSTOM_BRANDING","PRIORITY_SUPPORT"],
       "limits":{"customers":null,"upcomingEvents":null,"teamMembers":25,"storageGb":2048,"whatsappMessagesPerMonth":2000}}')
ON CONFLICT (plan_code) DO NOTHING;

-- The free trial now unlocks everything in Studio for 14 days.
UPDATE fb_user_subscription
SET plan_name = 'Free trial (Studio)',
    description = '14 days of every Studio feature.',
    tagline = 'Try every Studio feature',
    trial_days = 14,
    public_plan = FALSE,
    sort_order = 0,
    max_storage_gb = 500,
    entitlements = (SELECT entitlements FROM fb_user_subscription WHERE plan_code = 'STUDIO')
WHERE plan_code = 'FREE_TRIAL';

-- Retire the old storage-only plans and move anyone on them to the closest new plan.
UPDATE fb_user AS u
SET subscription_id = (SELECT id FROM fb_user_subscription WHERE plan_code = CASE old.plan_code
        WHEN 'BRONZE' THEN 'SOLO' WHEN 'SILVER' THEN 'SOLO' WHEN 'GOLD' THEN 'STUDIO' ELSE 'BUSINESS' END)
FROM fb_user_subscription AS old
WHERE u.subscription_id = old.id
  AND old.plan_code IN ('BRONZE', 'SILVER', 'GOLD', 'PLATINUM');

UPDATE fb_user_subscription
SET active = FALSE, public_plan = FALSE
WHERE plan_code IN ('BRONZE', 'SILVER', 'GOLD', 'PLATINUM');

ALTER TABLE fb_user
    ADD COLUMN plan_expires_at  TIMESTAMPTZ,
    ADD COLUMN billing_cycle    VARCHAR(10) CHECK (billing_cycle IS NULL OR billing_cycle IN ('MONTHLY', 'YEARLY')),
    ADD COLUMN last_login_at    TIMESTAMPTZ,
    ADD COLUMN suspended_reason VARCHAR(500),
    ADD COLUMN admin_notes      TEXT;

-- Every plan change (trial start, upgrade, renewal, downgrade, admin override).
CREATE TABLE fb_subscription_event
(
    id              UUID           PRIMARY KEY,
    photographer_id UUID           NOT NULL REFERENCES fb_user (id) ON DELETE CASCADE,
    action          VARCHAR(30)    NOT NULL,
    from_plan_code  VARCHAR(40),
    to_plan_code    VARCHAR(40),
    billing_cycle   VARCHAR(10),
    period_end      TIMESTAMPTZ,
    amount          NUMERIC(10, 2),
    reference       VARCHAR(120),
    note            VARCHAR(500),
    actor           VARCHAR(320)   NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_fb_subscription_event_photographer ON fb_subscription_event (photographer_id, created_at DESC);

-- A photographer asks for a plan; the admin collects payment and approves it
-- (bridge until a payment gateway is integrated).
CREATE TABLE fb_plan_request
(
    id              UUID          PRIMARY KEY,
    photographer_id UUID          NOT NULL REFERENCES fb_user (id) ON DELETE CASCADE,
    plan_code       VARCHAR(40)   NOT NULL,
    billing_cycle   VARCHAR(10)   NOT NULL CHECK (billing_cycle IN ('MONTHLY', 'YEARLY')),
    status          VARCHAR(10)   NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    message         VARCHAR(500),
    admin_note      VARCHAR(500),
    handled_by      VARCHAR(320),
    handled_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX ux_fb_plan_request_one_pending ON fb_plan_request (photographer_id) WHERE status = 'PENDING';

CREATE TABLE fb_admin_audit
(
    id          UUID          PRIMARY KEY,
    admin_email VARCHAR(320)  NOT NULL,
    action      VARCHAR(60)   NOT NULL,
    target_type VARCHAR(40),
    target_id   VARCHAR(80),
    details     JSONB,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_fb_admin_audit_created ON fb_admin_audit (created_at DESC);
