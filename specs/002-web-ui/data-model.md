# Phase 1 Data Model: Web UI for Shortening and Stats Lookup

No new entities, columns, or persisted state. This feature introduces no schema of its own — the
page is a pure client of two shapes the API already defines in `docs/api.yaml`:

- **`CreateLinkRequest`** (existing) — the shortening form collects exactly its fields: `url`,
  optional `alias`, and an expiration choice translated to `expiresInSeconds` before the request
  is sent (see `research.md` §3).
- **`LinkResponse`** (existing) — the successful-shortening view displays `shortCode`/`shortUrl`
  directly from this response; nothing is computed or stored beyond what's already returned.
- **`LinkStatsResponse`** (existing) — the stats-lookup view displays `clickCount`, `createdAt`,
  `lastAccessedAt`, and `expiresAt` directly from this response.
- **`ErrorResponse`** (existing) — every failure state the page shows is a direct translation of
  this shape's `error` field (see `research.md` §4); no new error taxonomy is introduced.

## Validation

All validation (URL format/safety, alias format/availability, expiration bounds) already happens
server-side, exactly as it does for any other API caller — the page performs only the minimal
client-side check needed for a responsive form (e.g. not sending an empty URL field), never a
second, independent copy of a rule the server already enforces. The server's response is always
the source of truth for whether a submission succeeded.
