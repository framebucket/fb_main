-- Table definition is managed by Flyway in:
-- backend/src/main/resources/db/migration/V1__create_photographer_users.sql

-- Find an account for login. Compare case-insensitively and return the hash
-- only to the backend authentication layer; never return password_hash to React.
SELECT id, full_name, studio_name, email, password_hash, active, created_at
FROM photographer_users
WHERE LOWER(email) = LOWER(:email)
  AND active = TRUE;

-- Check whether an email is already registered before inserting.
SELECT EXISTS (
    SELECT 1
    FROM photographer_users
    WHERE LOWER(email) = LOWER(:email)
);

-- Insert is normally performed by JPA after BCrypt hashing the password.
-- :password_hash must be a BCrypt hash, never a plain-text password.
INSERT INTO photographer_users
    (id, full_name, studio_name, email, password_hash, active, created_at)
VALUES
    (:id, :full_name, :studio_name, LOWER(:email), :password_hash, TRUE, CURRENT_TIMESTAMP);

-- Return the signed-in photographer's safe profile (no password hash).
SELECT id, full_name, studio_name, email, created_at
FROM photographer_users
WHERE id = :id
  AND active = TRUE;

-- Deactivate an account without deleting its event/audit references.
UPDATE photographer_users
SET active = FALSE
WHERE id = :id;
