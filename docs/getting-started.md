# Getting Started

## Prerequisites

- Docker and Docker Compose (the only requirement to run the full system end-to-end)
- For local development without full containerization: Java 21, Maven, and Docker (for Postgres
  only, via `docker compose up db`)

## Run the whole system

```bash
docker compose up --build
```

This builds the app image, starts PostgreSQL, waits for it to be healthy, runs the database
migrations automatically on startup, and starts the API. Once
`GET http://localhost:8080/actuator/health` returns `{"status":"UP"}`, the system is ready.

Swagger UI is available at `http://localhost:8080/swagger-ui.html` for interactive exploration.

## Try it

### Create a short link

```bash
curl -i -X POST http://localhost:8080/api/links \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com/some/very/long/path?with=query"}'
```

Expect `201 Created`, a `Location` header with the short URL, and a JSON body with `shortCode`,
`shortUrl`, `longUrl`, and `createdAt` (see `api.yaml`).

### Follow the redirect

```bash
curl -i http://localhost:8080/<shortCode-from-previous-step>
```

Expect `302 Found` with a `Location` header equal to the original `longUrl`.

```bash
curl -i http://localhost:8080/zzzzzz
```

Expect `404 Not Found` with an error body (assuming `zzzzzz` was never created).

### Check click stats

```bash
curl -i http://localhost:8080/api/links/<shortCode-from-first-step>/stats
```

Expect `200 OK` with `clickCount` and `lastAccessedAt` reflecting how many times you followed the
redirect above. Before the first redirect, `clickCount` is `0` and `lastAccessedAt` is `null`.

```bash
curl -i http://localhost:8080/api/links/zzzzzz/stats
```

Expect `404 Not Found`, same as the redirect endpoint's response for an unknown code.

### Submit a duplicate URL

Submit the exact same `url` from the first `curl` above a second time. Expect `201 Created`
again, but with the **same** `shortCode`/`shortUrl` as the first response — no second row is
created.

### Submit invalid or unsafe URLs

```bash
curl -i -X POST http://localhost:8080/api/links \
  -H "Content-Type: application/json" \
  -d '{"url":"not-a-url"}'

curl -i -X POST http://localhost:8080/api/links \
  -H "Content-Type: application/json" \
  -d '{"url":"javascript:alert(1)"}'

curl -i -X POST http://localhost:8080/api/links \
  -H "Content-Type: application/json" \
  -d '{"url":"http://127.0.0.1:8080/admin"}'
```

Expect all three to return `400 Bad Request` with an error body, and no short link created for
any of them.

### Trigger the rate limiter

Issue creation requests rapidly in a loop from the same source IP past the configured limit.
Expect `429 Too Many Requests` with a `Retry-After` header once the limit is exceeded, until the
window resets.

## Run tests

```bash
mvn test
```

Runs unit tests (no external dependencies) and integration/contract tests (Testcontainers spins
up a real, disposable Postgres container automatically — no manual database setup needed).
