ALTER TABLE fb_user
    ADD COLUMN trial_ends_at TIMESTAMPTZ;

ALTER TABLE fb_subscription
    ADD COLUMN trial_days INTEGER NOT NULL DEFAULT 0 CHECK (trial_days >= 0),
    ADD COLUMN customer_order_tracking BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN ai_image_delivery BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN quotation_calendar BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN data_module_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN raw_photo_selection BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN max_storage_gb BIGINT CHECK (max_storage_gb IS NULL OR max_storage_gb >= 0);

INSERT INTO fb_subscription
    (id, plan_code, plan_name, description, price_amount, currency_code, billing_cycle,
     active, trial_days, customer_order_tracking, ai_image_delivery, quotation_calendar,
     data_module_enabled, raw_photo_selection, max_storage_gb)
VALUES
    ('10000000-0000-4000-8000-000000000001', 'FREE_TRIAL', '7-day Free Trial',
     'Seven days to explore the FrameBucket photographer workspace.', 0.00, 'INR', 'MONTHLY',
     TRUE, 7, TRUE, TRUE, TRUE, TRUE, TRUE, NULL),
    ('10000000-0000-4000-8000-000000000002', 'BRONZE', 'Bronze Plan',
     'Core client and booking tools.', 599.00, 'INR', 'MONTHLY',
     TRUE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, 0),
    ('10000000-0000-4000-8000-000000000003', 'SILVER', 'Silver Plan',
     'Core tools plus RAW photo selection with up to 50 GB storage.', 799.00, 'INR', 'MONTHLY',
     TRUE, 0, TRUE, TRUE, TRUE, TRUE, TRUE, 50),
    ('10000000-0000-4000-8000-000000000004', 'GOLD', 'Gold Plan',
     'Core tools plus RAW photo selection with up to 100 GB storage.', 1299.00, 'INR', 'MONTHLY',
     TRUE, 0, TRUE, TRUE, TRUE, TRUE, TRUE, 100),
    ('10000000-0000-4000-8000-000000000005', 'PLATINUM', 'Platinum Plan',
     'Core tools plus RAW photo selection with up to 500 GB storage.', 1999.00, 'INR', 'MONTHLY',
     TRUE, 0, TRUE, TRUE, TRUE, TRUE, TRUE, 500)
ON CONFLICT (plan_code) DO UPDATE SET
    plan_name = EXCLUDED.plan_name,
    description = EXCLUDED.description,
    price_amount = EXCLUDED.price_amount,
    currency_code = EXCLUDED.currency_code,
    billing_cycle = EXCLUDED.billing_cycle,
    active = EXCLUDED.active,
    trial_days = EXCLUDED.trial_days,
    customer_order_tracking = EXCLUDED.customer_order_tracking,
    ai_image_delivery = EXCLUDED.ai_image_delivery,
    quotation_calendar = EXCLUDED.quotation_calendar,
    data_module_enabled = EXCLUDED.data_module_enabled,
    raw_photo_selection = EXCLUDED.raw_photo_selection,
    max_storage_gb = EXCLUDED.max_storage_gb;

-- Move existing accounts without an assigned plan onto the same trial as new registrations.
UPDATE fb_user AS photographer
SET subscription_id = trial.id,
    trial_ends_at = photographer.created_at + INTERVAL '7 days'
FROM fb_subscription AS trial
WHERE trial.plan_code = 'FREE_TRIAL'
  AND photographer.subscription_id IS NULL;
