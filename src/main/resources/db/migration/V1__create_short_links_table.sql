-- Rollback plan (manual — Flyway's free edition has no automatic down-migrations):
-- to revert this migration, run:
--   DROP TABLE short_links;
--   DROP SEQUENCE short_link_seq;

CREATE SEQUENCE short_link_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE short_links (
    id          BIGINT PRIMARY KEY DEFAULT nextval('short_link_seq'),
    short_code  VARCHAR(6) NOT NULL,
    long_url    TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_short_links_short_code UNIQUE (short_code),
    CONSTRAINT uq_short_links_long_url UNIQUE (long_url),
    CONSTRAINT chk_short_links_short_code_format CHECK (short_code ~ '^[a-z0-9]{6}$'),
    CONSTRAINT chk_short_links_long_url_length CHECK (char_length(long_url) <= 2048)
);
