-- Plan limits for editing presets (AI picks one per photo) and reference album designs (AI follows them per
-- sheet). References are a Studio and Business feature: 0 on Free and Solo shows an upgrade prompt.
UPDATE fb_user_subscription
SET entitlements = jsonb_set(jsonb_set(entitlements, '{limits,editingPresets}',
        to_jsonb(CASE plan_code WHEN 'FREE' THEN 3 WHEN 'SOLO' THEN 5 WHEN 'STUDIO' THEN 10 WHEN 'BUSINESS' THEN 25
                                WHEN 'FREE_TRIAL' THEN 10 ELSE 3 END), true),
        '{limits,albumReferences}',
        to_jsonb(CASE plan_code WHEN 'STUDIO' THEN 50 WHEN 'BUSINESS' THEN 100 WHEN 'FREE_TRIAL' THEN 50 ELSE 0 END), true),
    updated_at = CURRENT_TIMESTAMP
WHERE entitlements ? 'limits';
