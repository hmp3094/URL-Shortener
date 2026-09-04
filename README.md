# URL Shortener

A URL shortener service. This repository implements the core API: creating short links from long
URLs (optionally with an expiration), resolving them via redirect, and reporting click statistics
per link.

**Start here**: [docs/engineering-summary.md](docs/engineering-summary.md) (plan, artifacts, risks,
validation, assumptions, limitations) and [docs/architecture-overview.md](docs/architecture-overview.md)
(components, control flow, key decisions) are the two documents that summarize the whole project —
read those first if you're short on time.

## Where each deliverable lives

| Deliverable | Where |
|---|---|
| Working prototype (runnable end-to-end) | `docker compose up --build` — see "Run it" below |
| Architecture overview (components, tools, execution approach, control flow, key decisions) | [docs/architecture-overview.md](docs/architecture-overview.md) |
| Three scenarios: greenfield, brownfield, ambiguous (decomposition, execution, validation) | "Three scenarios" table below, [docs/scenarios/](docs/scenarios/) |
| Setup instructions | [docs/getting-started.md](docs/getting-started.md), "Run it" / "Try it" below |
| Testing approach, limitations, and trade-offs | [docs/engineering-summary.md](docs/engineering-summary.md) ("Validation", "Limitations"), [docs/design-decisions.md](docs/design-decisions.md) |

## Engineering highlights

The decisions worth reading, not just the code that resulted from them:

- **Exact click counts over redirect-path purity** — click tracking is synchronous, adding a real,
  measured latency cost (p50 5.1ms → 8.4ms, +65%) to every redirect rather than batching or going
  async. Accepted because correctness mattered more than redirect-path speed at this scale
  ([docs/design-decisions.md](docs/design-decisions.md)).
- **Retire-and-reissue, not reactivate, on expired-link resubmission** — an expired short code
  never comes back to life; resubmitting its URL creates a new code instead. Reactivating it would
  quietly defeat the point of setting an expiration
  ([docs/scenarios/ambiguous-link-expiration.md](docs/scenarios/ambiguous-link-expiration.md)).
- **A real concurrency bug found by running the system, not by reading the code** — a
  `WITH ... DELETE ... INSERT` statement that looked atomic in the SQL wasn't, because of
  Postgres's same-snapshot semantics for data-modifying CTEs. Caught only because manual
  `docker compose` validation was treated as load-bearing, not a formality
  ([docs/scenarios/ambiguous-link-expiration.md](docs/scenarios/ambiguous-link-expiration.md)).
- **The governance document was checked against reality, and reality lost some arguments** —
  partway through, the project's own constitution and the actual implementation had drifted apart
  (synchronous click tracking vs. an async requirement, open stats access vs. an owner-only
  requirement). Some gaps were fixed in code; others were reconciled by amending the constitution
  with an explicit rationale and version bump — never by quietly weakening the one non-negotiable
  principle (test-first) to match what had already been done; that deviation is logged openly
  instead ([.specify/memory/constitution.md](.specify/memory/constitution.md)).

## Requirements

- Docker and Docker Compose (the only requirement to run the full system)

## Run it

```bash
docker compose up --build
```

This builds the app image, starts PostgreSQL, waits for it to be healthy, runs the database
migrations automatically on startup, and starts the API on `http://localhost:8080`.

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health check: `http://localhost:8080/actuator/health`

## Try it

```bash
# Create a short link
curl -i -X POST http://localhost:8080/api/links \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com/some/long/path"}'

# Follow it (use the shortCode from the response above)
curl -i http://localhost:8080/<shortCode>

# Check its stats
curl -i http://localhost:8080/api/links/<shortCode>/stats
```

See [docs/getting-started.md](docs/getting-started.md) for every other user-facing scenario:
setting an expiration, duplicate-URL reuse, invalid-URL rejection, rate limiting.

## Three scenarios

| Scenario | What it demonstrates | PR | Scenario writeup |
|---|---|---|---|
| **Greenfield** | Core API built from scratch: create, redirect, duplicate reuse, validation, rate limiting | [#1](https://github.com/hmp3094/URL-Shortener/pull/1) | — |
| **Brownfield** | Click analytics added to the already-shipped core, including the sync-vs-async counting trade-off | [#2](https://github.com/hmp3094/URL-Shortener/pull/2) | [brownfield-click-analytics.md](docs/scenarios/brownfield-click-analytics.md) |
| **Ambiguous requirement** | Link expiration (TTL) — five hidden decision points behind "add expiration," decomposed and resolved, including a real concurrency bug found and fixed | [#3](https://github.com/hmp3094/URL-Shortener/pull/3) | [ambiguous-link-expiration.md](docs/scenarios/ambiguous-link-expiration.md) |

## Project docs

- [docs/architecture-overview.md](docs/architecture-overview.md) — components, tools/execution
  approach, control flow, key decisions at a glance
- [docs/engineering-summary.md](docs/engineering-summary.md) — plan, artifacts, risks,
  validation, assumptions, and limitations for the whole project
- [docs/requirements.md](docs/requirements.md) — what the API does and why
- [docs/design-decisions.md](docs/design-decisions.md) — stack, architecture, and the reasoning
  behind each choice
- [docs/data-model.md](docs/data-model.md) — schema
- [docs/api.yaml](docs/api.yaml) — OpenAPI contract
- [docs/performance.md](docs/performance.md) — redirect latency measurement
- [docs/scenarios/](docs/scenarios/) — how specific pieces of work were scoped, built, and
  validated

## Tests

```bash
mvn test
```

Requires Docker running in the background — integration and contract tests use Testcontainers to
spin up a real, disposable PostgreSQL instance automatically.

## Static analysis

```bash
mvn verify
```

Runs SpotBugs and writes a report to `target/spotbugsXml.xml`. Report-only for now — it doesn't
fail the build (see `docs/design-decisions.md`'s "Static analysis" section for why, and for the
two known, benign findings on the current codebase).
