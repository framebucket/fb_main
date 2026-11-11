-- Festival coupons are no longer offered in the public quotation builder.
ALTER TABLE fb_quotaion
    DROP COLUMN IF EXISTS coupon_code;
