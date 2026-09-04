# Redirect Latency Measurement

The redirect path is the hottest, most latency-sensitive part of the system, so it's worth
measuring rather than assuming.

## Tool & setup

No dedicated load-testing binary (`hey`/`ab`/`wrk`) was available on the machine this was built
on, and installing one for a single measurement would be adding a dependency for its own sake.
Instead, latency was measured with a simple sequential loop using `curl`'s built-in timing
against `GET /{code}` for an already-cached short code, running against the app via
`docker compose up --build` (app + Postgres, both local, no network hop beyond localhost). This
is a lighter-weight, single-connection measurement rather than a true concurrent load test — a
documented trade-off, not a claim of production-scale throughput testing.

```bash
CODE="00000l"
curl -s -o /dev/null http://localhost:8080/$CODE   # warm the cache
for i in $(seq 1 200); do
  curl -s -o /dev/null -w "%{time_total}\n" http://localhost:8080/$CODE
done
```

## Results (200 sequential requests, cache-hit path)

| Metric | Value |
|---|---|
| min | 4.1 ms |
| p50 | 5.1 ms |
| p95 | 6.9 ms |
| p99 | 7.7 ms |
| max | 9.5 ms |

## Notes

- This measures the cache-hit path only (the code was resolved once beforehand to warm the
  cache), which is the common case. A cache miss (first-ever request for a code, hitting
  Postgres) wasn't separately measured; given the query is a single indexed lookup, it's expected
  to add low single-digit milliseconds locally, but this isn't empirically confirmed.
- No earlier baseline exists to compare against — this measurement becomes the baseline for
  future changes to the redirect path.
- Measured on a developer's local machine via Docker Desktop, not a production-equivalent
  environment; treat these numbers as directional, not an SLA guarantee.
