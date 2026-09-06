# Phase 0 Research: Custom Alias on Link Creation

## 1. Widening the redirect route pattern without shadowing system routes

**Decision**: Change `RedirectController`'s mapping from `@GetMapping("/{code:[a-zA-Z0-9]{6}}")`
to `@GetMapping("/{code:[a-zA-Z0-9_-]{3,32}}")`, and treat reserved-word rejection at
alias-*creation* time as the authoritative guard against collisions — not the route regex.

**Rationale**: The original 6-char-exact constraint existed to stop this catch-all-looking route
from shadowing root-level system paths (`/swagger-ui.html`, `/actuator`). Widening it to 3–32
chars means shapes like `actuator` (8 alphanumeric chars) now fit the pattern. Two independent
protections make this safe:
- `CustomAliasValidator` rejects any alias matching a reserved name before a row can ever be
  created, so `resolve("actuator")` always 404s — there is never a row for Spring to find.
- Spring MVC ranks a fully literal mapping (no path variable) as more specific than a
  variable/regex mapping for the same URI, so Actuator's own literal `/actuator` mapping and
  springdoc's literal mappings still win routing precedence regardless of what our regex allows
  in shape. This is defense-in-depth, not the primary guarantee — the reserved-word check is.
`/swagger-ui.html` and Actuator's sub-paths (`/actuator/health`) are unaffected either way since
they contain a `.` or a `/`, neither of which is in the alias charset.

**Alternatives considered**:
- *Leave the route fixed at 6 chars, restrict aliases to exactly 6 chars too*: rejected — this
  defeats the entire point of a memorable alias (`summer-sale` is 11 characters) and isn't what
  the spec asks for (FR-004 sets 3–32).
- *Keep two separate routes (one fixed-6, one variable) chosen by a lookup*: rejected as
  needless complexity — a single widened regex plus the existing reserved-word check achieves
  the same safety with no new routing logic (Governance: avoid new abstractions not required by
  a principle).

## 2. Atomically distinguishing "alias taken" from "URL already shortened"

**Decision**: Add `ShortLinkRepository.insertWithAlias(...)` using
`INSERT ... ON CONFLICT (short_code) DO NOTHING RETURNING *` (mirroring the existing
`insertIfLongUrlAbsent`'s pattern, but targeting the other unique constraint). An empty result
means the alias was taken by a concurrent request — mapped to `AliasAlreadyTakenException`
(409). A `DataIntegrityViolationException` surfacing the `uq_short_links_long_url` constraint
name means the destination URL already has a live mapping — mapped to
`DestinationAlreadyShortenedException` (409), per the confirmed FR-009 behavior (reject, don't
silently return the existing link, when the caller explicitly asked for an alias).

**Rationale**: Postgres's `ON CONFLICT` clause can only suppress a violation on the constraint
it names; a violation on the *other* unique constraint in the same statement still raises. That
asymmetry is exactly what's needed here: it lets one INSERT distinguish two conflict types
atomically, with no pre-check SELECT and therefore no TOCTOU race — consistent with Principle
I's "rejected atomically on conflict" and "collisions via unique constraints enforced at the
data layer, not application-level checks alone."

**Correction during implementation**: the initial version also tried to name the existing short
code in `DestinationAlreadyShortenedException`'s message by calling `findByLongUrl` inside the
`catch` block. This broke, caught by running `CustomAliasConflictContractTest` against a real
Postgres instance: Postgres aborts the entire transaction the instant the INSERT violates
`uq_short_links_long_url`, and this method's `@Transactional` boundary only rolls that
transaction back once the method itself exits — so the follow-up query, still inside the same
now-aborted transaction, failed with "current transaction is aborted, commands ignored until end
of transaction block." Fixed by dropping the lookup; the exception's message states the conflict
without naming the code, which is all FR-009 actually requires.

**Alternatives considered**:
- *Pre-check with `findByLongUrl`/`findByShortCode` before inserting*: rejected — introduces a
  race window between check and insert under concurrent requests, which is precisely the bug
  User Story 2's acceptance scenario 2 tests against.
- *Single generic `ON CONFLICT DO NOTHING` with no target column*: rejected — suppresses either
  conflict without telling the caller which one fired, and the spec requires the two rejection
  reasons to be distinguishable (FR-009 vs. FR-007/taken).

## 3. Case normalization

**Decision**: Store the lowercased form of the submitted alias; the response's `shortCode`
reflects that lowercased value, not the caller's original casing.

**Rationale**: Every stored short code today is already lowercase-only — `ShortCodeEncoder`'s
alphabet is lowercase, and `resolve`/`recordClick` both lowercase their input before matching.
Storing aliases lowercased reuses this exact mechanism for case-insensitive matching (FR-006)
with no new column, index, or citext extension — consistent with Governance's preference for
extending existing mechanisms over introducing new ones.

**Alternatives considered**:
- *Preserve original casing in storage, lowercase only for comparison*: rejected — would need a
  second lowercased column or a case-insensitive index/collation not used anywhere else in this
  schema, for a purely cosmetic benefit (the spec never requires echoing back the caller's exact
  casing).

## 4. Reserved-word list

**Decision**: A static `Set<String>` in `CustomAliasValidator`: `api`, `actuator`, `health`,
`error`, `swagger-ui` — the literal first-path-segment names actually used by this application's
own routes and Spring Boot/springdoc defaults today.

**Rationale**: Matches the spec's Assumptions section ("starts with the application's own known
top-level route segments... extended if new system routes are added later"). A hardcoded set is
the simplest mechanism that satisfies the requirement at this project's scale; no config file or
database-backed list is justified absent a documented need for one (Governance).

**Correction during implementation**: the original decision also listed `v3` (springdoc's
`/v3/api-docs`), which running the test suite caught as broken — `v3` is 2 characters, already
unreachable as an alias below the 3-character minimum from decision §4's own length rule, and
`/v3/api-docs` is a two-segment path the single-segment `{code}` route never matches regardless.
It was dropped rather than worked around, since it protected against a collision that was never
actually reachable.

**Alternatives considered**:
- *Externalize the list to `application.yml`*: rejected for now as unnecessary indirection for a
  list that only changes when the application itself adds a new root route — a rare, code-level
  event where updating a constant alongside the new `@RequestMapping` is not a burden.

## 5. Redirect-path latency re-measurement

**Decision**: Re-run the existing load-test setup documented in `docs/performance.md` against
the widened route pattern before this feature merges, and record the new p50/p95 numbers
alongside the previous baseline.

**Rationale**: The Reliability & Data Standards section requires measured latency numbers for
"any change to the redirect path," and this feature changes that route's regex. This is a
process step to carry into `tasks.md`, not a design change here.
