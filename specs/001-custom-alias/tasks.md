---

description: "Task list template for feature implementation"
---

# Tasks: Custom Alias on Link Creation

**Input**: Design documents from `specs/001-custom-alias/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/api-changes.yaml](./contracts/api-changes.yaml), [quickstart.md](./quickstart.md)

**Tests**: Test-First Delivery is Principle III of this project's constitution and marked
NON-NEGOTIABLE — every test task below MUST be written and confirmed failing before its
corresponding implementation task is started. This is not optional for this feature (unlike the
two prior features, which logged this as an acknowledged deviation — plan.md commits to no
exceptions going forward).

**Organization**: Tasks are grouped by user story (spec.md) to enable independent implementation
and testing of each.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Every task names its exact file path

## Phase 1: Setup

**Purpose**: Satisfy Principle V (API contract discipline) before any implementation begins —
correcting the contract-timing deviation logged against the two prior features.

- [X] T001 Merge `specs/001-custom-alias/contracts/api-changes.yaml` into `docs/api.yaml`: add the `alias` property to the `CreateLinkRequest` schema and the two new `409` responses (`ALIAS_TAKEN`, `URL_ALREADY_SHORTENED`) to the `POST /api/links` operation, in `docs/api.yaml`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared infrastructure every user story depends on — schema, request shape, error
types, the atomic conflict-aware insert, and the widened redirect route.

**⚠️ CRITICAL**: No user story task may start until this phase is complete.

- [X] T002 Create Flyway migration widening `short_code` to `VARCHAR(32)` and replacing `chk_short_links_short_code_format` with `CHECK (short_code ~ '^[a-z0-9_-]{3,32}$')`, with the manual rollback plan documented in a header comment (see [data-model.md](./data-model.md)), in `src/main/resources/db/migration/V4__widen_short_code_for_custom_alias.sql`
- [X] T003 [P] Add an optional `alias` field (no `@NotBlank`, absence means auto-generate) to `src/main/java/com/urlshortener/link/dto/CreateLinkRequest.java`
- [X] T004 [P] Create `InvalidAliasException` (400, format/reserved-name violations) in `src/main/java/com/urlshortener/validation/InvalidAliasException.java`
- [X] T005 [P] Create `AliasAlreadyTakenException` (409, carries the requested alias) in `src/main/java/com/urlshortener/link/AliasAlreadyTakenException.java`
- [X] T006 [P] Create `DestinationAlreadyShortenedException` (409) in `src/main/java/com/urlshortener/link/DestinationAlreadyShortenedException.java` — does not carry the existing short code (see T018 correction: looking it up inside the already-failed transaction that triggers this exception isn't possible)
- [X] T007 Add `@ExceptionHandler` methods for `InvalidAliasException` → `400 VALIDATION_ERROR`, `AliasAlreadyTakenException` → `409 ALIAS_TAKEN`, `DestinationAlreadyShortenedException` → `409 URL_ALREADY_SHORTENED` in `src/main/java/com/urlshortener/common/ApiExceptionHandler.java` (depends on T004, T005, T006)
- [X] T008 Add `ShortLinkRepository.insertWithAlias(id, shortCode, longUrl, createdAt, expiresAt)` using `INSERT ... ON CONFLICT (short_code) DO NOTHING RETURNING *`, mirroring `insertIfLongUrlAbsent`'s pattern (see [research.md](./research.md) §2), in `src/main/java/com/urlshortener/link/ShortLinkRepository.java` (depends on T002)
- [X] T009 [P] Widen `RedirectController`'s route from `@GetMapping("/{code:[a-zA-Z0-9]{6}}")` to `@GetMapping("/{code:[a-zA-Z0-9_-]{3,32}}")` in `src/main/java/com/urlshortener/link/RedirectController.java`
- [X] T010 [P] Implement `CustomAliasValidator`: length 3–32, charset `[a-zA-Z0-9_-]`, reserved-name rejection (`api`, `actuator`, `health`, `error`, `swagger-ui`, case-insensitive), throwing `InvalidAliasException` naming the violated rule, in `src/main/java/com/urlshortener/validation/CustomAliasValidator.java` — `v3` was dropped from the original list; see research.md §4's implementation correction

**Checkpoint**: Foundation ready — user story work can now begin.

---

## Phase 3: User Story 1 - Claim a memorable alias when shortening a link (Priority: P1) 🎯 MVP

**Goal**: A caller can supply an alias on creation and get back exactly that alias as the short
code, immediately resolvable.

**Independent Test**: `POST /api/links` with `{"url": "...", "alias": "summer-sale"}`, verify
the response's `shortCode` is `summer-sale`, then `GET /summer-sale` redirects to the submitted
URL.

### Tests for User Story 1 ⚠️

> Write these first; confirm they fail before starting the Implementation tasks below.

- [X] T011 [P] [US1] Contract test: available alias → `201` with `shortCode` equal to the alias; no-alias request still auto-generates unchanged (FR-002), in `src/test/java/com/urlshortener/contract/CustomAliasCreationContractTest.java`
- [X] T012 [P] [US1] Unit test: `CustomAliasValidator` accepts every well-formed shape (min length 3, max length 32, each allowed character class), in `src/test/java/com/urlshortener/unit/CustomAliasValidatorTest.java`
- [X] T013 [P] [US1] Integration test: a link created with an alias resolves via `GET /{alias}` to the destination URL, and resolves identically regardless of case (`Promo2026` vs `promo2026`, FR-006), in `src/test/java/com/urlshortener/integration/CustomAliasIntegrationTest.java`

### Implementation for User Story 1

- [X] T014 [US1] Add `ShortLinkService.create(String longUrl, Long expiresInSeconds, String alias)`: lowercase-normalize the alias, run it through `CustomAliasValidator`, then call `insertWithAlias` for the success path, in `src/main/java/com/urlshortener/link/ShortLinkService.java` (depends on T008, T010)
- [X] T015 [US1] Wire `LinkController.createShortLink` to read `alias` from `CreateLinkRequest` and call the new service overload, in `src/main/java/com/urlshortener/link/LinkController.java` (depends on T014)

**Checkpoint**: User Story 1 is fully functional and independently testable — creating and
resolving a custom-alias link works end to end.

---

## Phase 4: User Story 2 - Reject a taken alias without overwriting the existing link (Priority: P1)

**Goal**: Conflicting requests are rejected atomically; nothing is ever silently overwritten or
misattributed.

**Independent Test**: Create alias `promo`; submit a second, different URL with the same alias;
verify the second request is rejected and the first mapping is untouched.

### Tests for User Story 2 ⚠️

> Write these first; confirm they fail before starting the Implementation task below.

- [X] T016 [P] [US2] Contract test: alias already in use → `409 ALIAS_TAKEN`; destination URL already has a live link and an alias was requested → `409 URL_ALREADY_SHORTENED` (FR-009), in `src/test/java/com/urlshortener/contract/CustomAliasConflictContractTest.java`
- [X] T017 [P] [US2] Integration test: two concurrent creation requests for the same available alias — exactly one succeeds, the other receives `409 ALIAS_TAKEN`, and the winning mapping is unmodified, in `src/test/java/com/urlshortener/integration/CustomAliasConflictIntegrationTest.java`

### Implementation for User Story 2

- [X] T018 [US2] In `ShortLinkService`'s alias-create path, translate `insertWithAlias` returning empty into `AliasAlreadyTakenException` (message names the alias) and a caught `uq_short_links_long_url` constraint violation into `DestinationAlreadyShortenedException`, in `src/main/java/com/urlshortener/link/ShortLinkService.java` (depends on T014) — **correction**: the initial implementation tried to name the existing short code in the message by querying `findByLongUrl` inside the `catch` block, but Postgres aborts the whole transaction the instant the INSERT violates `uq_short_links_long_url`, so that follow-up query itself failed with "current transaction is aborted" — caught by running `CustomAliasConflictContractTest` against a real Postgres instance, not assumed correct. Fixed by dropping the existing-code lookup; the message states the conflict without naming the code

**Checkpoint**: User Stories 1 and 2 both work independently — the feature is now safe to ship
(memorable aliases, with no silent-overwrite failure mode).

---

## Phase 5: User Story 3 - Get a clear reason when an alias is invalid (Priority: P2)

**Goal**: A malformed or reserved alias is rejected with a reason specific enough to fix without
guessing, and distinct from an "already taken" conflict.

**Independent Test**: Submit aliases with a disallowed character, too-short length, too-long
length, and a reserved word; verify each gets a distinct, specific `400` reason.

### Tests for User Story 3 ⚠️

> Write these first; confirm they fail before starting the Implementation task below. (Most of
> the underlying behavior already exists from Foundational task T010 — these tests pin down its
> exact rejection reasons per rule, which is this story's actual deliverable.)

- [X] T019 [P] [US3] Contract test: alias too short, too long, containing a disallowed character, and equal to a reserved name each return `400 VALIDATION_ERROR` with a rule-specific message, distinct from the `409` conflicts in US2, in `src/test/java/com/urlshortener/contract/CustomAliasValidationContractTest.java`
- [X] T020 [US3] Extend `CustomAliasValidatorTest.java` (created in T012) with one rejection case per rule (length, charset, reserved), asserting each exception message names its specific rule, in `src/test/java/com/urlshortener/unit/CustomAliasValidatorTest.java` (depends on T012 — same file)

### Implementation for User Story 3

- [X] T021 [US3] Refine `CustomAliasValidator`'s exception messages so length, charset, and reserved-name violations are each individually identifiable (address any gaps found by T019/T020), in `src/main/java/com/urlshortener/validation/CustomAliasValidator.java` (depends on T010) — T010's original messages already satisfy T019/T020 (distinct "between"/"letters, digits, hyphens, and underscores"/"reserved" wording); no change needed

**Checkpoint**: All three user stories are independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T022 [P] Verify `docs/api.yaml` matches the final implementation (status codes, field constraints, examples), in `docs/api.yaml`
- [ ] T023 Re-run the load-test tooling documented in `docs/performance.md` against the widened `GET /{code}` route and record fresh p50/p95 numbers next to the existing baseline (Reliability & Data Standards — required because this feature changes the redirect route), in `docs/performance.md` — **NOT completed**: this needs a running instance of the app (`docker compose up --build`), a separate concern from the `mvn test` suite (now fully green, see Notes); `docs/performance.md` documents the exact commands to run before merge
- [ ] T024 Run every scenario in `specs/001-custom-alias/quickstart.md` end-to-end against a running instance — **NOT completed**: same as T023, needs a live running instance rather than the test suite; the underlying behavior is covered by the automated contract/integration tests (all passing), but that's not a substitute for the manual curl walkthrough
- [X] T025 [P] Update `docs/architecture-overview.md` and/or `docs/design-decisions.md` if they enumerate endpoints, short-code rules, or the route-regex rationale affected by this feature — also updated `docs/data-model.md`, which enumerates the schema directly

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2)**: Depends on Setup (T001) only loosely — in practice can start in
  parallel with it, but BLOCKS all user stories.
- **User Stories (Phase 3+)**: All depend on Foundational (Phase 2) completion.
  - US1 and US2 are both P1 and together form the minimum safe MVP — implement in order (US1
    then US2) since US2's tests exercise code paths US1's implementation creates.
  - US3 (P2) depends only on Foundational (T010's validator already exists) and can start any
    time after Phase 2, independently of US1/US2.
- **Polish (Phase 6)**: Depends on all three user stories being complete.

### Within Each User Story

- Tests MUST be written and confirmed failing before that story's Implementation tasks start
  (Principle III, no exceptions on this feature).
- US2's implementation (T018) depends on US1's (T014) — same method, its conflict branches.
- US3's tests may be written any time after Foundational; its one implementation task (T021)
  only refines what T010 already built.

### Parallel Opportunities

- T003–T006 and T009–T010 (Foundational) can all run in parallel — five different files.
- T011, T012, T013 (US1 tests) can run in parallel — three different files.
- T016, T017 (US2 tests) can run in parallel — two different files.
- T019 (US3 contract test) can run in parallel with T011–T013/T016–T017 once Foundational is
  done, since it's an independent file with no dependency on US1/US2 implementation.
- T022 and T025 (Polish) can run in parallel with each other.

---

## Parallel Example: Foundational Phase

```bash
Task: "Add optional alias field to CreateLinkRequest in src/main/java/com/urlshortener/link/dto/CreateLinkRequest.java"
Task: "Create InvalidAliasException in src/main/java/com/urlshortener/validation/InvalidAliasException.java"
Task: "Create AliasAlreadyTakenException in src/main/java/com/urlshortener/link/AliasAlreadyTakenException.java"
Task: "Create DestinationAlreadyShortenedException in src/main/java/com/urlshortener/link/DestinationAlreadyShortenedException.java"
Task: "Widen RedirectController's {code} route regex in src/main/java/com/urlshortener/link/RedirectController.java"
Task: "Implement CustomAliasValidator in src/main/java/com/urlshortener/validation/CustomAliasValidator.java"
```

## Parallel Example: User Story 1 Tests

```bash
Task: "Contract test for available-alias creation in src/test/java/com/urlshortener/contract/CustomAliasCreationContractTest.java"
Task: "Unit test for CustomAliasValidator's accepted shapes in src/test/java/com/urlshortener/unit/CustomAliasValidatorTest.java"
Task: "Integration test for alias redirect + case-insensitivity in src/test/java/com/urlshortener/integration/CustomAliasIntegrationTest.java"
```

---

## Implementation Strategy

### MVP First (User Stories 1 + 2)

Unlike a typical spec-kit MVP (User Story 1 alone), this feature's MVP is **US1 + US2
together**: Principle I treats atomic conflict-rejection as inseparable from claiming an alias
at all — shipping "claim an alias" without "never silently overwrite on conflict" would violate
the constitution's core trust guarantee, not just leave a nice-to-have out.

1. Complete Phase 1 (Setup) and Phase 2 (Foundational).
2. Complete Phase 3 (US1) and Phase 4 (US2).
3. **STOP and VALIDATE**: run the T024 quickstart scenarios for sections 1–3 and 6.
4. This is the MVP — safe to deploy/demo.

### Incremental Delivery

1. Setup + Foundational → foundation ready.
2. US1 + US2 together → MVP (memorable aliases, conflict-safe) → validate → deploy/demo.
3. US3 → clearer rejection reasons → validate → deploy/demo.
4. Polish → contract/doc/performance follow-through → done.

---

## Notes

- **Final suite status, after rebasing onto the merged web UI feature**: `mvn test` — **122
  tests, 0 failures, 0 errors**. Fully green.

- **Pre-existing bug identified, initially deferred, now fixed for real**:
  `ShortLinkService.create(String longUrl)` (the 1-arg overload, untouched by this feature
  otherwise) had no `@Transactional` of its own — it only inherited transactional behavior by
  calling `create(longUrl, null)` internally, which is a same-class self-invocation that bypasses
  Spring's proxy and therefore that method's `@Transactional` advice entirely. `deleteIfExpired`'s
  `@Modifying` query then ran with no active transaction and failed with
  `InvalidDataAccessApiUsageException: Executing an update/delete query`. This had apparently
  never been caught before because the test suite's Docker-based database had never successfully
  connected in a session before this one (see the Testcontainers finding below). The fix (adding
  `@Transactional` directly to the 1-arg method) was verified working, then reverted once to keep
  this feature branch narrowly scoped while the web UI feature was built and merged separately,
  and reapplied here — after rebasing onto the merged web UI — once keeping the two features on
  separate branches no longer required deferring an already-solved, already-verified fix.

- **Bug found and fixed (this feature's own code)**: the original reserved-name list included
  `v3`, but `v3` is only 2 characters — shorter than the alias minimum length (3) — so the length
  check always rejected it first and the reserved-name message could never actually fire. Caught
  by `CustomAliasValidatorTest.rejectsAReservedName`. Fixed by dropping `v3` from the reserved
  list everywhere it was documented (it also never corresponded to a real route collision —
  springdoc's actual route is the two-segment `/v3/api-docs`, which the single-segment `{code}`
  route never matches anyway). See `research.md` §4's correction note.

- **Bug found and fixed (this feature's own code)**: `ShortLinkService.create(...)`'s
  `DestinationAlreadyShortenedException` path originally tried to name the existing short code by
  querying `findByLongUrl` inside the `catch` block. Postgres aborts the entire transaction the
  instant the triggering INSERT violates a constraint, so that follow-up query — still inside the
  same now-aborted transaction — itself failed with "current transaction is aborted." Caught by
  `CustomAliasConflictContractTest` against a real Postgres instance. Fixed by dropping the
  lookup; the message states the conflict without naming the code. See `research.md` §2's
  correction note.

- **Test infrastructure change (not part of the original plan, made necessary by the findings
  above)**: switched integration/contract tests from Testcontainers (Docker-based Postgres) to Zonky's
  embedded-postgres (a real Postgres binary run directly on the host). Two independent problems
  drove this: (1) Testcontainers' Docker client got a malformed `400` from this machine's Docker
  Desktop version regardless of transport (npipe or TCP), reproducing identically on the
  pre-existing `RedirectContractTest`; (2) `AbstractIntegrationTest`'s `@Container`-annotated
  static field restarted the Postgres container per test class, which was both slow and a source
  of stale-`DataSource` failures once Docker connectivity was restored. A shared-container
  ("singleton") fix for (2) alone was proven to work (13s, 0 failures) but still left Docker as a
  hard requirement to run `mvn test` at all — switching to embedded-postgres removes that
  requirement entirely while keeping full Postgres fidelity (this project's migrations use
  genuine Postgres-only SQL — regex `CHECK` constraints, native `ON CONFLICT ... RETURNING` — so
  a lighter substitute like H2 would not faithfully exercise them). See `AbstractIntegrationTest`'s
  class-level Javadoc for the full account.

- **Bug found and fixed (exposed by the above, not introduced by this feature)**: sharing one
  Spring context across the whole suite (a genuine improvement) also means the `RateLimiter`
  bean's per-IP token bucket is genuinely shared across all 116 tests, not silently reset per
  class the way it accidentally was before. Most tests share one default client IP and
  collectively exhausted the production-sized bucket (capacity 20), cascading into unrelated
  `429` failures. Fixed by raising only `app.rate-limit.capacity` for tests (not the refill rate)
  in `AbstractIntegrationTest` — see its Javadoc for why raising both broke
  `StatsRateLimitContractTest` on the first attempt (the bucket refills continuously, not per
  discrete window, so a larger capacity's longer loop plus a proportionally faster refill rate
  let it refill during its own test).

- **Test-First is non-negotiable on this feature** (plan.md's Constitution Check): every `⚠️`
  block above must be red before its Implementation tasks begin. This is a deliberate change
  from the two prior features, which logged Test-First violations as acknowledged deviations —
  this feature does not repeat that.
- **High-impact sign-off**: this feature touches persistence (T002 migration) and
  validation/conflict logic (T010, T014, T018), the rebased-in test-infrastructure changes
  (Testcontainers → embedded-postgres, rate-limit test config) also touch persistence and test
  infrastructure, and the pre-existing `@Transactional` fix now included here also touches
  persistence — per the constitution's Development Workflow section, all of it needs the project
  owner's explicit sign-off before merge, regardless of authorship.
- [P] tasks = different files, no dependency on an incomplete task.
- Commit after each task or logical group, per the project's incremental-commit convention.
