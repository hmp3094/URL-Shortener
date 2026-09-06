# Phase 1 Data Model: Custom Alias on Link Creation

## Entity: ShortLink (existing — extended constraints, no new fields)

No new columns. The existing `short_code` column now accepts a wider value produced by two
possible origins (auto-generated or caller-supplied); the entity itself doesn't record which,
since a short link behaves identically either way once created (spec FR-008).

| Column | Before | After | Notes |
|---|---|---|---|
| `short_code` | `VARCHAR(6)`, `CHECK (short_code ~ '^[a-z0-9]{6}$')` | `VARCHAR(32)`, `CHECK (short_code ~ '^[a-z0-9_-]{3,32}$')` | Widened to fit aliases; auto-generated 6-char codes still match the new pattern unchanged |

All other columns (`id`, `long_url`, `created_at`, `click_count`, `last_accessed_at`,
`expires_at`) are unchanged.

### Migration

`V4__widen_short_code_for_custom_alias.sql`:

```sql
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
```

## Validation Rules (`CustomAliasValidator`, checked in this order)

1. Length: 3–32 characters inclusive.
2. Character set: `a`–`z`, `A`–`Z`, `0`–`9`, `-`, `_` only.
3. Not a reserved name (case-insensitive): `api`, `actuator`, `health`, `error`, `swagger-ui`,
   `v3`.

A violation of 1 or 2 throws `InvalidAliasException` naming the specific rule; a violation of 3
throws the same exception with a reserved-name message — both map to `400 VALIDATION_ERROR`,
distinct from the `409 ALIAS_TAKEN` conflict raised later at persistence time (spec User Story
3, acceptance scenario 3: reserved is a different reason than taken, not a different status
family than other format errors).

Availability (uniqueness against existing rows) is *not* checked by the validator — it's
resolved atomically by the database at insert time (Research §2), so the validator only ever
rules out shapes that could never be legal regardless of what else exists.

## New Exceptions → Error Responses

| Exception | HTTP Status | `error` code | Raised when |
|---|---|---|---|
| `InvalidAliasException` | 400 | `VALIDATION_ERROR` | Alias fails length, charset, or reserved-name check |
| `AliasAlreadyTakenException` | 409 | `ALIAS_TAKEN` | The requested alias already has a live mapping (won the DB-level conflict) |
| `DestinationAlreadyShortenedException` | 409 | `URL_ALREADY_SHORTENED` | The destination URL already has a live mapping under a different code, and the caller supplied an alias (FR-009) |

All three follow the existing `ErrorResponse` shape (`error`, `message`, `timestamp`) via new
`@ExceptionHandler` methods in `ApiExceptionHandler`, consistent with every existing error path.

## Service Layer

`ShortLinkService` gains an overload:

```text
create(String longUrl, Long expiresInSeconds, String alias)
```

- `alias == null` → delegates to the existing `create(longUrl, expiresInSeconds)` path
  unchanged (FR-002: zero behavior change for callers that don't supply one).
- `alias != null` → normalizes to lowercase, calls `ShortLinkRepository.insertWithAlias(...)`
  (Research §2) instead of `insertIfLongUrlAbsent`, and translates its two possible failure
  modes into `AliasAlreadyTakenException` / `DestinationAlreadyShortenedException`.

## Repository Layer

New method alongside the existing `insertIfLongUrlAbsent`:

```text
insertWithAlias(id, shortCode, longUrl, createdAt, expiresAt) -> Optional<ShortLink>
```

`INSERT ... ON CONFLICT (short_code) DO NOTHING RETURNING *` — empty result means the alias was
taken; an uncaught constraint violation on `uq_short_links_long_url` means the URL already has a
live link (see Research §2 for why one statement can distinguish both).

## API Layer

`CreateLinkRequest` gains one new optional field: `alias` (`String`, no `@NotBlank` — absence is
valid and means "auto-generate," matching how `expiresInSeconds` is already optional). Format
validation (length/charset) happens in `CustomAliasValidator`, not bean-validation annotations,
since the reserved-word check can't be expressed as a single annotation and every alias rule
should report through the same validator/exception path.

`LinkResponse` is unchanged in shape — a custom-alias link's `shortCode` is just a string, same
as an auto-generated one (FR-008).
