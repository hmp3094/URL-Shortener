---

description: "Task list template for feature implementation"
---

# Tasks: Web UI for Shortening and Stats Lookup

**Input**: Design documents from `specs/002-web-ui/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [quickstart.md](./quickstart.md)

**Tests**: Test-First Delivery is Principle III of this project's constitution and marked
NON-NEGOTIABLE. Every automatable piece of this feature (the page and its assets being served
with the expected shape) gets a test written and confirmed failing before the corresponding
markup/config exists. The one thing that genuinely can't be automated here — interactive
JavaScript behavior in a real browser — has no test-runner tooling in this project (no Node, no
headless-browser harness) and is validated manually instead (Phase 6, quickstart.md), documented
as a deliberate choice rather than a silently skipped gate.

**Organization**: Tasks are grouped by user story (spec.md) to enable independent implementation
and testing of each.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Every task names its exact file path

## Phase 1: Foundational (Blocking Prerequisites)

**Purpose**: The one backend change every story depends on, plus the page shell and shared visual
foundation all three stories build their own section into.

**⚠️ CRITICAL**: No user story task may start until this phase is complete.

- [X] T001 Write a contract test asserting `GET /` returns `200` with an HTML content type, in `src/test/java/com/urlshortener/contract/WebUiContractTest.java` (extends `AbstractIntegrationTest`) — confirm it fails first (nothing is served yet) — **adjusted during implementation**: MockMvc doesn't re-dispatch Spring Boot's welcome-page `forward:index.html` through the real resource handler (documented MockMvc behavior, confirmed correct against a real running instance instead), so the test asserts `GET /` resolves with `200` and a separate `GET /index.html` test verifies the actual rendered content — see the class-level comment in the test file
- [X] T002 Remove `spring.web.resources.add-mappings: false` from `src/main/resources/application.yml`, restoring Spring Boot's default static-resource serving (see research.md §1 for why this can't collide with the redirect route)
- [X] T003 Create the page shell in `src/main/resources/static/index.html`: doctype, head (title, viewport meta, links to `style.css`/`app.js`), a header, and two empty, identifiable `<section>` containers for the shortening form and the stats lookup (depends on T002; makes T001 pass) — built together with T007/T011/T015 as one cohesive page rather than re-edited three separate times, since HTML for a single page is naturally authored as a whole
- [X] T004 [P] Create the shared visual foundation in `src/main/resources/static/style.css`: color palette, typography, spacing scale, responsive layout rules, and generic form/button/input styles the later sections reuse — built together with T009/T013/T017 for the same reason as T003
- [X] T005 [P] Create `src/main/resources/static/app.js` with shared bootstrap only: a load listener and small helper functions for calling the JSON API and rendering a message into a container — no story-specific behavior yet

**Checkpoint**: The page loads, styled, with an empty shell; T001 passes.

---

## Phase 2: User Story 1 - Shorten a link from a web page (Priority: P1) 🎯 MVP

**Goal**: A visitor can submit a URL and get back a short link they can copy in one action.

**Independent Test**: Submit a valid URL with no alias or expiration; verify a short link appears
and can be copied.

### Tests for User Story 1 ⚠️

> Write first; confirm it fails before starting the Implementation tasks below.

- [X] T006 [P] [US1] Extend `WebUiContractTest.java` with a test that the served page's markup contains the shortening form's essential controls (URL input, alias input, expiration control, submit button, a result container), identified by stable `id` attributes, in `src/test/java/com/urlshortener/contract/WebUiContractTest.java`

### Implementation for User Story 1

- [X] T007 [US1] Add the shortening form markup to `index.html`'s shorten section: URL input, alias input, expiration control (see research.md §3 for the friendly-duration approach), submit button, and a result container for the short link plus a copy control, in `src/main/resources/static/index.html` (depends on T003; makes T006 pass)
- [X] T008 [US1] Implement the happy-path submit handler in `app.js`: prevent default, translate the chosen expiration to seconds, `POST /api/links`, on success render the short link and wire the copy control (Clipboard API with the selectable-field fallback from research.md §5), in `src/main/resources/static/app.js` (depends on T005, T007)
- [X] T009 [US1] Style the form, result, and copy-control states in `style.css`, in `src/main/resources/static/style.css` (depends on T004, T007)

**Checkpoint**: The happy-path shortening flow works end to end in a real browser.

---

## Phase 3: User Story 2 - Understand why shortening didn't work (Priority: P1)

**Goal**: Every rejected submission is explained in plain language, distinguishing each known
failure reason.

**Independent Test**: Trigger each known failure case (bad URL, taken alias, duplicate URL with
an alias, rate limit) and verify each produces a distinct, plain-language message on the page.

### Tests for User Story 2 ⚠️

> Write first; confirm it fails before starting the Implementation task below.

- [X] T010 [US2] Extend `WebUiContractTest.java` with a test that the served page's markup includes an identifiable error-message container within the shorten section, in `src/test/java/com/urlshortener/contract/WebUiContractTest.java` (depends on T006 — same file)

### Implementation for User Story 2

- [X] T011 [US2] Add the error-message container to the shorten section's markup in `index.html`, in `src/main/resources/static/index.html` (depends on T007; makes T010 pass)
- [X] T012 [US2] Implement the error-code-to-plain-language mapping and failure-branch rendering in `app.js`'s submit handler (research.md §4 — `VALIDATION_ERROR`, `ALIAS_TAKEN`, `URL_ALREADY_SHORTENED`, `RATE_LIMITED` each get a distinct message; `VALIDATION_ERROR` can surface the API's own `message` directly), in `src/main/resources/static/app.js` (depends on T008, T011)
- [X] T013 [US2] Style the error state (visually distinct, sufficient contrast) in `style.css`, in `src/main/resources/static/style.css` (depends on T009, T011)

**Checkpoint**: User Stories 1 and 2 together are the real MVP — a shortening flow that both
works and explains itself when it doesn't.

---

## Phase 4: User Story 3 - Check how a link has performed (Priority: P2)

**Goal**: Anyone with a short code can see its click count, creation time, last-used time, and
expiration status from the same page.

**Independent Test**: Look up a code created moments ago and verify its stats appear; look up a
nonexistent or expired code and verify a single, plain "not found" message.

### Tests for User Story 3 ⚠️

> Write first; confirm it fails before starting the Implementation tasks below.

- [X] T014 [US3] Extend `WebUiContractTest.java` with a test that the served page's markup contains the stats-lookup section's essential controls (code input, lookup button, result container), in `src/test/java/com/urlshortener/contract/WebUiContractTest.java` (depends on T010 — same file)

### Implementation for User Story 3

- [X] T015 [US3] Add the stats-lookup markup to `index.html`'s stats section: code input, lookup button, a result display area, and a not-found message container, in `src/main/resources/static/index.html` (depends on T003; makes T014 pass)
- [X] T016 [US3] Implement the lookup handler in `app.js`: `GET /api/links/{code}/stats`, render click count/created/last-used/expiration on success, render the same not-found message for both a 404 and an expired link, in `src/main/resources/static/app.js` (depends on T005, T015)
- [X] T017 [US3] Style the stats section and its result/not-found states in `style.css`, in `src/main/resources/static/style.css` (depends on T004, T015)

**Checkpoint**: All three user stories are independently functional.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [X] T018 [P] Add a small favicon and any final visual polish (consistent hover/focus states, spacing) in `src/main/resources/static/` and `style.css`
- [X] T019 [P] Update `docs/architecture-overview.md` to note the served UI alongside the existing HTTP layer, in `docs/architecture-overview.md`
- [X] T020 [P] Update `docs/getting-started.md` with "open the UI" instructions alongside the existing curl walkthrough, in `docs/getting-started.md`
- [X] T021 Record the "hand-written page, no framework" decision and the static-resource-serving re-enablement in `docs/design-decisions.md` (see research.md §1–2), in `docs/design-decisions.md` — also corrected a now-stale "API-only, no frontend" claim in the Project layout section
- [X] T022 Run every scenario in `specs/002-web-ui/quickstart.md` end-to-end in a real browser against a running instance — **partially completed**: no browser-automation tooling exists in this environment (no `chromium-cli`, no Node/Playwright), so this ran as direct HTTP calls against a real running instance mirroring exactly what `app.js` sends/parses, plus a manual trace of the rendering logic against those real responses — not the same as an actual visual/interactive browser check, and worth re-running in a real browser before merge. Two real findings came out of it anyway (see Notes)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 1)**: No dependencies — BLOCKS all user stories.
- **User Stories (Phase 2+)**: All depend on Foundational completion.
  - US1 and US2 are both P1 and together form the real MVP — implement in order (US1 then US2),
    since US2's tests and markup extend what US1 creates in the same files.
  - US3 (P2) depends only on Foundational and can be built independently of US1/US2 — it touches
    a different section of the same shell and a different part of `app.js`.
- **Polish (Phase 5)**: Depends on all three user stories being complete.

### Within Each User Story

- Tests MUST be written and confirmed failing before that story's Implementation tasks start.
- US2's tasks (T010–T013) each depend on US1's corresponding task (T006/T007/T008/T009) — same
  files, additive changes, not independent work.
- US3's tasks (T014–T017) depend only on the Foundational shell (T003–T005), not on US1/US2.

### Parallel Opportunities

- T004 and T005 (Foundational) can run in parallel — different files, both depend only on T003.
- T009 (US1 styling) and T013 (US2 styling) touch the same file (`style.css`) sequentially, not
  in parallel, since US2 builds on US1's markup.
- T017 (US3 styling) can run in parallel with US1/US2 work once Foundational is done, since it's
  an independent section of the same files.
- T018–T020 (Polish) can run in parallel with each other.

---

## Parallel Example: Foundational Phase

```bash
Task: "Create shared visual foundation in src/main/resources/static/style.css"
Task: "Create shared JS bootstrap in src/main/resources/static/app.js"
```

---

## Implementation Strategy

### MVP First (User Stories 1 + 2)

Like the custom-alias feature before it, this feature's MVP is **US1 + US2 together**, not US1
alone: a shortening form that fails silently or with a raw error on invalid input isn't a
meaningfully different product from having no UI at all, per this feature's own success criteria.

1. Complete Phase 1 (Foundational).
2. Complete Phase 2 (US1) and Phase 3 (US2).
3. **STOP and VALIDATE**: run quickstart.md sections 1–2.
4. This is the MVP — safe to demo.

### Incremental Delivery

1. Foundational → shell ready.
2. US1 + US2 together → MVP (shorten and understand failures) → validate → demo.
3. US3 → stats lookup → validate → demo.
4. Polish → docs and final visual details → done.

---

## Notes

- **Full test suite status**: `mvn test` — **82 tests, 0 failures**, plus the same 12
  pre-existing `@Transactional` self-invocation errors already identified and deliberately left
  unfixed on the custom-alias branch (`ShortLinkService.create(String)` lacking its own
  `@Transactional`, inherited from `main`, unrelated to this feature). Confirmed identical root
  cause and identical unaffected scope on this branch.

- **Test fixed (this feature's own code)**: `WebUiContractTest`'s first draft asserted on
  `GET /`'s rendered content directly, but MockMvc records Spring Boot's welcome-page handling as
  a `forward:index.html` `ModelAndView` without actually re-dispatching that forward through the
  real resource handler — a documented MockMvc limitation, not a bug in this configuration
  (confirmed correct by curling a real running instance, which returned the full page with the
  right content type). Fixed by asserting only `200` on `/` and moving every content assertion to
  `GET /index.html`, which MockMvc does execute fully.

- **Bug found and fixed (manual verification)**: `#short-url-output` inherited `width: 100%` from
  the generic `input` rule, but as a flex item inside `.copy-row` without `min-width: 0`, it
  wouldn't shrink below its content size next to the copy button — a classic flexbox overflow on
  narrow viewports, directly against this feature's own SC-003 (no horizontal scrolling on a
  phone-sized screen). Caught by tracing the CSS logic during manual verification, not by a
  screenshot (none was available — see below). Fixed with `flex: 1; min-width: 0;`.

- **Real limitation surfaced by manual verification, not a bug in this feature**: this branch
  (`feature/web-ui`) was deliberately branched off `main`, which doesn't include the custom-alias
  feature (kept on its own branch). Testing the shortening form's alias field against this
  branch's actual backend confirmed the `alias` field is silently ignored (Spring Boot's default
  Jackson configuration doesn't reject unknown JSON properties) — a request with an alias just
  gets an auto-generated code back, and the `ALIAS_TAKEN`/`URL_ALREADY_SHORTENED` error paths in
  `app.js` can never actually be triggered by this branch's API alone. The UI code is written
  correctly for when both features are combined (it sends the right field, handles the right
  error codes) — this is a branch-topology consequence of building the two features separately,
  not something to fix in this feature's own code. Re-verify the alias flow for real once this
  branch and `feature/custom-alias` are combined.

- **Not independently re-verified in a real browser**: with no browser-automation tooling in this
  environment, the copy-to-clipboard interaction, the fallback path, and actual visual appearance
  (spacing, color contrast, responsive layout beyond CSS logic tracing) were not confirmed via an
  actual rendered screenshot. Every other scenario in `quickstart.md` was confirmed via direct
  HTTP calls matching `app.js`'s exact requests and responses, and the markup/CSS was read
  carefully against each requirement — but this is not a substitute for opening the page in a
  real browser before merge.

- **Test-First applies here too**: every `⚠️` block above must be red before its Implementation
  tasks begin, consistent with this project's non-negotiable stance on the two prior features.
- **Manual validation is not optional polish**: this project already treats real end-to-end
  verification (`docker compose` + browser/curl) as load-bearing, not a formality — Phase 5's
  quickstart run-through is that same standard applied here, since no automated tooling can
  exercise the JavaScript itself.
- [P] tasks = different files, no dependency on an incomplete task.
- Commit after each phase or logical group, per the project's incremental-commit convention.
