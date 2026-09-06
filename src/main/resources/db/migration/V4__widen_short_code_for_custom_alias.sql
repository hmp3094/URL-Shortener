-- Rollback plan (manual — Flyway's free edition has no automatic down-migrations):
-- to revert this migration, run (only safe if no alias longer than 6 characters exists):
--   ALTER TABLE short_links ALTER COLUMN short_code TYPE VARCHAR(6);
--   ALTER TABLE short_links DROP CONSTRAINT chk_short_links_short_code_format;
--   ALTER TABLE short_links ADD CONSTRAINT chk_short_links_short_code_format
--     CHECK (short_code ~ '^[a-z0-9]{6}$');

ALTER TABLE short_links ALTER COLUMN short_code TYPE VARCHAR(32);

ALTER TABLE short_links DROP CONSTRAINT chk_short_links_short_code_format;
ALTER TABLE short_links ADD CONSTRAINT chk_short_links_short_code_format
    CHECK (short_code ~ '^[a-z0-9_-]{3,32}$');
