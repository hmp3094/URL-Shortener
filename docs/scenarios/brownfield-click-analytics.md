# Scenario: Brownfield — Click Analytics

**Type**: Brownfield (enhancement to existing, already-shipped code — not a new subsystem).

## Requirement

The original build covered creation and redirect only; click analytics was explicitly deferred
(see the earlier version of `requirements.md`'s Assumptions section). The ask: let a link creator
see how many times their short link has been used, and when it was last used, without adding
accounts or a separate analytics subsystem.

## Why this counts as brownfield

This touches code and data that already existed and were already in production use, rather than
standing up something new alongside them:

- `short_links` — an existing table gets two new columns via a new migration, not a new table.
- `ShortLink.java` — an existing entity gains two fields.
- `RedirectController` — an existing, already-tested endpoint's behavior changes (it now also
  records a click), on the exact code path that the redirect caching decision was built around.
- `ShortLinkService.resolve()` — an existing cache-aside method had to be reasoned about
  carefully: incrementing a counter *inside* it would silently stop firing once a code is cached,
  since `@Cacheable` skips the method body entirely on a hit. That's a real interaction between old
  code and new code, not something a greenfield build would ever have to consider.

## Decomposition

Tasks, in dependency order:

1. `V2__add_click_tracking.sql` — add `click_count` (default 0) and `last_accessed_at` (nullable)
   to `short_links`. Must come first; everything else depends on the columns existing.
2. `ShortLink.java` — add the two fields, getters, and extend the constructor.
3. `ShortLinkRepository` — add an atomic `recordClick(shortCode)` update, following the same
   native-query pattern already used for `insertIfLongUrlAbsent` (single-statement, no
   read-then-write race).
4. `ShortLinkService` — add `recordClick(code)` (a separate, uncached, `@Transactional` method —
   see above for why it can't live inside `resolve()`) and `getStatsSnapshot(code)` (an uncached
   read, so stats are never served from a stale cache entry).
5. `RedirectController` — call `recordClick` after a successful `resolve`.
6. `LinkStatsResponse` + `StatsController` — new read endpoint, `GET /api/links/{code}/stats`,
   reusing the existing `ShortLinkNotFoundException` → `404` mapping already registered in
   `ApiExceptionHandler`.
7. Tests — a repository/service-level integration test proving the count is exact across repeated
   redirects and still correct when the destination lookup was cache-served; a contract test for
   the new endpoint's `200`/`404` shapes.
8. Docs — `requirements.md`, `data-model.md`, `api.yaml`, `design-decisions.md`,
   `getting-started.md`, `README.md` all needed updates; several explicitly said analytics was
   *out* of scope and had to be corrected rather than just added to.

Step 4 is also where an ambiguous part of this requirement showed up: "track clicks" doesn't say
whether the count needs to be exact, which mattered because the redirect path is cache-aside (see
`design-decisions.md`'s "Click tracking: exact vs. approximate counting" for the full trade-off and
decision). That ambiguity was resolved inline as part of this scenario rather than as a separate
deliverable — see `ambiguous-link-expiration.md` for the standalone ambiguous-requirement scenario.

## Execution

All code (migration, entity, repository, service methods, controller, DTO, and both new test
classes) was AI-generated in one pass, following the conventions the existing codebase already
established rather than introducing new ones — e.g. the atomic `UPDATE` mirrors the existing
`INSERT ... ON CONFLICT` pattern in `ShortLinkRepository`, and the new DTO/controller mirror the
shape of `LinkResponse`/`LinkController`.

Reviewed before being treated as done:

- The interaction between `@Cacheable resolve()` and the click counter was the one place AI-first
  output would have been wrong if written naively (incrementing inside `resolve()` would drop
  counts on cache hits) — this was caught during design, before code was written, not as a
  post-hoc fix.
- Nothing generated this round needed to be rejected or rewritten; each piece was checked against
  the existing code it touches (see Validation) before moving to the next task in the
  decomposition above.
- The specific counting strategy (synchronous exact vs. batched/approximate) was a judgment call
  presented to the engineer with trade-offs *before* implementation, not decided unilaterally — see
  the "Click tracking" entry in `design-decisions.md`.

## Validation

- `mvn -DskipTests compile` — clean compile after every file change.
- Existing unit tests (`ShortCodeEncoderTest`, `DestinationUrlValidatorTest`, `RateLimiterTest`) —
  still pass, confirming no regression to unrelated modules.
- End-to-end, via `docker compose up --build` against a fresh database (new migration applied
  cleanly):
  - `POST /api/links` → `stats` immediately after shows `clickCount: 0`, `lastAccessedAt: null`.
  - Three redirects (`GET /{code}`) against the same code.
  - `stats` afterward shows exactly `clickCount: 3`, with `lastAccessedAt` populated.
  - `stats` for a nonexistent code returns `404` with the same error shape the redirect endpoint
    uses.
- The two new automated tests (`ClickAnalyticsIntegrationTest`, `StatsContractTest`) are written
  and structurally correct, but — like the rest of this project's Testcontainers-backed suite —
  can't execute via `mvn test` on this machine due to the pre-existing Docker
  Desktop/Testcontainers compatibility issue documented in `design-decisions.md`. The manual
  end-to-end pass above exercises the same behavior these tests assert (exact count, cache
  interaction, 404 shape) through the real HTTP layer instead.

## Risks / limitations

- Every redirect now performs a database write (see `design-decisions.md`'s trade-off writeup) —
  acceptable at this scale, revisit if redirect volume or instance count grows.
- Click counts are a single running total, not a time-series; "clicks per day" or "top referrers"
  would be a separate, larger feature.
- If Postgres is briefly unavailable during a cached redirect, the redirect itself still succeeds
  (served from cache) but the click for that request is lost rather than queued — an accepted gap
  given exact-but-synchronous was the chosen trade-off.
