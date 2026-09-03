<!--
Sync Impact Report
- Version change: 1.1.0 → 1.2.0 (minor: new principle content + new governance rule, none removed)
- Modified principles:
  - I. Short Link Integrity → added a MUST requirement to validate destination URLs against an
    allow-list of schemes and reject private/loopback/link-local addresses (SSRF protection);
    deferred alias-specific validation rules (charset, reserved names, retention window) to
    feature-level specs rather than asserting them here.
  - II. Redirect Performance & Availability → removed the asserted "sub-10ms-median" figure;
    numeric targets are now set and measured in the Reliability & Data Standards section instead
    of being asserted in the principle itself.
- Modified sections:
  - Reliability & Data Standards → replaced fixed SLA numbers (99.9% availability, p95/p50
    latency, ingestion-lag targets) with a requirement to measure and report p50/p95 latency and
    ingestion lag per change, with regressions against the previous baseline called out and
    justified, rather than asserting fixed targets in advance.
  - Development Workflow & Quality Gates → added URL-validation/SSRF checks to the high-impact
    change classification requiring explicit human sign-off.
  - Governance → added an anti-abstraction rule: new capabilities MUST extend or compose existing
    modules/services/repositories before introducing new ones.
- Added sections: none
- Removed sections: none
- Templates requiring updates:
  - .specify/templates/plan-template.md ⚠ pending manual review for Constitution Check gate alignment
  - .specify/templates/spec-template.md ⚠ pending manual review for analytics/reliability requirement coverage
  - .specify/templates/tasks-template.md ⚠ pending manual review for test-first task ordering
- Follow-up TODOs:
  - TODO(RATIFICATION_DATE): Original ratification date is unknown; set to the date v1.0.0 was
    adopted (2026-09-03) pending confirmation from the project owner if an earlier date applies.
-->

# URL Shortener Constitution

## Core Principles

### I. Short Link Integrity
Every short code MUST resolve deterministically to exactly one destination URL at any point in time, and that mapping MUST be immutable once created unless an explicit, authenticated update or deletion operation is performed by the owning user. Short codes MUST be generated to avoid collisions via unique constraints enforced at the data layer, not application-level checks alone. Custom aliases MUST be validated for availability before acceptance and MUST be rejected atomically on conflict rather than silently overwritten; the specific validation rules (character set, reserved names, case sensitivity, retention window before reuse) are feature-level decisions defined in that feature's spec, not here. Submitted destination URLs MUST be validated against an allow-list of schemes (http/https) and MUST reject private, loopback, or link-local address ranges before a mapping is created. Rationale: A URL shortener's entire value proposition is trust that a link keeps working, keeps pointing where it was set, and can't be hijacked into redirecting somewhere unsafe. These are invariants regardless of which specific alias rules a given feature chooses.

### II. Redirect Performance & Availability
The redirect path (short code → HTTP redirect) MUST be treated as a distinct, minimal code path separate from management/API operations. Redirect lookups MUST be cache-first where a cache is available, and the redirect service MUST degrade gracefully (e.g., serve a stale-but-valid mapping) rather than fail outright if the primary datastore is briefly unavailable. Redirect endpoints MUST NOT block on analytics writes; click tracking MUST be asynchronous relative to the redirect response. Specific latency/availability numbers are set and measured per the Reliability standards below, not asserted here. Rationale: Users experience the shortener almost entirely through redirect latency and uptime. Coupling redirect availability to secondary concerns turns minor incidents into full outages of the core product.

### III. Test-First Delivery (NON-NEGOTIABLE)
For every core capability (shortening, redirection, alias validation, analytics ingestion, expiration/deletion), automated tests MUST be written before implementation and MUST fail prior to the corresponding code being written. Contract tests MUST exist for every public API endpoint, covering both success and error responses. Changes to the redirect or analytics-ingestion paths additionally require integration tests against the real datastore (or a faithful equivalent), not mocks alone. Rationale: Regressions in link resolution or silent data loss are hard to detect after the fact; tests-first is the cheapest guardrail against both.

### IV. Analytics Without Compromise
Click analytics (timestamp, referrer, coarse geolocation, device/user-agent class) MUST be collected without adding synchronous latency or availability risk to the redirect path, and MUST be resilient to bursts — a spike in clicks MUST NOT cause dropped redirects, even if analytics events are queued, batched, or sampled under load. Data MUST be attributable to the correct short link and time-bucketed consistently (UTC). Personally identifiable information beyond what's necessary for coarse aggregate stats MUST NOT be stored, and analytics endpoints MUST enforce that only the link owner can view a given link's stats. Rationale: Analytics is a core feature but secondary to the redirect itself, and must never become a reliability or privacy liability in pursuit of richer data.

### V. API Contract Discipline
All core APIs (create, resolve/redirect, retrieve metadata, retrieve analytics, update/delete) MUST be defined by an explicit contract (e.g., OpenAPI) before implementation, and breaking changes MUST be introduced via a new version rather than in place. Responses MUST use consistent error shapes and status codes across endpoints. Rate limiting MUST be applied to link-creation and analytics-read endpoints to prevent abuse; specific limits are documented in the contract. Rationale: The API is consumed by both first-party clients and integrators; undocumented or inconsistent behavior forces every consumer to reverse-engineer it and blocks safe evolution.

## Reliability & Data Standards

Mapping writes MUST be persisted with acknowledgment before a creation response is returned. Any change to the redirect path MUST include locally measured p50/p95 latency numbers from load testing (tool and setup documented), with regressions against the previous baseline called out and justified — targets are measured and reported, not asserted in advance. Analytics ingestion lag (event to queryable) MUST be measured and documented for any change to that path. Expired, deleted, and never-existed short codes MUST be distinguishable in the API response — the exact status codes and body are defined in the API contract, not here. Any incident where the redirect path fails or measurably regresses MUST be documented with root cause and a follow-up action before being considered resolved.

## Development Workflow & Quality Gates

This project is developed solo; there is no mandatory second-person code review gate. No AI-generated code MUST merge without the engineer (project owner) reviewing and explicitly signing off — self-review by the same person who prompted the AI satisfies this, no cooldown required. Changes classified as high-impact — schema changes, authentication/authorization logic, rate-limiting logic, URL-validation/SSRF checks, and any code touching persistence — MUST receive explicit human sign-off before merge, regardless of whether the change was AI-generated, human-written, or mixed. Every change to core APIs, the redirect path, or analytics ingestion MUST pass the full contract and integration test suite before merge; there is no merge-and-fix-forward exception for the redirect path or data-integrity tests. PRs (or commits, if working directly on main) touching the redirect or analytics path MUST include load/latency test results or a documented reason load testing doesn't apply. Schema changes to the short-code mapping table MUST include a rollback plan. If a second contributor joins, this section MUST be amended to define shared review/sign-off responsibilities.

## Governance

This constitution supersedes other project practices where a conflict exists. Amendments require: (1) a written proposal with rationale, (2) explicit identification of which principle(s) are added/removed/redefined, and (3) a semantic version bump — MAJOR for backward-incompatible governance or principle changes, MINOR for new principles or materially expanded guidance, PATCH for clarifications and wording fixes. Every PR and design review MUST verify compliance; deviations MUST be explicitly justified with a plan to either come into compliance or amend the constitution. New capabilities MUST extend or compose existing modules, services, or repositories before introducing new ones — new abstractions, services, or datastores not required by a Core Principle or a documented requirement MUST be rejected in review. This document is the primary source of runtime guidance for all Spec Kit workflows (/speckit.specify, /speckit.plan, /speckit.tasks, /speckit.implement) operating on this project.

**Version**: 1.2.0 | **Ratified**: 2026-09-03 | **Last Amended**: 2026-09-03
