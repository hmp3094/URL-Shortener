# Feature Specification: Custom Alias on Link Creation

**Feature Branch**: `feature/custom-alias`

**Created**: 2026-09-04

**Status**: Draft

**Input**: User description: "Add the ability to specify a custom alias when creating a short link. A user creating a link via POST /api/links may optionally provide a custom short code (alias) instead of receiving a randomly generated one. The alias must be validated for availability before acceptance and rejected atomically on conflict (no silent overwrite of an existing mapping). Validation rules for the alias (allowed character set, length bounds, reserved/disallowed names, case sensitivity, and any retention window before a deleted/expired alias can be reused) need to be defined as part of this feature. This must remain consistent with Principle I of the constitution, which already anticipates custom aliases and defers these exact validation rules to this feature's spec."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Claim a memorable alias when shortening a link (Priority: P1)

A user shortening a URL wants the resulting link to be memorable or on-brand (e.g. `.../summer-sale`) instead of an opaque random code, so they supply their own alias at creation time and get it back immediately if it's available.

**Why this priority**: This is the entire feature — without it, there is nothing to test or ship.

**Independent Test**: Submit a create request with a URL and an available alias; verify the response's short link uses exactly that alias and that the alias resolves (redirects) to the submitted URL.

**Acceptance Scenarios**:

1. **Given** an alias that matches no existing mapping, **When** a link is created with that alias, **Then** the response's short code is exactly the requested alias and the link is immediately resolvable.
2. **Given** no alias is supplied, **When** a link is created, **Then** the system behaves exactly as it does today — a random code is generated, with no change to existing callers.

---

### User Story 2 - Reject a taken alias without overwriting the existing link (Priority: P1)

A user requests an alias that's already in use by a live link. They need an immediate, unambiguous rejection — never a silent takeover of someone else's mapping, and never an ambiguous success that quietly gave them something other than what they asked for.

**Why this priority**: Silently overwriting or misattributing an existing mapping would violate the core trust guarantee of the product (Principle I) and is the exact failure mode the constitution calls out by name for this feature.

**Independent Test**: Create a link with alias `promo`; submit a second, different URL with the same alias `promo`; verify the second request is rejected (no mapping created or altered) and the first mapping is untouched and still resolves to its original URL.

**Acceptance Scenarios**:

1. **Given** a live mapping already exists for alias `promo`, **When** another creation request supplies `promo` as its alias, **Then** the request is rejected with a conflict error and the existing mapping is unchanged.
2. **Given** two concurrent creation requests both supply the same available alias, **When** both are processed, **Then** exactly one succeeds and the other receives a conflict error — never both succeeding, and never a corrupted or overwritten mapping.

---

### User Story 3 - Get a clear reason when an alias is invalid (Priority: P2)

A user supplies an alias that isn't well-formed (wrong characters, wrong length) or that collides with a name the system reserves for its own routes. They need to know specifically why it was rejected so they can pick a valid one without guessing.

**Why this priority**: Important for usability but secondary to the core claim/reject mechanics of P1/P1 above — a user can still succeed by trial and error without this, just with more friction.

**Independent Test**: Submit creation requests with an alias containing a disallowed character, an alias that's too short, an alias that's too long, and an alias equal to a reserved word; verify each is rejected with a distinct, specific reason.

**Acceptance Scenarios**:

1. **Given** an alias containing a character outside the allowed set, **When** a link is created, **Then** the request is rejected with a validation error naming the character-set rule.
2. **Given** an alias shorter than the minimum or longer than the maximum allowed length, **When** a link is created, **Then** the request is rejected with a validation error naming the length rule.
3. **Given** an alias equal to a reserved system name (e.g. `api`, `actuator`), **When** a link is created, **Then** the request is rejected as reserved, distinct from an "already taken" conflict.

---

### Edge Cases

- Two aliases that differ only in case (`Promo2026` vs `promo2026`) MUST be treated as the same alias, consistent with the existing case-insensitive matching used for auto-generated codes.
- An alias that happens to be the same shape as an auto-generated code (e.g. exactly 6 alphanumeric characters) MUST still be rejected if it collides with any existing code — custom aliases and auto-generated codes share one namespace, never two independent ones.
- A request that supplies both a custom alias and an expiration MUST apply both together (they are independent, orthogonal options).
- A malformed alias (bad characters/length/reserved word) MUST be rejected before any availability check is performed, since there's no reason to look up a name that could never be legal.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow an optional custom alias to be supplied when creating a short link, as an alternative to an auto-generated code.
- **FR-002**: When no alias is supplied, link creation MUST behave exactly as it does today (random code generation), with no change to existing callers or response shape beyond the new optional input field.
- **FR-003**: The system MUST validate a supplied alias's format (character set and length) before checking its availability, and MUST reject a malformed alias with an error that identifies which specific rule was violated.
- **FR-004**: Allowed alias characters MUST be limited to ASCII letters, digits, hyphens, and underscores. Alias length MUST be between 3 and 32 characters inclusive.
- **FR-005**: The system MUST reject an alias that matches a reserved name (system route segments such as `api`, `actuator`, `swagger-ui`, `health`), distinguishing this rejection reason from an "already taken" conflict.
- **FR-006**: Alias matching MUST be case-insensitive, consistent with existing short-code resolution — `Promo2026` and `promo2026` are the same alias and cannot both be claimed.
- **FR-007**: The system MUST check alias availability and create the mapping as a single atomic operation, such that two concurrent requests for the same alias can never both succeed. The request that loses MUST receive a clear conflict error; the winning mapping MUST NOT be altered or overwritten.
- **FR-008**: A link created with a custom alias MUST behave identically to one created with an auto-generated code for every existing capability — redirection, click statistics, and expiration.
- **FR-009**: When a creation request's destination URL already has a live (non-expired) short link under a different code, and the request also supplies a custom alias, the system MUST reject the request with a conflict error rather than silently discarding the requested alias.
- **FR-010**: Once the short link holding a given alias is no longer live (its expiration has passed), that alias MUST become available for a new request to claim immediately — no additional holding period.
- **FR-011**: Custom aliases and auto-generated codes MUST share a single namespace: an auto-generated code MUST never be issued if it collides with an already-claimed custom alias, and a custom alias request MUST be rejected as taken if it collides with an existing auto-generated code.
- **FR-012**: Custom-alias creation requests MUST be subject to the same creation rate-limiting protection already applied to link creation generally — no separate or relaxed limit.

### Key Entities

- **Short Link**: Existing entity, extended conceptually — its short code may now originate from a caller-supplied alias instead of exclusively from random generation. No new identity fields; the origin of the code (chosen vs. generated) does not change how the mapping behaves afterward.
- **Reserved Name List**: The set of alias values that can never be claimed because they would collide with the system's own routes (e.g. `api`, `actuator`, `swagger-ui`, `health`). Conceptual policy data, not a new business entity.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can obtain a short link with a self-chosen, memorable alias in a single request, with no separate availability check required beforehand.
- **SC-002**: Zero instances of two live short links ever sharing the same alias (case-insensitive) — verified continuously via the atomic conflict guarantee, not just at creation time.
- **SC-003**: A user whose alias is rejected can identify the specific reason (taken, invalid format, or reserved) from the response alone, without needing to guess or contact support.
- **SC-004**: Existing link-creation behavior for requests that don't supply an alias is unchanged — 100% backward compatible.

## Assumptions

- Character set (letters, digits, hyphens, underscores) and length bounds (3-32 characters) are chosen to support human-readable, brandable aliases while staying short and avoiding characters that would need URL-encoding or could be confused with path separators.
- The reserved-name list starts with the application's own known top-level route segments (`api`, `actuator`, `swagger-ui`, `health`) and is expected to be extended if new system routes are added later; the exact storage/extension mechanism is a planning-level decision, not a business rule.
- Alias matching reuses the existing case-insensitive comparison already applied to auto-generated codes, for consistency.
- No authentication/ownership model exists yet (per the constitution), so alias claiming is first-come-first-served for any caller, exactly like today's link creation — this feature does not change who can create or claim what.
- This feature does not introduce a deletion capability; "no longer live" in FR-010 currently means "expired," since deletion does not yet exist as a feature.
