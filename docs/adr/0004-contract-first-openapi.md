# ADR-0004: Contract-first OpenAPI spec with code generation on both ends

Date: 2026-04-28 · Status: Accepted

## Context

Backend (Java) and frontend (TypeScript) must agree exactly on the API —
including CRDT change payloads, where a mismatched field silently breaks
convergence. Hand-maintained DTOs on both sides drift.

## Decision

`api/openapi.yaml` is the single source of truth, written before the
implementation. The backend generates Spring interfaces from it; the frontend
generates a typed client (`openapi-fetch`). Generation runs automatically
before builds; generated code is never hand-edited and excluded from coverage
gates.

## Consequences

- A contract change is one edit; both sides fail to compile until they
  conform.
- Cost: extra build steps and occasional generator quirks.
