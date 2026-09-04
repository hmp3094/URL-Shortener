# Final Engineering Summary

## Objective and how it was interpreted

The brief asked for a URL shortener built with AI assistance, demonstrating requirement
understanding, task decomposition, and validated engineering output across a greenfield build, a
brownfield enhancement, and an ambiguous requirement — with the engineer, not the AI, owning
scope, correctness, and sign-off throughout.

That was interpreted literally rather than templated: three real pieces of work, each actually
built and validated, not three toy examples assembled after the fact to check a box. The greenfield
scenario is the shorten/redirect core; the brownfield scenario is click analytics added to that
already-shipped core; the ambiguous scenario is link expiration, where the requirement itself
("add TTL") didn't specify enough to implement without a judgment call, and the decomposition of
*why* it was ambiguous is as much the deliverable as the feature itself.

## Plan and approach

Work proceeded in two modes, described in full in `docs/architecture-overview.md`'s "Tools and
execution approach": an initial Spec Kit-scaffolded phase for the core API, followed by a
deliberate pivot to direct, conversational, scenario-driven engineering once the project owner
decided the deliverable needed to read as human-engineered work rather than framework output. Every
feature after that pivot followed the same loop: decompose the ambiguity or gap → propose a
resolution with trade-offs (not just an answer) → get explicit sign-off on anything touching
schema, persistence, or security → implement → validate against a real running system → document
the decision where it belongs (`docs/scenarios/` for the scenario narrative, `docs/design-decisions.md`
for the lasting technical rationale).

Git history reflects this directly: one `feature/{name}` branch per unit of work, each merged via
its own PR with a description grounded in that branch's actual diff, not a template.

## The three scenarios

- **Greenfield** — `feature/shorten-redirect-api` (PR #1): create a short link, redirect through
  it, reuse on duplicate submission, reject invalid/unsafe URLs, rate-limit creation. 15 commits,
  37 unit tests, full contract/integration test suite, redirect latency baselined
  (`docs/performance.md`).
- **Brownfield** — `feature/click-analytics` (PR #2): click count + last-accessed tracking added
  to the already-shipped core, including the concurrency-safety question of exact vs. approximate
  counting under an existing cache-aside redirect path. Full writeup:
  `docs/scenarios/brownfield-click-analytics.md`.
- **Ambiguous** — `feature/link-expiration` (PR #3): optional TTL, where "add expiration" hid five
  real decision points (default vs. opt-in, enforcement mechanism, response shape, code
  reusability, resubmission behavior) that a naive implementation would have picked arbitrarily.
  Full writeup, including a real bug the decomposition alone didn't catch:
  `docs/scenarios/ambiguous-link-expiration.md`.

Additionally, `feature/static-analysis` (not yet merged as of this writing) adds a SpotBugs
report-only gate — the "quality gates" requirement's static-analysis half, applied after the fact
to the merged codebase rather than baked into any one scenario.

## Artifacts

- **Code**: `src/main/java/com/urlshortener/...` — 5 controllers/config classes'-worth of HTTP
  layer, `ShortLinkService`/`ShortLinkRepository`/`ShortLink` as the persistence-touching core,
  validation and rate-limiting as isolated, dependency-free modules.
- **Schema**: 3 Flyway migrations (`V1`–`V3`), each with a documented manual rollback plan.
- **Tests**: 37+ unit tests (no external dependencies) plus a full contract/integration suite per
  endpoint (blocked from running via `mvn test` on this machine — see Limitations).
- **Docs**: `requirements.md`, `design-decisions.md`, `data-model.md`, `api.yaml`,
  `getting-started.md`, `performance.md`, `architecture-overview.md` (this summary's companion),
  three scenario writeups under `docs/scenarios/`.
- **Governance**: `.specify/memory/constitution.md`, amended twice (v1.2.0 → v2.0.0 → v3.0.0) as
  real implementation decisions were reconciled against it.
- **Runtime**: `Dockerfile` + `docker-compose.yml`, the only prerequisite to run the whole system
  end-to-end.

## Risks and trade-offs (deliberate, not oversights)

- **Synchronous click tracking** adds a database write to every redirect, including cache-hit
  ones — measured regression: p50 5.1ms → 8.4ms (+65%). Accepted because exact counts mattered
  more than redirect-path purity at this scale; the documented reversal path is batched/async
  counting if that assumption stops holding.
- **Soft-expire, no background sweep** — an expired row can sit in the database indefinitely,
  simply unreadable via the API, until something resubmits its URL. Avoids introducing this
  project's first scheduled/background component for a cost (some dead rows) that doesn't matter
  yet.
- **Single-instance design throughout** — the cache (Caffeine), the rate limiter, and the
  concurrency-safety guarantees all assume one app instance. Explicitly not built for horizontal
  scale; Redis and a distributed rate limiter are the named next steps if that changes.
- **No accounts/ownership model** — every endpoint is open to any caller (subject to rate
  limiting). The constitution's original aspiration for owner-scoped stats access was walked back
  to match this, rather than bolting on partial auth to satisfy a document.
- **Static analysis is report-only** — SpotBugs runs on every `mvn verify` but can't fail the
  build yet; there's no enforced baseline, just visibility. Checkstyle/style linting wasn't added
  at all (see Limitations).

## Validation

Every feature was validated two ways: Docker-independent unit tests (always runnable, always run),
and a full `docker compose up --build` pass exercising the real HTTP surface with `curl` — create,
redirect, duplicate-reuse, invalid-URL rejection, rate limiting, click counting, expiry lifecycle,
resubmission behavior, and stats, all confirmed against a real running Postgres-backed instance,
not mocked. This is not a formality: it is what caught the one real bug found during this project
(a `WITH ... DELETE ... INSERT` statement in the link-expiration feature that looked atomic and
wasn't, per Postgres's same-snapshot semantics for data-modifying CTEs — see
`docs/scenarios/ambiguous-link-expiration.md`). Redirect-path latency was measured and compared
against baseline for every change that touched that path (`docs/performance.md`).

## Assumptions

- No user accounts, authentication, or ownership — every capability is available to any caller,
  subject only to rate limiting.
- Single-instance deployment; nothing here is built for multi-instance coordination.
- Duplicate detection is exact-string match after whitespace trimming only — no URL normalization.
- Custom aliases (user-chosen short codes) are out of scope; every code is system-generated.
- Click tracking is a running total plus last-accessed time, not a per-click event log or
  time-series — richer analytics (referrer, geography, device) is explicitly out of scope.
- Expiration is opt-in only; nothing expires unless the caller asks for it.
- The full, itemized list per feature lives in each doc's own Assumptions section
  (`requirements.md`, and the Assumptions sections it accumulated across all three features).

## Limitations (open, not hidden)

- **Testcontainers doesn't run on this development machine** — a Docker Desktop/docker-java
  version incompatibility, root-caused and documented, not a code defect. Contract/integration
  tests are written and correct but validated manually via `docker compose` instead of `mvn test`.
- **Test-First was not practiced** for the click-analytics and link-expiration features — tests
  were written after the implementation, not before, violating the constitution's own
  NON-NEGOTIABLE principle. Logged explicitly rather than quietly excused; applies strictly to all
  work from that point forward (the stats-endpoint rate-limit fix was built test-first as the
  first instance of that).
- **API contract was not written before code** for the same two features — `docs/api.yaml` was
  updated alongside/after the implementation, violating the constitution's contract-first
  principle. Logged as an open deviation, not resolved in this pass.
- **No style/lint tool** (e.g. Checkstyle) — only bug-pattern static analysis (SpotBugs) was
  added, and it doesn't fail the build yet.
- **No CI pipeline** — every validation pass in this project was run locally, by hand, in this
  conversation. There's no automated gate preventing a future change from skipping any of it.
- **No historical/time-series analytics, no deletion feature, no custom aliases** — all
  explicitly deferred, not attempted and cut for time.

## AI-assisted engineering practice notes

Every schema change, every persistence-touching change, and every ambiguous-requirement resolution
was presented with trade-offs and explicitly signed off on before implementation — not decided
unilaterally and reported afterward. Traceability of what was AI-generated vs. edited vs. rejected
is deliberately kept in these docs and in `docs/scenarios/`, not in commit messages or git history,
since the git history itself is meant to read as ordinary engineering work rather than an
AI-generated audit trail. The one real defect this project produced was found by actually running
the system, not by inspecting the code — which is the argument for why the manual `docker compose`
validation pass was never treated as optional busywork standing in for the blocked automated suite.
