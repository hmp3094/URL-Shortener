# URL Shortener

A URL shortener service. This repository implements the core API: creating short links from long
URLs, resolving them via redirect, and reporting click statistics per link.

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

See [docs/getting-started.md](docs/getting-started.md) for curl-based walkthroughs of every
user-facing scenario (create a link, follow the redirect, check click stats, duplicate-URL reuse,
invalid-URL rejection, rate limiting).

## Project docs

- [docs/requirements.md](docs/requirements.md) — what the API does and why
- [docs/design-decisions.md](docs/design-decisions.md) — stack, architecture, and the reasoning
  behind each choice
- [docs/data-model.md](docs/data-model.md) — schema
- [docs/api.yaml](docs/api.yaml) — OpenAPI contract
- [docs/performance.md](docs/performance.md) — redirect latency measurement
- [docs/scenarios/](docs/scenarios/) — how specific pieces of work were scoped, built, and
  validated (a brownfield enhancement and an ambiguous-requirement resolution)

## Tests

```bash
mvn test
```

Requires Docker running in the background — integration and contract tests use Testcontainers to
spin up a real, disposable PostgreSQL instance automatically.
