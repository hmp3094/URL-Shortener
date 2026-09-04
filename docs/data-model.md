# Data Model

## Table: `short_links`

| Field | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `BIGINT` | Primary key, from `short_link_seq` | Source value for the short code's encoding |
| `short_code` | `VARCHAR(6)` | `NOT NULL`, `UNIQUE`, `CHECK (short_code ~ '^[a-z0-9]{6}$')` | Lowercase alphanumeric, stored/compared case-insensitively |
| `long_url` | `TEXT` | `NOT NULL`, `UNIQUE`, max length 2048 (app-level validation) | Exact string as submitted, after whitespace trimming only — no other normalization. Used directly as the redirect target |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL`, `DEFAULT now()` | Set once at insert; never changes |
| `click_count` | `BIGINT` | `NOT NULL`, `DEFAULT 0` | Incremented atomically on every successful redirect (see Design Decisions) |
| `last_accessed_at` | `TIMESTAMPTZ` | Nullable | `NULL` until the first redirect; updated alongside `click_count` |

### Validation (applied before any write)

- `long_url` must be non-empty and parse as a well-formed URI.
- `long_url`'s scheme must be `http` or `https` (case-insensitive scheme check only — the
  submitted URL's own casing elsewhere is preserved verbatim).
- `long_url`'s host, once DNS-resolved, must not resolve to a loopback, site-local (RFC 1918), or
  link-local address.
- `long_url` must not exceed 2048 characters (rejected outright, never truncated).
- A request failing any rule above creates no row and returns a `400` with a machine-readable
  reason (see `api.yaml`).

### Lifecycle

A short link is created once and never deleted, and its identity fields (`short_code`, `long_url`,
`created_at`) never change after creation (no expiration, no deletion, no ownership transfer —
deferred to a later feature). `click_count` and `last_accessed_at` are the one exception: they're
updated in place on every redirect. The operations against a row are insert-if-absent (creation),
read (redirect lookup, stats lookup), and increment (click tracking).

### Indexes

- Primary key index on `id` (implicit).
- Unique index on `short_code` (supports `GET /{code}` lookups — the hot path).
- Unique index on `long_url` (supports the `ON CONFLICT (long_url)` duplicate-detection insert).

## Migration

`src/main/resources/db/migration/V1__create_short_links_table.sql` creates the sequence and the
table above (minus the two click-tracking columns, added later).

**Rollback plan**: Flyway's free edition doesn't auto-apply down-migrations, so reverting this
migration is a documented manual procedure:

```sql
DROP TABLE short_links;
DROP SEQUENCE short_link_seq;
```

`src/main/resources/db/migration/V2__add_click_tracking.sql` adds `click_count` and
`last_accessed_at` to the existing table.

**Rollback plan**:

```sql
ALTER TABLE short_links DROP COLUMN click_count;
ALTER TABLE short_links DROP COLUMN last_accessed_at;
```

No other tables exist in this version — no accounts, no raw click-event log (click tracking is an
aggregate counter on the existing row, not a separate events table); authentication remains out of
scope (see `requirements.md`).
