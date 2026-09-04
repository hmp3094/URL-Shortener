<!--
Sync Impact Report
- Version change: 2.0.0 → 3.0.0 (MAJOR: this branch (link-expiration) builds on the v2.0.0
  amendment already made for click-analytics — see that amendment's own history for the
  click-tracking/analytics-scope/ownership reconciliation — and adds a further backward-
  incompatible change of its own: flipping the expired/never-existed distinguishability
  guarantee)
- Reason for this amendment: v2.0.0 reconciled the constitution with click-analytics as actually
  built. This project has since built link expiration (TTL) on top of click-analytics, which
  introduces its own divergence from what v2.0.0 still asserted: an expired link's fate (retire
  and reissue vs. reactivate) and whether expired-vs-never-existed must be distinguishable. Both
  were deliberate, discussed, and signed off on by the project owner — this amendment reconciles
  them the same way v2.0.0 reconciled click-analytics, rather than letting a second layer of
  drift accumulate.
- Modified principles:
  - I. Short Link Integrity → added a second named exception to the identity-fields-never-change
    guarantee (on top of v2.0.0's click-count/last-accessed one): an *expired* mapping MAY be
    atomically retired and replaced by a fresh mapping (new short code) when its destination URL
    is resubmitted, but MUST NOT be reactivated under its old code — reactivating it would defeat
    the point of it having expired. See `docs/scenarios/ambiguous-link-expiration.md` for the
    decomposition and the rejected alternative (reactivation).
- Modified sections:
  - Reliability & Data Standards → "Expired, deleted, and never-existed short codes MUST be
    distinguishable" (still asserted as of v2.0.0, since click-analytics alone has no expiry
    concept) is now replaced with the opposite, deliberately-chosen guarantee: expired and
    never-existed codes MUST be indistinguishable in the API response (same 404) — consistent
    with Principle I's existing "never reveal why a code doesn't resolve" pattern already applied
    to malformed codes. "Deleted" is dropped entirely — this project still has no deletion
    feature, only expiration.
- Added sections: none
- Removed sections: none
- NOT amended (explicitly, by project-owner instruction, unchanged since v2.0.0):
  - III. Test-First Delivery — still completely unchanged, still NON-NEGOTIABLE. Logged deviation
    (carried forward from v2.0.0, now also covering this branch): link-expiration was implemented
    with tests written after the code, not before, same violation as click-analytics. Still not
    brought into retroactive compliance — logged per the Governance clause's "explicitly
    justified... plan to come into compliance." Test-First applies without exception to all work
    on this project from this point forward.
- Inherited from v2.0.0, already resolved on this branch too (not re-litigated here):
  - Stats-endpoint rate limiting and the load/latency-measurement requirement — both satisfied;
    see `docs/design-decisions.md` and `docs/performance.md` (which also has a link-expiration-
    specific addendum: the expiry check adds no measurable redirect-path cost).
- Still found during this audit, deliberately left open rather than silently resolved (raised to
  the project owner separately, not decided unilaterally in this amendment):
  - Principle V's "contract before implementation" requirement — `docs/api.yaml` was updated
    alongside/after the link-expiration code, not before it, the same pattern as the Test-First
    deviation above.
- Follow-up TODOs:
  - TODO(RATIFICATION_DATE): unchanged from v1.0.0 — original ratification date still unconfirmed.
-->

# URL Shortener Constitution

## Core Principles

### I. Short Link Integrity
Every short code MUST resolve deterministically to exactly one destination URL at any point in time. A mapping's identity fields — short code, destination URL, creation timestamp — MUST NOT change once set. The only exceptions, both system-driven rather than user-authenticated (this project's current scope has no account or ownership model): click-count and last-accessed bookkeeping MAY be updated in place on every redirect, and an *expired* mapping MAY be atomically retired and replaced by a fresh mapping — a genuinely new short code — when its destination URL is resubmitted; it MUST NOT be reactivated under its old code, since that would defeat the point of it having expired. If an authenticated ownership model is introduced later, user-authenticated update/delete MUST be layered on top of this guarantee, not substituted for it. Short codes MUST be generated to avoid collisions via unique constraints enforced at the data layer, not application-level checks alone. Custom aliases MUST be validated for availability before acceptance and MUST be rejected atomically on conflict rather than silently overwritten; the specific validation rules (character set, reserved names, case sensitivity, retention window before reuse) are feature-level decisions defined in that feature's spec, not here. Submitted destination URLs MUST be validated against an allow-list of schemes (http/https) and MUST reject private, loopback, or link-local address ranges before a mapping is created. Rationale: A URL shortener's entire value proposition is trust that a link keeps working, keeps pointing where it was set, and can't be hijacked into redirecting somewhere unsafe — and, symmetrically, that an expired link stays expired rather than being silently revivable.

### II. Redirect Performance & Availability
The redirect path (short code → HTTP redirect) MUST be treated as a distinct, minimal code path separate from management/API operations. Redirect lookups MUST be cache-first where a cache is available, and the redirect service MUST degrade gracefully (e.g., serve a stale-but-valid mapping) rather than fail outright if the primary datastore is briefly unavailable. Click tracking MAY be synchronous with the redirect response when exact counts are prioritized over decoupling the two — this is a deliberate, revisitable trade-off, not a default, and it MUST be measured and documented (see Reliability & Data Standards) and reconsidered if it becomes a bottleneck at scale. Specific latency/availability numbers are set and measured per the Reliability standards below, not asserted here. Rationale: Users experience the shortener almost entirely through redirect latency and uptime. Coupling redirect availability to secondary concerns turns minor incidents into full outages of the core product — but at small scale, an exact count can be worth one extra row update per redirect, provided that trade-off is made explicitly and revisited if the scale assumption stops holding.

### III. Test-First Delivery (NON-NEGOTIABLE)
For every core capability (shortening, redirection, alias validation, analytics ingestion, expiration/deletion), automated tests MUST be written before implementation and MUST fail prior to the corresponding code being written. Contract tests MUST exist for every public API endpoint, covering both success and error responses. Changes to the redirect or analytics-ingestion paths additionally require integration tests against the real datastore (or a faithful equivalent), not mocks alone. Rationale: Regressions in link resolution or silent data loss are hard to detect after the fact; tests-first is the cheapest guardrail against both.

### IV. Analytics Without Compromise
Click analytics MUST be collected without adding synchronous latency or availability risk beyond what's explicitly accepted as a documented trade-off under Principle II, and MUST be resilient to bursts within that design — a spike in clicks MUST NOT cause dropped redirects. The data collected is deliberately minimal: a running click count and a last-accessed timestamp per short link, not a per-click event log, and not referrer, geolocation, or device/user-agent data — richer analytics is a separate, larger feature, not assumed here. Data MUST be attributable to the correct short link. Personally identifiable information beyond what's necessary for coarse aggregate stats MUST NOT be stored. Access control on analytics endpoints (e.g., restricting a link's stats to its owner) MUST be added if and when an account/ownership model exists; it is not asserted against a system that has no notion of an owner today. Rationale: Analytics is a core feature but secondary to the redirect itself, and must never become a reliability or privacy liability in pursuit of richer data than the product actually needs right now.

### V. API Contract Discipline
All core APIs (create, resolve/redirect, retrieve metadata, retrieve analytics, update/delete) MUST be defined by an explicit contract (e.g., OpenAPI) before implementation, and breaking changes MUST be introduced via a new version rather than in place. Responses MUST use consistent error shapes and status codes across endpoints. Rate limiting MUST be applied to link-creation and analytics-read endpoints to prevent abuse; specific limits are documented in the contract. Rationale: The API is consumed by both first-party clients and integrators; undocumented or inconsistent behavior forces every consumer to reverse-engineer it and blocks safe evolution.

## Reliability & Data Standards

Mapping writes MUST be persisted with acknowledgment before a creation response is returned. Any change to the redirect path MUST include locally measured p50/p95 latency numbers from load testing (tool and setup documented), with regressions against the previous baseline called out and justified — targets are measured and reported, not asserted in advance. Analytics ingestion lag (event to queryable) MUST be measured and documented for any change to that path; under the synchronous design permitted by Principle II, this lag is trivially ~0, which satisfies rather than violates this requirement. Expired and never-existed short codes MUST be indistinguishable in the API response — the same outcome, not a status code or body that reveals which one it was — consistent with never revealing why a code doesn't resolve. (This project has no deletion feature; if one is added, its response's distinguishability is a decision for that feature.) Any incident where the redirect path fails or measurably regresses MUST be documented with root cause and a follow-up action before being considered resolved.

## Development Workflow & Quality Gates

This project is developed solo; there is no mandatory second-person code review gate. No AI-generated code MUST merge without the engineer (project owner) reviewing and explicitly signing off — self-review by the same person who prompted the AI satisfies this, no cooldown required. Changes classified as high-impact — schema changes, authentication/authorization logic, rate-limiting logic, URL-validation/SSRF checks, and any code touching persistence — MUST receive explicit human sign-off before merge, regardless of whether the change was AI-generated, human-written, or mixed. Every change to core APIs, the redirect path, or analytics ingestion MUST pass the full contract and integration test suite before merge; there is no merge-and-fix-forward exception for the redirect path or data-integrity tests. PRs (or commits, if working directly on main) touching the redirect or analytics path MUST include load/latency test results or a documented reason load testing doesn't apply. Schema changes to the short-code mapping table MUST include a rollback plan. If a second contributor joins, this section MUST be amended to define shared review/sign-off responsibilities.

## Governance

This constitution supersedes other project practices where a conflict exists. Amendments require: (1) a written proposal with rationale, (2) explicit identification of which principle(s) are added/removed/redefined, and (3) a semantic version bump — MAJOR for backward-incompatible governance or principle changes, MINOR for new principles or materially expanded guidance, PATCH for clarifications and wording fixes. Every PR and design review MUST verify compliance; deviations MUST be explicitly justified with a plan to either come into compliance or amend the constitution. New capabilities MUST extend or compose existing modules, services, or repositories before introducing new ones — new abstractions, services, or datastores not required by a Core Principle or a documented requirement MUST be rejected in review. This document is the primary source of runtime guidance for all Spec Kit workflows (/speckit.specify, /speckit.plan, /speckit.tasks, /speckit.implement) operating on this project.

**Version**: 3.0.0 | **Ratified**: 2026-09-03 | **Last Amended**: 2026-09-03
