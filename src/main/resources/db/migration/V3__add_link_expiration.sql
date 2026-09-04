-- Rollback plan (manual — Flyway's free edition has no automatic down-migrations):
-- to revert this migration, run:
--   ALTER TABLE short_links DROP COLUMN expires_at;

ALTER TABLE short_links
    ADD COLUMN expires_at TIMESTAMPTZ;
