-- AI album auto-design (studio side) and AI wedding invitations (client portal): Studio and Business plans.
UPDATE fb_user_subscription
SET entitlements = jsonb_set(entitlements, '{features}',
        (entitlements -> 'features') || '["AI_ALBUM_DESIGN","AI_INVITATIONS"]'::jsonb),
    updated_at = CURRENT_TIMESTAMP
WHERE plan_code IN ('STUDIO', 'BUSINESS', 'FREE_TRIAL')
  AND NOT (entitlements -> 'features') @> '["AI_ALBUM_DESIGN"]'::jsonb;
