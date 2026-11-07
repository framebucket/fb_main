-- Normalize existing Indian numbers to the same 10-digit representation used by the application.
UPDATE fb_user
SET mobile_number = SUBSTRING(mobile_number FROM 4)
WHERE mobile_number ~ '^\+91[0-9]{10}$';

UPDATE fb_customer
SET mobile_number = SUBSTRING(mobile_number FROM 4)
WHERE mobile_number ~ '^\+91[0-9]{10}$';

UPDATE fb_customer_login_otp
SET mobile_number = SUBSTRING(mobile_number FROM 4)
WHERE mobile_number ~ '^\+91[0-9]{10}$';
