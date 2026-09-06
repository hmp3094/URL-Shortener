# Quickstart: Validating Custom Alias on Link Creation

Prerequisites: local stack running per `docs/getting-started.md` (Spring Boot app + PostgreSQL),
migration `V4__widen_short_code_for_custom_alias.sql` applied.

## 1. Create a link with an available alias (User Story 1)

```sh
curl -i -X POST http://localhost:8080/api/links \
  -H "Content-Type: application/json" \
  -d '{"url": "https://example.com/summer-catalog", "alias": "summer-sale"}'
```

Expected: `201 Created`, `Location` header and `shortCode` both `summer-sale`. Then:

```sh
curl -i http://localhost:8080/summer-sale
```

Expected: `302 Found` redirecting to `https://example.com/summer-catalog`. See
[data-model.md](./data-model.md) for the full request/response shapes and
[contracts/api-changes.yaml](./contracts/api-changes.yaml) for the contract.

## 2. Request an alias that's already taken (User Story 2)

```sh
curl -i -X POST http://localhost:8080/api/links \
  -H "Content-Type: application/json" \
  -d '{"url": "https://example.com/different-page", "alias": "summer-sale"}'
```

Expected: `409 Conflict`, `error: ALIAS_TAKEN`. The original mapping (step 1) must still resolve
unchanged — re-run the `curl -i http://localhost:8080/summer-sale` check above.

## 3. Request an alias for a URL that's already shortened (FR-009)

```sh
curl -s -X POST http://localhost:8080/api/links -H "Content-Type: application/json" \
  -d '{"url": "https://example.com/already-linked"}'   # no alias — get an auto code first
curl -i -X POST http://localhost:8080/api/links -H "Content-Type: application/json" \
  -d '{"url": "https://example.com/already-linked", "alias": "again"}'
```

Expected: second request returns `409 Conflict`, `error: URL_ALREADY_SHORTENED`, and no mapping
is created for `again`.

## 4. Invalid alias shapes (User Story 3)

| Request `alias` | Expected |
|---|---|
| `"ab"` (2 chars) | `400 VALIDATION_ERROR` — length |
| 33 characters | `400 VALIDATION_ERROR` — length |
| `"has a space"` | `400 VALIDATION_ERROR` — character set |
| `"actuator"` | `400 VALIDATION_ERROR` — reserved name |

## 5. Backward compatibility (FR-002)

```sh
curl -i -X POST http://localhost:8080/api/links \
  -H "Content-Type: application/json" \
  -d '{"url": "https://example.com/no-alias-here"}'
```

Expected: identical behavior to today — `201 Created` with an auto-generated 6-character
`shortCode`, no `alias` field required or referenced.

## 6. Case-insensitivity (FR-006)

Create with alias `"Promo2026"`, then confirm `GET /promo2026` (lowercase) and
`GET /PROMO2026` (uppercase) both resolve to the same mapping, and a second creation attempt
with alias `"PROMO2026"` returns `409 ALIAS_TAKEN`.

## Redirect-path latency re-check (Reliability & Data Standards)

Before merging, re-run the load test tooling documented in `docs/performance.md` against the
widened `GET /{code}` route and record fresh p50/p95 numbers next to the existing baseline —
required because this feature changes that route's regex (see
[research.md](./research.md) §5).
