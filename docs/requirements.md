# Requirements: Shorten & Redirect API

## What this covers

A URL shortener with two core capabilities: an API to create a short link from a long URL, and a
redirect endpoint that resolves a short link back to its destination. Submitting a URL that's
already been shortened returns the existing short link instead of creating a duplicate, and
malformed or unsafe URLs are rejected before anything is created.

## User scenarios

**Creating a short link.** A user submits a long URL and gets back a short link they can share.
This is the core of the product — without creation, there's nothing to redirect. Once a short
link is returned, it must be immediately usable; there's no delay before it becomes active.

**Resolving a short link.** Anyone who visits a short link is redirected to the original long
URL. Visiting a code that doesn't exist returns a clear "not found" response rather than a
generic error or a silent failure.

**Reusing a short link for a duplicate URL.** If a long URL has already been shortened, submitting
it again returns the same short link rather than creating a new one — this prevents short-code
sprawl (many different codes pointing at the same destination) and matches how established
shortening services behave. Submitting the same URL a third or fourth time keeps returning that
same original short link (idempotent).

**Rejecting invalid submissions.** Anything that isn't a usable, safe URL is refused, with an
explanation of why. This keeps short links from breaking, and stops the service being used to
disguise unsafe destinations or reach internal-only addresses.

## Edge cases

- The same long URL submitted with a trailing slash, different query-string order, different
  casing anywhere (including scheme or host), or a different fragment is treated as a *different*
  URL and gets its own short link — see the duplicate-matching rule below.
- An extremely long submitted URL (several kilobytes) is rejected outright rather than silently
  truncated.
- Two concurrent requests for the same brand-new long URL must not both succeed: exactly one
  mapping gets created, and the "losing" request receives the same short link as the winning one,
  not an error.
- If a generated short code ever collided with one already in use, the system would need to
  detect that and generate a different code rather than overwrite the existing mapping (in
  practice this can't happen — see Design Decisions).
- Requesting a short code with an unexpected format or length returns the same "not found"
  response as a nonexistent-but-valid-looking code, so the response never reveals whether the
  format itself was invalid.

## Requirements

1. Accept a long URL via the creation API and, if valid, return a short code/link that uniquely
   identifies it.
2. Validate that a submitted URL is well-formed and uses an allowed scheme (`http` or `https`)
   before creating any mapping; reject anything else.
3. Reject submitted URLs that resolve to private, loopback, or link-local addresses, so the
   redirect endpoint can't be used to reach internal destinations.
4. Detect when a submitted long URL is an exact string match (after trimming leading/trailing
   whitespace only — no other normalization) of one that already has a short link, and return the
   existing short link instead of creating a duplicate.
5. Guarantee that duplicate detection and short-link creation can't both succeed for the same
   long URL at the same time — no two short links for one long URL, even under concurrent
   requests.
6. Provide a redirect endpoint that, given a valid, existing short code, responds with an HTTP
   redirect to the associated long URL. Matching is case-insensitive — codes are normalized to
   lowercase, so `aB3xY9` and `ab3xy9` resolve to the same short link (simpler for people typing
   or reading codes by hand, at the cost of a smaller effective keyspace).
7. Respond with a distinct "not found" outcome when a redirect is requested for a short code that
   doesn't exist, with no side effects.
8. Generate short codes automatically as 6-character, lowercase, alphanumeric strings; codes must
   be unique across all short links, with collisions never overwriting an existing mapping.
9. Reject a creation request — without creating any mapping — when the submitted URL fails
   validation (missing, malformed, disallowed scheme, or disallowed target address), and report a
   clear reason.
10. Limit how many creation requests a single caller can make in a given time window, to prevent
    abuse of the creation endpoint.
11. A created short link remains resolvable indefinitely (no automatic expiration in this
    version).

## Data

**Short Link** — the mapping between a system-generated short code and a destination long URL.
Attributes: short code (unique, 6-character lowercase alphanumeric string), destination long URL,
creation timestamp. Each long URL maps to at most one short link.

## Measurable outcomes

- A user can submit a long URL and get back a working short link in a single request.
- Visiting any valid short link reaches the original destination 100% of the time (excluding
  cases where the destination itself is unreachable).
- Submitting the same long URL any number of times always returns the same short link — zero
  duplicates are ever created for one long URL.
- 100% of invalid or disallowed URL submissions are rejected with a clear reason, and create zero
  short links.
- Requesting a short code that was never created always produces a clear "not found" outcome
  rather than an error page or unrelated failure.

## Assumptions

- No user accounts or authentication: short-link creation is available to any caller (subject to
  rate limiting), and duplicate detection is global rather than per-user.
- Custom aliases (user-chosen short codes) are out of scope; all short codes are system-generated.
  This could be a separate feature later.
- Duplicate detection is a plain exact-string match after trimming whitespace only — no scheme,
  host, or path normalization. URLs that differ by trailing slash, query-string order, casing, or
  fragment are treated as distinct rather than silently merged, since guessing wrong here could
  send someone to the wrong destination.
- Short links don't expire and aren't deletable in this version; lifecycle management
  (expiration, deletion, ownership transfer) is deferred to a later feature.
- Click analytics and usage statistics are explicitly out of scope for this version and would be
  a separate feature.
- Specific numeric rate-limit thresholds and exact HTTP status codes were left as implementation
  decisions rather than fixed upfront (see Design Decisions).
