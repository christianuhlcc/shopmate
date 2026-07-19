# ADR-0009: One Docker Compose stack, dev to prod, behind an nginx edge proxy

Date: 2026-07-12 · Status: Accepted

## Context

The app is four cooperating processes (frontend, backend, postgres, edge).
Dev and prod environments that differ structurally are a standing source of
"works on my machine" deploy failures.

## Decision

`docker-compose.yml` defines the canonical stack; prod runs **the same file**
plus an override (`docker-compose.prod.yml`) for ECR images, TLS, and the
observability sidecar. An **nginx edge proxy** is the single entry point:
serves the SPA, proxies `/api` and the SSE stream to the backend, later
terminates TLS and proxies RUM telemetry. The backend is never directly
exposed.

## Consequences

- The stack smoke-tested locally is structurally identical to prod;
  same-origin frontend/API avoids CORS entirely.
- nginx config must handle SSE (buffering off) and forwarded headers for
  OAuth2 redirects — both bit us once and are now encoded in the config.
- Compose caps us at single-host deployment — accepted deliberately
  (ADR-0010).
