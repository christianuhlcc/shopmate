# ADR-0005: Backend stack — Java 21 + Spring Boot 3, PostgreSQL + JPA + Flyway

Date: 2026-04-28 · Status: Accepted

## Context

The adapters need mature OAuth2/JWT support, an ORM with migrations, SSE, and
integration tests against a real database. The novelty budget is spent on the
CRDT core, not the plumbing.

## Decision

Java 21 (records for the immutable domain model) + Spring Boot 3. PostgreSQL
only — CRDT state stored relationally (per-field LWW columns), not as opaque
blobs. Flyway for migrations, Spring Data JPA behind repository adapters,
Testcontainers for integration tests against real Postgres (no H2).

## Consequences

- Boring, well-supported stack; queryable, migratable data.
- Testcontainers requires Docker locally and in CI (caused colima friction),
  but real-Postgres tests caught real bugs (LWW persistence merge).
