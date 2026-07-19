# ADR-0011: Observability via an OTel Collector sidecar to Dash0, prod only

Date: 2026-07-18 · Status: Accepted

## Context

Once live, the app needs traces, metrics, and logs without instrumenting
application code by hand or scattering vendor credentials across services.
Local dev should export nothing.

## Decision

All signals flow through an **OpenTelemetry Collector sidecar** in the prod
compose stack, which alone holds the Dash0 auth token:

- Backend: OTel **Java agent** (zero-code), baked into the image but
  activated only by prod `JAVA_TOOL_OPTIONS`.
- Infra containers: fluentd log driver into the collector; host and
  container metrics via collector receivers.
- Frontend RUM: `@dash0/sdk-web` on prod https only, exporting to a
  same-origin `/telemetry/` path that nginx proxies to the collector — **no
  token in the browser bundle**.

## Consequences

- Vendor-neutral pipeline: switching backends means changing one collector
  exporter; app code stays uninstrumented.
- Zero telemetry (and zero noise) in local dev; the trade-off is that the
  pipeline itself is only exercised in prod.
- The collector is another prod container to keep healthy; nginx is
  deliberately decoupled so telemetry outages can't take down the app.
