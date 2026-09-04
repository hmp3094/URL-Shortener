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

| Metric | Baseline (core redirect only) | With click tracking | Change |
|---|---|---|---|
| min | 4.1 ms | 6.8 ms | +2.7 ms |
| p50 | 5.1 ms | 8.4 ms | +3.3 ms (+65%) |
| p95 | 6.9 ms | 9.8 ms | +2.9 ms (+42%) |
| p99 | 7.7 ms | 11.2 ms | +3.5 ms |
| max | 9.5 ms | 13.2 ms | +3.7 ms |

## Regression: click tracking adds a database write to every redirect

The "with click tracking" column is a real, measured regression against the original baseline,
not noise — every redirect now performs an atomic `UPDATE` against Postgres to record the click
(see `docs/design-decisions.md`'s "Click tracking: exact vs. approximate counting"), even when the
destination lookup itself was served from cache. That's the expected, already-reasoned-through
cost of choosing exact counts over keeping every redirect off the database. The regression is
consistent with that trade-off (a few milliseconds per redirect, on a local single-instance setup)
and is accepted rather than treated as a defect — it's the documented consequence of a deliberate
design decision, not a surprise. If redirect latency at this cost ever became a problem, the
documented next step is the same one named in the design doc: move click counting to a
batched/async write instead of a synchronous one per redirect.

## Link expiration: no additional measurable cost

Re-measured on `feature/link-expiration` (click tracking + the expiry check both active on the
redirect path): p50 8.4 ms, p95 10.3 ms — statistically indistinguishable from the click-tracking
numbers above. Expected: `ShortLink.isExpired()` is a single in-memory timestamp comparison against
an already-fetched entity, not an extra database round trip, so it doesn't add to the cost the way
click tracking's `UPDATE` did.

## Notes

- This measures the cache-hit path only (the code was resolved once beforehand to warm the
  cache), which is the common case. A cache miss (first-ever request for a code, hitting
  Postgres for both the lookup and the click write) wasn't separately measured.
- Measured on a developer's local machine via Docker Desktop, not a production-equivalent
  environment; treat these numbers as directional, not an SLA guarantee.
- Analytics-ingestion lag (event to queryable) is effectively 0 ms here: the click write is
  synchronous and committed before the redirect response is returned, so a stats read immediately
  afterward always reflects it.
