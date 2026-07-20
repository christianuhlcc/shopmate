# SLO Definitions (Dash0)

SLOs for the ShopMate backend, defined as [OpenSLO v1](https://openslo.com/) JSON and
managed through the Dash0 SLO API (Private Beta — API-only, no creation UI yet).
The files in this folder are the source of truth; Dash0 holds a copy.

| File | Objective |
| --- | --- |
| `availability.json` | 99.99% of server requests complete without an ERROR span status (28d rolling) |
| `error-rate.json` | 99.9% of server requests complete without an ERROR span status (28d rolling) |
| `latency.json` | 99% of API requests finish within 500ms (28d rolling) |

## Applying

Requires a Dash0 auth token with **configuration** permission
(Organization Settings → Auth Tokens; only an Admin can grant it).
The ingest token in SSM (`/shopmate/prod/DASH0_AUTH_TOKEN`) is used by the
collector for telemetry and also works here if it carries that permission.

```bash
# Create (first time). The API host is region-specific — our org is on europe-west4.gcp.
curl -sS -X POST "https://api.europe-west4.gcp.dash0.com/api/slos?dataset=default" \
  -H "Authorization: Bearer $DASH0_TOKEN" \
  -H "Content-Type: application/json" \
  -d @latency.json

# The response contains the server-assigned id ("originOrId"). Updates go to:
#   PUT /api/slos/{originOrId}?dataset=default
# List existing SLOs and their ids:
#   GET /api/slos?dataset=default
```

## Constraints (Dash0 SLO Private Beta)

- SLI queries must be **bare vector selectors** — label matchers only (regex and
  negative matchers are fine), no PromQL functions or aggregations.
- Only `Occurrences` budgeting, a single objective, and a rolling 28-day window.
- `thresholdMetric` SLIs are not supported, so latency SLOs are expressed as a
  bucket ratio: `good` = `dash0.spans.duration` bucket `le="0.5"`, `total` = `le="+Inf"`.

## Latency SLO design notes

- Built on `dash0.spans.duration`, a histogram Dash0 synthesizes directly from
  span data, so it supports the same span attribute filters as `dash0.spans`.
- `http_route=~"/api/.*"` restricts the SLI to real API traffic — the Docker
  healthcheck hits `/actuator/health` every ~15s and would otherwise dominate
  the ratio and mask slow user requests.
- `http_route!="/api/lists/{listId}/events"` excludes the SSE stream, which is
  a long-lived connection by design and would count as a permanently "slow"
  request.
- Threshold 500ms / target 99%: steady-state p95 is single-digit milliseconds;
  the budget exists for JVM cold starts after deploys (first requests after a
  container restart run 250–600ms) and low-traffic noise (a single slow request
  moves the SLI a lot at our request volume).
