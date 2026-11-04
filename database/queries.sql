-- Table definition is managed by Flyway in:
-- backend/src/main/resources/db/migration/V1__create_photographer_users.sql
-- backend/src/main/resources/db/migration/V2__rename_user_and_add_subscriptions.sql

-- Find an account for login. Compare case-insensitively and return the hash
-- only to the backend authentication layer; never return password_hash to React.
SELECT id, full_name, studio_name, email, password_hash, active, created_at
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
    (id, full_name, studio_name, email, password_hash, active, created_at)
VALUES
    (:id, :full_name, :studio_name, LOWER(:email), :password_hash, TRUE, CURRENT_TIMESTAMP);

-- Return the signed-in photographer's safe profile (no password hash).
SELECT id, full_name, studio_name, email, subscription_id, created_at
FROM fb_user
WHERE id = :id
  AND active = TRUE;

-- Deactivate an account without deleting its event/audit references.
UPDATE fb_user
SET active = FALSE
WHERE id = :id;

-- List plans available for new subscriptions.
SELECT id, plan_code, plan_name, description, price_amount, currency_code, billing_cycle
FROM fb_subscription
WHERE active = TRUE
ORDER BY price_amount, plan_name;

-- Assign an active plan to an existing photographer.
UPDATE fb_user AS photographer
SET subscription_id = plan.id
FROM fb_subscription AS plan
WHERE photographer.id = :user_id
  AND plan.id = :subscription_id
  AND plan.active = TRUE;
