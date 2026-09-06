# Implementation Plan: Custom Alias on Link Creation

**Branch**: `feature/custom-alias` | **Date**: 2026-09-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-custom-alias/spec.md`

## Summary

Let a caller supply an optional `alias` on `POST /api/links` in place of a random short code.
The alias is validated for shape (character set, length, reserved words) before any database
work, then claimed atomically against the same `short_code` column and unique constraint that
already back auto-generated codes — no new table, no new service, no new datastore. Two new
creation-time conflict outcomes become distinguishable from today's single "return the existing
link" behavior: the alias is already taken, or the destination URL already has a live link under
a different code. Everything downstream of creation (redirect, stats, expiration) is unchanged,
since a custom alias is just a short code that happened to be chosen by its caller instead of
generated.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot (Web, Data JPA, Validation, Cache, Actuator), Flyway, PostgreSQL JDBC driver, springdoc-openapi — all already in use, no additions

**Storage**: PostgreSQL, `short_links` table (existing) — widened column/constraint only, no new table

**Testing**: JUnit 5 + Spring Boot Test (contract tests), Testcontainers PostgreSQL (integration tests) — same layout as existing `src/test/java/com/urlshortener/{contract,integration,unit}`

**Target Platform**: Linux server (containerized Spring Boot service), unchanged

**Project Type**: Single web service (existing layout — no new modules)

**Performance Goals**: Redirect path (`GET /{code}`) must not regress against the p50/p95 baseline in `docs/performance.md` — its route pattern is touched by this feature (see Research §1), which the constitution treats as "any change to the redirect path" and therefore requires fresh measurement

**Constraints**: Alias charset `[a-z0-9_-]`, length 3–32, case-insensitive, reserved-word rejection, atomic conflict handling (Principle I); no new services/datastores (Governance — extend existing modules first)

**Scale/Scope**: Same single-instance, solo-developer scale as the rest of the project — no scale assumptions change

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design below.*

| Principle | Check | Result |
|---|---|---|
| I. Short Link Integrity | Alias availability validated before acceptance; rejected atomically on conflict (no silent overwrite); collision avoidance via DB unique constraint, not app-level checks alone; short code (chosen or generated) still never changes after creation | PASS |
| II. Redirect Performance & Availability | Redirect path stays cache-first and structurally unchanged (only its route regex widens); no new synchronous work added to the redirect path | PASS — but triggers the Reliability standard's latency re-measurement requirement (see below) |
| III. Test-First Delivery (NON-NEGOTIABLE) | Contract + integration + unit tests for alias validation, conflict handling, and the widened redirect route MUST be written and failing before any implementation code, per `/speckit-tasks` ordering | GATE CARRIED FORWARD to tasks.md — no exceptions this time, unlike the logged deviations for click-analytics/link-expiration |
| IV. Analytics Without Compromise | Not touched by this feature | N/A |
| V. API Contract Discipline | `docs/api.yaml` contract fragment produced in Phase 1, **before** implementation — corrects the pattern flagged as a deviation in the two prior features rather than repeating it; rate limiting on `/api/links` already applies path-wide regardless of body, so it already covers alias requests with no config change | PASS |
| Reliability & Data Standards | Mapping writes still synchronously acknowledged before response; schema change ships with a documented rollback plan (below); expired/never-existed 404 indistinguishability is untouched (alias/URL conflicts are creation-time errors, not resolution-time) | PASS, with one follow-through obligation: p50/p95 redirect latency must be re-measured post-implementation since the redirect route pattern changes |
| Development Workflow & Quality Gates | This change touches persistence (migration) and validation/conflict logic — classified high-impact, requires explicit project-owner sign-off before merge regardless of authorship | GATE CARRIED FORWARD to review before merge |

No violations requiring the Complexity Tracking table: this feature extends the existing
`ShortLinkService` / `ShortLinkRepository` / `short_links` table and existing validator pattern
(`DestinationUrlValidator` → new sibling `CustomAliasValidator`), introducing no new service,
module, or datastore.

## Project Structure

### Documentation (this feature)

```text
specs/001-custom-alias/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── api-changes.yaml # Phase 1 output — fragment to merge into docs/api.yaml
└── tasks.md             # Phase 2 output (/speckit-tasks — not created here)
```

### Source Code (repository root)

```text
src/main/java/com/urlshortener/
├── link/
│   ├── LinkController.java              # extended: read alias from request, map new conflict exceptions
│   ├── RedirectController.java          # extended: widen {code} route regex
│   ├── ShortLinkService.java            # extended: create(url, expiresInSeconds, alias) overload
│   ├── ShortLinkRepository.java         # extended: insertWithAlias(...) atomic query
│   ├── AliasAlreadyTakenException.java  # new
│   ├── DestinationAlreadyShortenedException.java # new
│   └── dto/
│       └── CreateLinkRequest.java       # extended: optional `alias` field
├── validation/
│   ├── CustomAliasValidator.java        # new — format + reserved-word checks
│   └── InvalidAliasException.java       # new
└── common/
    └── ApiExceptionHandler.java         # extended: map the three new exceptions

src/main/resources/db/migration/
└── V4__widen_short_code_for_custom_alias.sql   # new

src/test/java/com/urlshortener/
├── contract/
│   └── CustomAliasContractTest.java     # new
├── integration/
│   └── CustomAliasIntegrationTest.java  # new
└── unit/
    └── CustomAliasValidatorTest.java    # new
```

**Structure Decision**: Single existing Spring Boot service, unchanged package layout. Every
addition is a new sibling file next to its existing counterpart (validator next to validator,
exception next to exception, migration next in the existing Flyway sequence) — no new module,
consistent with the Governance clause's "extend existing modules... before introducing new
ones."

## Complexity Tracking

*No entries — no Constitution Check violations require justification.*

## Post-Design Constitution Re-Check

Re-verified after Phase 1 (research.md, data-model.md, contracts/, quickstart.md): the atomic
dual-conflict INSERT (Research §2) and the lowercase-normalization decision (Research §3) both
reuse the exact mechanisms Principle I already prescribes (DB-level uniqueness, no
application-level check standing in for it), so no new gate is triggered by the concrete design.
The route-widening decision (Research §1) is the one design choice with real risk, and it's
resolved by a creation-time guard (reserved words can never become rows) rather than by trusting
routing precedence alone. No new violations found; no Complexity Tracking entries needed.
