# Data Model

## Table: `short_links`

| Field | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `BIGINT` | Primary key, from `short_link_seq` | Source value for the short code's encoding |
| `short_code` | `VARCHAR(6)` | `NOT NULL`, `UNIQUE`, `CHECK (short_code ~ '^[a-z0-9]{6}$')` | Lowercase alphanumeric, stored/compared case-insensitively |
| `long_url` | `TEXT` | `NOT NULL`, `UNIQUE`, max length 2048 (app-level validation) | Exact string as submitted, after whitespace trimming only — no other normalization. Used directly as the redirect target |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL`, `DEFAULT now()` | Set once at insert; the row is otherwise immutable |

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

No state transitions. A short link is created once and never updated or deleted in this version
(no expiration, no deletion, no ownership transfer — deferred to a later feature). The only two
operations against a row are insert-if-absent (creation) and read (redirect lookup).

### Indexes

- Primary key index on `id` (implicit).
- Unique index on `short_code` (supports `GET /{code}` lookups — the hot path).
- Unique index on `long_url` (supports the `ON CONFLICT (long_url)` duplicate-detection insert).

## Migration

`src/main/resources/db/migration/V1__create_short_links_table.sql` creates the sequence and the
table above.

**Rollback plan**: Flyway's free edition doesn't auto-apply down-migrations, so reverting this
migration is a documented manual procedure:

```sql
DROP TABLE short_links;
DROP SEQUENCE short_link_seq;
```

No other tables exist in this version — no accounts, no click events; analytics and
authentication are out of scope (see `requirements.md`).
