-- =====================================================================
-- Marks a specific staff account as also having platform-operator access
-- (onboarding new restaurants, listing every restaurant), so they don't
-- have to type the shared X-Platform-Admin-Key by hand. Independent of
-- role: it's a capability on top of whatever restaurant role they hold,
-- not a new role, since the platform operator's own account is still an
-- ordinary OWNER of restaurant #1 for everyday use.
-- =====================================================================

ALTER TABLE staff_user ADD COLUMN platform_admin boolean NOT NULL DEFAULT false;

-- On a database that already has an OWNER for the default restaurant (id 1) -- i.e. everywhere this app was
-- already running before this migration -- BootstrapOwnerRunner won't run again to set the flag on it (it only
-- seeds when no OWNER exists at all). Grant it here instead, so the existing default owner gets platform access
-- without needing a fresh database.
UPDATE staff_user SET platform_admin = true WHERE restaurant_id = 1 AND role = 'OWNER';
