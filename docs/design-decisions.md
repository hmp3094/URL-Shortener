# Design Decisions: Shorten & Redirect API

A Spring Boot 3 (Java 21) REST service exposing two endpoints: `POST /api/links` to create a
short link from a long URL (with exact-match duplicate reuse and scheme/SSRF validation), and
`GET /{code}` to redirect a 6-character, case-insensitive short code to its long URL. Short-link
mappings persist in PostgreSQL (Flyway-managed schema); the redirect path is cache-fronted with
an in-process Caffeine cache. The whole system (app + database) runs via Docker Compose so it
starts end-to-end on any machine with Docker installed, with no manual setup steps.

Every dependency below is included because it's needed for something specific, not by default —
"alternatives considered" records what was deliberately left out to keep the dependency footprint
small.

## Stack

Java 21, Spring Boot 3 (Spring Web, Spring Data JPA, Spring Validation, Spring Boot Actuator),
Maven, PostgreSQL 16, Flyway for migrations.

Flyway specifically because schema changes to this tableneed a documented rollback plan, and 
versioned migrations are the standard way to make thatconcrete and executable.

**Alternative considered**: an embedded/file-based database (H2, SQLite) needs no separate
service, but mapping writes need to be durable and survive a single node/instance failure — an
embedded DB tied to the app container's filesystem doesn't meet that bar as cleanly as a real,
independently-persisted database.

## Short code generation

A Postgres sequence (`short_link_seq`) provides a monotonically increasing number on insert; the
application encodes that value into a fixed-width, 6-character, lowercase alphanumeric string
(zero-padded), used directly as the short code.

Sequence-derived codes are collision-free by construction — no random-generate-and-retry loop
needed. 6 lowercase alphanumeric characters give roughly 2.18 billion possible codes, ample for
this scope. The database's `UNIQUE` constraint on `short_code` stays as defense-in-depth even
though the encoding scheme already guarantees uniqueness by itself.

**Alternative considered**: random generation + collision retry — simpler code, but needs a retry
loop and doesn't get the "collision-free by construction" property. UUID-based codes were also
considered and rejected as too long for a short link's purpose.

## Duplicate URL detection and creation concurrency

`long_url` carries a `UNIQUE` database constraint (plain exact-string match — no normalization
beyond whitespace trimming, done in the application layer before the insert attempt). Creation
uses `INSERT ... ON CONFLICT (long_url) DO NOTHING RETURNING *`; if no row comes back, the service
re-selects the existing row by `long_url` and returns it.

This pattern is atomic at the database level, so two short links can never be created for one
long URL, even under concurrent requests — without needing application-level locking. It reuses
the same unique constraint that already enforces exact-match duplicate detection, rather than
adding a separate mechanism.

**Alternative considered**: optimistic locking / catch-and-retry on a constraint-violation
exception — functionally equivalent but a second round trip and more exception-driven control
flow than a single `ON CONFLICT` statement.

## Redirect caching

Caffeine, used as an in-process cache-aside layer in front of the Postgres lookup: check cache →
on miss, query Postgres → populate cache → return.

The redirect path needs to be fast and cache-first. Caffeine is a single, small, in-memory
library with no separate process to run or operate, which keeps the system easy to run end-to-end
on someone else's machine (no Redis container to add to docker-compose).

**Alternative considered**: Redis — would allow a shared cache across multiple app instances, but
this targets a single-instance deployment; adding a second stateful service isn't worth it at this
scale. Documented as the future path if the service ever needs multiple instances sharing one
cache.

**Known limitation**: because there's exactly one cache tier and no read-through fallback
replica, a code that has never been cached and is requested while Postgres is briefly unavailable
will still fail. The cache makes the *common* case (a previously-resolved code) resilient to a
brief Postgres outage; it can't make an uncached cold lookup succeed with no database at all.

## Click tracking: exact vs. approximate counting

Each short link tracks a click count and a last-accessed timestamp, exposed via
`GET /api/links/{code}/stats`. The count is incremented with a single atomic
`UPDATE ... SET click_count = click_count + 1 WHERE short_code = ?` against Postgres on every
redirect — not batched, not cached, not folded into the `resolve()` lookup used for the redirect
itself.

"Track clicks" doesn't say on its own whether the count needs to be exactly right. That's an
ambiguous requirement, and the two readings pull in different directions given the redirect path
is already cache-aside (see Redirect caching, above):

- **Exact count, on every redirect** (chosen): a synchronous atomic update per redirect. Correct
  under concurrent redirects for the same code — Postgres serializes the row-level update — but it
  reintroduces a database write on every redirect, including ones whose destination lookup was
  served entirely from cache. That partially undercuts the reason the cache exists.
- **Approximate count, batched/async**: buffer increments in memory per instance and flush
  periodically, or fire the update asynchronously off the request thread. Keeps every redirect off
  the database, but the count can lag or be lost entirely if the instance crashes before a flush,
  and a multi-instance deployment would need to merge counts across instances.

Exact was chosen because this is a single-instance deployment at a scale where one extra row
update per redirect is not a meaningful cost, and a stats number that's silently wrong is a worse
outcome than a small, well-understood performance trade-off. If this needed to scale past a single
instance or a much higher redirect volume, batched/async counting is the documented next step. This
decision, and the options weighed against it, are also walked through in
`docs/scenarios/brownfield-click-analytics.md`, which covers the ambiguity as part of that
scenario's execution.

## Rate limiting on link creation and stats

A small hand-rolled in-memory per-IP token bucket (a map keyed by client IP, refilled on a fixed
schedule), applied via a Spring interceptor on both `POST /api/links` (creation) and
`GET /api/links/{code}/stats` (analytics-read) — the two endpoint categories the constitution
requires rate limiting on. Both share one bucket per IP rather than separate, independently-tuned
limits; that's a simplification, not a precision requirement anything currently asks for. The
redirect endpoint itself is deliberately excluded — it's the one path the constitution treats as
distinct and minimal, not a target for this requirement.

The creation/stats endpoints need rate limiting, but the logic itself (a token bucket per key) is
small enough to implement directly without adding a dependency for a genuinely simple,
single-instance need. Exceeding the limit returns `429 Too Many Requests` with a `Retry-After`
header.

**Alternative considered**: a dedicated rate-limiting library (e.g., Bucket4j) — evaluated and
rejected for this scope: it would pull in an extra dependency for the same algorithm this feature
already needs in a few dozen lines of code. If limiting later needs to be distributed across
multiple instances, that's the point to introduce a shared-state library or a Redis-backed
implementation.

## Link expiration

An optional, opt-in expiration (`expiresInSeconds` on creation, stored as a nullable `expires_at`
timestamp). Enforcement is a read-time check, not a background sweep: `resolve()` and
`getStatsSnapshot()` both fetch the row as usual, and the caller (`RedirectController`,
`StatsController`) checks `ShortLink.isExpired()` afterward, throwing the same
`ShortLinkNotFoundException` used for a code that never existed. This mirrors the click-tracking
decision above: the check can't live inside `resolve()` itself, since `@Cacheable` skips that
method's body entirely on a cache hit — but `expiresAt` never changes after creation, so comparing
the (possibly cached) entity's `expiresAt` against "now" at the caller is always correct regardless
of when the entity was cached.

No background job ever proactively deletes an expired row the moment it expires — a row can sit in
the database expired-but-untouched indefinitely; it's simply unreadable through the API from that
point on. The one place expiry causes a real write is resubmission: if the destination URL of an
expired link is submitted again, that row is deleted and a fresh one (new id, new short code)
takes its place, rather than reactivating the expired code — see
`docs/scenarios/ambiguous-link-expiration.md` for why reactivation was rejected.

**Bug found and fixed during implementation**: the first version of the retire-and-replace logic
combined the delete and the insert into one SQL statement (`WITH deleted AS (DELETE ...) INSERT
... ON CONFLICT DO NOTHING`), reasoning that the delete would run first and clear the way for the
insert. It didn't — Postgres executes every data-modifying clause of a single statement's `WITH`
block against the *same* snapshot, so the `INSERT` never saw the `DELETE`'s own effect and still
conflicted on `long_url`, leaving the row deleted but nothing inserted in its place (an
`IllegalStateException` on resubmission after expiry, reproduced against a real database via
`docker compose`, not caught by reasoning about the SQL alone). The fix:
`ShortLinkRepository.deleteIfExpired` and `insertIfLongUrlAbsent` as two separate statements inside
one `@Transactional` method — under Postgres's default READ COMMITTED isolation, each new statement
in a transaction sees the latest committed state (including the transaction's own prior writes), so
the insert correctly sees that the delete already happened.

**Alternative considered**: reactivating an expired link's existing row (same short code, cleared
expiry) instead of retiring it — simpler, no schema/constraint implications at all. Rejected: it
would mean a short code that was supposed to have stopped working could be silently revived just by
someone resubmitting its URL, defeating the point of setting an expiration in the first place.

## Destination URL / SSRF validation

`java.net.URI` parsing to check the scheme is `http`/`https`, followed by DNS resolution and
rejecting the request if any resolved address is loopback, site-local (RFC 1918), or link-local
(covers `169.254.0.0/16`, including the common cloud-metadata SSRF target), using the JDK's own
address-classification methods — no library needed.

Because this service never fetches the destination server-side (it only issues an HTTP redirect
for the client's own browser to follow), the risk being mitigated is specifically link-shortener
abuse — e.g., an automated internal link-preview/unfurl bot that does fetch short links
server-side and could be tricked into reaching an internal address.

**Alternative considered**: a dedicated SSRF-protection library — rejected as unnecessary; the
check needed here is a small, well-understood set of address-range checks already in the JDK.

## API contract and documentation

`docs/api.yaml` (written before any implementation code) is the authoritative contract.
`springdoc-openapi-starter-webmvc-ui` is added as a runtime dependency to serve a live, browsable
Swagger UI generated from the controllers' annotations, and contract tests assert the
implementation's actual responses match the contract.

Writing the contract first, ahead of any code, keeps the API's shape a deliberate decision rather
than whatever the implementation happened to produce. springdoc doesn't replace that; it gives
anyone running the container a working `/swagger-ui.html` to explore and manually exercise the
API without needing example requests on hand already.

**Alternative considered**: hand-maintained-only contract with no runtime doc generation —
lighter on dependencies, but would leave nothing self-explanatory when someone else runs the
container for the first time.

## Redirect HTTP status code

`302 Found` (temporary redirect) for `GET /{code}`.

`301 Moved Permanently` would let browsers cache the redirect, reducing load on the redirect path,
but link lifecycle (expiration/deletion) is a likely near-future feature; a browser-cached `301`
would go stale with no way to invalidate it once that lands. `302` keeps that door open at a
small, well-understood performance cost, revisitable once link lifecycle is finalized.

**Alternatives considered**: `301 Moved Permanently` — rejected for the caching-invalidation
reason above. `307 Temporary Redirect` — functionally similar to `302` here but far less
conventional for URL shorteners, no real benefit.

## Testing

JUnit 5 + Mockito (both already bundled via `spring-boot-starter-test`, no separate dependency)
for unit tests; Testcontainers (a real, disposable Postgres container via Spring Boot's
`@ServiceConnection` support) for integration and contract tests.

Redirect and creation logic need integration tests against a real datastore, not mocks alone —
Testcontainers is the standard way to get a real, disposable Postgres instance in CI and locally
without a shared test database.

**Alternative considered**: an in-memory H2 substitute for tests — rejected because it doesn't
exercise real Postgres behavior (e.g., the `ON CONFLICT` concurrency handling above is
Postgres-specific SQL).

**Known environment issue (unresolved, documented)**: on the machine this was built on, `mvn test`
runs the unit tests successfully, but every Testcontainers-backed contract/integration test fails
at container startup with "Could not find a valid Docker environment." Root cause isolated: this
particular Docker Desktop version's daemon returns a response to the Java Docker client's
connectivity check that the client library can't parse — confirmed via direct HTTP calls that the
daemon itself responds correctly to the exact same request made with a plain HTTP client, over
both the named pipe and a TCP endpoint. This is specific to the Java Docker client library, not a
Docker Desktop misconfiguration, and it doesn't affect `docker compose up --build`, which uses
the Docker CLI directly and was used instead to validate the running application end-to-end (see
`getting-started.md`).

## Containerization

A multi-stage `Dockerfile` (Maven build stage → slim JRE 21 runtime stage) for the application,
plus a `docker-compose.yml` at the repo root defining two services — `app` and `db`
(`postgres:16`) — so `docker compose up --build` runs the entire system on a fresh machine with
only Docker installed. The `app` service waits for `db` to report healthy before starting, backed
by the actuator health endpoint below and Postgres's own built-in healthcheck.

**Alternative considered**: Spring Boot's own docker-compose auto-start integration (starts
service containers declared in a compose file whenever the app runs locally via
`mvn spring-boot:run`) — rejected as redundant on top of a full `docker-compose.yml`; it optimizes
a local inner-loop convenience this project doesn't need urgently, at the cost of one more
dependency. `docker compose up db` already covers "just run Postgres for local dev."

## Observability / health

`spring-boot-starter-actuator`, exposing only `/actuator/health` (used by Docker Compose's
healthcheck) and `/actuator/info`.

A near-zero-cost, standard addition that gives whoever runs the container a concrete way to
confirm the app actually started successfully, and gives docker-compose a real readiness signal
instead of a fixed sleep/delay. Kept deliberately minimal — no metrics/tracing endpoints, since
none are needed here.

## Short-URL response shape

`POST /api/links` returns the created short code *and* a fully-qualified short URL, built from
the incoming request's own scheme/host/port rather than a hardcoded/configured base URL.

This is what a caller actually needs to use or share the link. Deriving it from the incoming
request means it's automatically correct whether the app is reached via `localhost:8080`, a
Docker-mapped port, or a future real domain — no configuration step required.

**Alternative considered**: a configured base-URL property — rejected as an unnecessary manual
setup step for this scope; can be introduced later if the service ever sits behind a reverse
proxy that changes the externally visible host.

## Project layout

Single Maven/Spring Boot module at the repository root — this is an API-only backend with no
frontend. Packages are organized by feature area (`link`, `validation`, `ratelimit`, `config`,
`common`) rather than by technical layer, keeping each concern's controller/service/repository
together.

```text
pom.xml
Dockerfile
docker-compose.yml

src/main/java/com/urlshortener/
├── UrlShortenerApplication.java
├── config/            (cache, rate limiting, OpenAPI, web config)
├── link/               (entity, repository, service, controllers, DTOs)
├── validation/         (destination URL / SSRF validation)
├── ratelimit/           (token bucket limiter + interceptor)
└── common/              (shared exception handling)

src/main/resources/
├── application.yml
└── db/migration/
    └── V1__create_short_links_table.sql

src/test/java/com/urlshortener/
├── contract/    (API-shape tests against the OpenAPI contract)
├── integration/ (Testcontainers-backed Postgres tests)
├── unit/         (pure logic, no Spring context)
└── support/       (shared test base class)
```
