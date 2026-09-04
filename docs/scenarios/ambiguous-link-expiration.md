# Scenario: Ambiguous Requirement — Link Expiration (TTL)

**Type**: Ambiguous requirement. Originally scoped as decomposition-and-decision-only; the
resolutions below were later implemented for real (see Execution and Validation) once the
resolution itself was reviewed and approved.

## The ambiguity

`requirements.md`'s Assumptions section has said, since the very first version of this project:
"Short links don't expire and aren't deletable in this version; lifecycle management (expiration,
deletion, ownership transfer) is deferred to a later feature." That's a deferral, not a
specification — "add expiration" doesn't say what expiration actually means for this system, and
several reasonable readings exist that would produce materially different designs:

1. **Does a link expire by default, or only if asked?** A blanket default duration for every link
   changes behavior for the entire existing test suite and every current assumption ("resolvable
   indefinitely"); opt-in per link changes nothing for callers who don't ask for it.
2. **How is expiry enforced — a background sweep, or a check at read time?** The system has no
   scheduler or background worker anywhere today; introducing one is a bigger architectural change
   than it looks.
3. **What does a redirect to an expired code return?** The same `404` used for a code that never
   existed, or a distinct status (e.g. `410 Gone`) that reveals the code *did* exist?
4. **Is an expired code's short code reusable?** Codes are derived from a monotonically increasing
   sequence (`ShortCodeEncoder`/`short_link_seq`); there's no existing mechanism to reclaim and
   reissue one, so "reusable" is a much bigger change than "expired."
5. **What happens if the same long URL is submitted again after its short link expired?** Does it
   reactivate the expired mapping, or create a new one? This interacts directly with an existing,
   already-tested guarantee: requirement #4 says a duplicate submission "returns the existing short
   link" — if "existing" silently comes to mean "existing and not expired," that's a change to a
   requirement that's already shipped and tested, not just an addition.

None of these have a "correct" answer from the phrase "add TTL" alone — each is a real fork with
consequences for the schema, the redirect endpoint, and the duplicate-detection logic that's
already in production.

## Decomposition

1. Enumerate the hidden decision points above rather than picking an implementation and finding
   out about the conflicts (like #5) partway through.
2. For each, weigh the options against what already exists: no background workers anywhere in this
   system, a hard existing guarantee around duplicate detection, and the project's standing
   preference for the smallest change that resolves the actual requirement (no speculative
   generality).
3. Propose one resolution per decision point, with rationale — treating this as a design decision
   to be signed off on, the same way the click-counting trade-off was, before any schema or code
   would be touched.
4. Explicitly flag anywhere a resolution would change already-shipped, already-tested behavior
   (see #5) rather than letting that slide in unannounced.

## Resolution for each decision point (as implemented)

| # | Decision | Proposed resolution | Why |
|---|---|---|---|
| 1 | Default vs. opt-in expiry | **Opt-in**: no expiry unless the creation request supplies one (e.g. an optional `expiresInSeconds` or `expiresAt` field) | Zero behavior change for every existing link, test, and documented assumption; a blanket default would silently break "resolvable indefinitely" for links nobody asked to expire |
| 2 | Enforcement mechanism | **Read-time check** (soft-expire): a nullable `expires_at` column, checked in `resolve()` before returning a hit, no background job | Matches how this project has avoided adding infrastructure it doesn't need elsewhere (no Redis, no external rate-limit library); a sweep job would be the first scheduled/background component in the system |
| 3 | Response for an expired code | **Same `404`** used for a nonexistent code, not a distinct `410` | Consistent with an existing, deliberate choice already made for malformed-vs-nonexistent codes: never reveal *why* a code doesn't resolve. Extending that principle to expired-vs-nonexistent is more consistent than introducing an exception to it |
| 4 | Code reusability | **Not reusable** — an expired code is retired, not recycled | Reclaiming codes would require redesigning the sequence-based ID/encoding scheme (`ShortCodeEncoder`) to track freed values, a disproportionate change for what TTL is actually asking for |
| 5 | Re-submitting the long URL after expiry | **Issue a new short link**; treat "expired" as terminal, not reversible | Simpler mental model (expiry doesn't silently un-happen) and avoids a live short link's target changing out from under anyone who already has the old code. The explicit cost: requirement #4 ("duplicate returns the existing short link") would need to be reworded to "the existing, unexpired short link" — a real requirement change this decision forces, called out here rather than made silently |

## Execution

Built on `feature/link-expiration` (branched from `feature/click-analytics`, so the two features
compose rather than conflict): a `V3` migration adding a nullable `expires_at`; `CreateLinkRequest`
gaining an optional, validated `expiresInSeconds` field (`@Positive`, capped at 31,536,000 seconds
/ 365 days); `ShortLink.isExpired()` as the single point of truth for the check; the redirect and
stats controllers each checking it after fetching, for the same reason click-counting couldn't live
inside `resolve()` — that method is `@Cacheable` and skips its body entirely on a hit, so the check
has to happen at the caller, not inside it.

Resolution #5 (issue a new short link on resubmission, never reactivate) turned out to be harder to
implement correctly than resolution #4's write-up estimated. The first attempt combined the retire
and the insert into one SQL statement (a `WITH ... DELETE ... INSERT` CTE), which looked atomic and
correct on paper — decomposition alone didn't surface the problem. It was wrong: Postgres runs
every data-modifying clause of one statement's `WITH` block against the same snapshot, so the
insert never saw the delete's own effect and still conflicted, leaving the row deleted with nothing
inserted in its place. This was only caught by actually running it against a real database via
`docker compose` — a `500` and an `IllegalStateException` on the exact resubmission-after-expiry
path this scenario is about. Fixed by splitting it into two statements
(`ShortLinkRepository.deleteIfExpired` then `insertIfLongUrlAbsent`) inside one transaction, which
Postgres's READ COMMITTED isolation handles correctly — see the "Link expiration" entry in
`docs/design-decisions.md` for the full explanation. Everything else (the migration, the entity,
the validation, the two controllers) matched its design-time write-up without needing changes.

## Validation

- Unit: `ShortLinkExpiryTest` covers `isExpired()` for no-expiry, future-expiry, and past-expiry
  cases directly, no database needed.
- End-to-end via `docker compose`, exercising every resolution above against a real Postgres
  instance: a link created with a 2-second expiry redirects successfully immediately, then returns
  `404` for both redirect and stats once expired; resubmitting its URL afterward returns a
  genuinely different short code, and the old code stays `404` permanently; resubmitting a
  *still-live* link's URL continues to return the same code, unchanged; invalid `expiresInSeconds`
  values (`0`, negative, `31536001`) are rejected with `400`.
- The integration/contract tests for this feature (`LinkExpirationIntegrationTest`,
  additions to `LinkCreationContractTest`) are written and structurally exercise the same cases,
  but — like the rest of this project's Testcontainers-backed suite — can't run via `mvn test` on
  this machine (the pre-existing Docker Desktop/Testcontainers issue documented in
  `design-decisions.md`); the `docker compose` pass above is the substitute validation, and is what
  actually caught the CTE bug.
