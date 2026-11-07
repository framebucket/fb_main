-- Table definition is managed by Flyway in:
-- backend/src/main/resources/db/migration/V1__create_photographer_users.sql
-- backend/src/main/resources/db/migration/V2__rename_user_and_add_subscriptions.sql
-- backend/src/main/resources/db/migration/V3__add_email_verification.sql
-- backend/src/main/resources/db/migration/V4__seed_subscription_plans_and_trial.sql
-- backend/src/main/resources/db/migration/V7__create_customers.sql
-- backend/src/main/resources/db/migration/V8__create_customer_login_otp.sql
-- backend/src/main/resources/db/migration/V9__normalize_indian_mobile_numbers.sql

-- Find an account for login. Compare case-insensitively and return the hash
-- only to the backend authentication layer; never return password_hash to React.
SELECT id, full_name, studio_name, email, password_hash, active, email_verified,
       subscription_id, trial_ends_at, created_at
FROM fb_user
WHERE LOWER(email) = LOWER(:email)
  AND active = TRUE;

-- Check whether an email is already registered before inserting.
SELECT EXISTS (
    SELECT 1
    FROM fb_user
    WHERE LOWER(email) = LOWER(:email)
);

-- Insert is normally performed by JPA after BCrypt hashing the password.
-- :password_hash must be a BCrypt hash, never a plain-text password.
INSERT INTO fb_user
    (id, full_name, studio_name, email, password_hash, active, email_verified,
     subscription_id, trial_ends_at, created_at)
VALUES
    (:id, :full_name, :studio_name, LOWER(:email), :password_hash, TRUE, FALSE,
     (SELECT id FROM fb_user_subscription WHERE plan_code = 'FREE_TRIAL' AND active = TRUE),
     CURRENT_TIMESTAMP + INTERVAL '7 days', CURRENT_TIMESTAMP);

-- Return the signed-in photographer's safe profile (no password hash).
SELECT id, full_name, studio_name, email, email_verified, subscription_id, trial_ends_at, created_at
FROM fb_user
WHERE id = :id
  AND active = TRUE;

-- Deactivate an account without deleting its event/audit references.
UPDATE fb_user
SET active = FALSE
WHERE id = :id;

-- List plans available for new subscriptions.
SELECT id, plan_code, plan_name, description, price_amount, currency_code, billing_cycle,
       data_module_enabled, raw_photo_selection, max_storage_gb
FROM fb_user_subscription
WHERE active = TRUE
ORDER BY price_amount, plan_name;

-- Assign an active plan to an existing photographer.
UPDATE fb_user AS photographer
SET subscription_id = plan.id
FROM fb_user_subscription AS plan
WHERE photographer.id = :user_id
  AND plan.id = :subscription_id
  AND plan.active = TRUE;

-- The API inserts customer rows using the authenticated photographer ID.
-- Events and revenue are initialized to zero and will be updated by the Event module.
INSERT INTO fb_customer
    (id, photographer_id, name, email, mobile_number, date_of_birth, address,
     events_booked, total_revenue_generated, created_at)
VALUES
    (:id, :photographer_id, :name, LOWER(:email), :mobile_number, :date_of_birth, :address,
     0, 0.00, CURRENT_TIMESTAMP);

-- List only the current photographer's customer records.
SELECT id, name, email, mobile_number, date_of_birth, address,
       events_booked, total_revenue_generated, created_at
FROM fb_customer
WHERE photographer_id = :photographer_id
ORDER BY created_at DESC;

-- Customer sign-in codes are stored as keyed hashes and expire after 10 minutes.
-- The API sends the same raw code to each registered email for this mobile number and to the mobile sender.
SELECT id, mobile_number, code_hash, expires_at, consumed_at, attempt_count, created_at
FROM fb_customer_login_otp
WHERE mobile_number = :mobile_number
  AND consumed_at IS NULL
  AND expires_at > CURRENT_TIMESTAMP
ORDER BY created_at DESC
LIMIT 1;
