-- Rollback plan (manual — Flyway's free edition has no automatic down-migrations):
-- to revert this migration, run:
--   ALTER TABLE short_links DROP COLUMN click_count;
--   ALTER TABLE short_links DROP COLUMN last_accessed_at;

ALTER TABLE short_links
    ADD COLUMN click_count      BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN last_accessed_at TIMESTAMPTZ;
