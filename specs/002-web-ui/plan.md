# Implementation Plan: Web UI for Shortening and Stats Lookup

**Branch**: `feature/web-ui` | **Date**: 2026-09-06 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/002-web-ui/spec.md`

## Summary

Serve a single static page from the existing Spring Boot app that lets a visitor shorten a URL
(with optional alias and expiration) and look up an existing code's stats, calling the JSON API
that already exists today with no changes to it. No framework, no build tooling, no new backend
service — hand-written HTML/CSS/JS served as static files, in keeping with this project's
established preference for the smallest dependency footprint that does the job (the same
reasoning that chose an in-process cache over Redis and a hand-rolled rate limiter over a
library). The only backend change of substance is re-enabling Spring Boot's static resource
serving, which is off today.

## Technical Context

**Language/Version**: HTML5, CSS3, vanilla JavaScript (ES2020+, no transpilation) for the UI;
Java 21/Spring Boot for the one configuration change that serves it

**Primary Dependencies**: None added. No frontend framework, no bundler, no package manager — the
page is hand-written and served as-is

**Storage**: N/A — the page holds no state of its own; every value shown comes from a live call
to the existing API

**Testing**: JUnit 5 + Spring Boot Test (MockMvc), same as the rest of this project — a contract
test asserting the page and its assets are served with the expected shape; interactive behavior
(form submission, copy-to-clipboard, error rendering) verified manually in a real browser against
a running instance, the same way this project already treats `docker compose` validation as
load-bearing rather than a formality

**Target Platform**: Same Spring Boot service, same deployment — no new runtime, no separate
static host

**Project Type**: Single web service (existing layout) — a `static/` resources directory is new,
everything else is unchanged

**Performance Goals**: Not applicable to the page itself (a handful of small static files); the
existing redirect-path performance targets are unaffected since this feature does not touch
`RedirectController`, its route pattern, or its cache

**Constraints**: No new capability beyond what `POST /api/links` and
`GET /api/links/{code}/stats` already provide (Governance — extend existing capability via its
existing contract, don't grow the API to serve the UI)

**Scale/Scope**: One page, three static files (markup, styles, behavior) — no scale assumptions
change

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design below.*

| Principle | Check | Result |
|---|---|---|
| I. Short Link Integrity | Untouched — the UI calls the existing create/resolve/stats behavior exactly as documented, no new write path | N/A |
| II. Redirect Performance & Availability | `RedirectController`'s mapping and cache are not touched by this feature; static assets can never collide with a short code — every static filename contains a `.`, which is outside the alias character set, the same property that already protects `/swagger-ui.html` today | PASS |
| III. Test-First Delivery (NON-NEGOTIABLE) | A contract test for the page/assets being served correctly is written and failing before the static files exist; interactive JS behavior has no automated-test tooling in this project (no Node/browser-test runner) and is validated manually instead, documented as such rather than silently skipped | GATE CARRIED FORWARD to tasks.md |
| IV. Analytics Without Compromise | Untouched — the UI reads stats through the existing endpoint, adds no new data collection | N/A |
| V. API Contract Discipline | No new JSON API surface is introduced — `docs/api.yaml` needs no changes. The one new HTTP-visible surface (`GET /` and its static assets) is documented in `docs/architecture-overview.md`/`docs/getting-started.md` instead, since it isn't a JSON API endpoint | PASS |
| Reliability & Data Standards | No change to mapping writes, redirect latency, or the expired/never-existed indistinguishability guarantee | N/A |
| Development Workflow & Quality Gates | Touches configuration (`application.yml`) but not persistence, authentication, rate-limiting, or URL-validation logic, so it does not meet this project's own bar for mandatory high-impact sign-off — still worth a review pass as with any change | PASS |

No violations requiring the Complexity Tracking table: this extends the existing service with a
resources directory and one configuration change, introducing no new module, dependency, or
datastore.

## Project Structure

### Documentation (this feature)

```text
specs/002-web-ui/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output (kept minimal — no data model of its own)
├── quickstart.md        # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit-tasks — not created here)
```

### Source Code (repository root)

```text
src/main/resources/
├── static/
│   ├── index.html       # new — the single page (shorten form + stats lookup)
│   ├── style.css        # new
│   └── app.js           # new — form handling, fetch() calls, copy-to-clipboard, error rendering
└── application.yml      # edited — remove `spring.web.resources.add-mappings: false`

src/test/java/com/urlshortener/contract/
└── WebUiContractTest.java   # new — asserts the page and its assets are served as expected

docs/
├── architecture-overview.md   # edited — note the served UI alongside the existing HTTP layer
├── getting-started.md         # edited — "open the UI" alongside the existing curl walkthrough
└── design-decisions.md        # edited — record the "hand-written page, no framework" decision
                                #          and the static-resource-serving re-enablement
```

**Structure Decision**: Everything lives in the existing single-service layout. The only new
directory is `src/main/resources/static/`, which is exactly where Spring Boot expects static
content to live by convention — no custom resource-handler wiring needed once the blanket
`add-mappings: false` override is removed.

## Complexity Tracking

*No entries — no Constitution Check violations require justification.*

## Post-Design Constitution Re-Check

Re-verified after Phase 1 (research.md, data-model.md, quickstart.md): the route-collision
analysis in Research §1 confirms the redirect path's safety property (dot-containing paths are
structurally excluded from the alias character set) extends to this feature's static assets
without needing a new reserved-name entry or any change to `RedirectController`. Nothing in the
concrete design introduces a data model, a new API surface, or a dependency — no new gate is
triggered. No Complexity Tracking entries needed.
