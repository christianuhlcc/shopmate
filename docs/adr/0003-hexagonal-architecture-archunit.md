# ADR-0003: Hexagonal architecture, enforced by ArchUnit

Date: 2026-04-28 · Status: Accepted

## Context

The CRDT merge logic must be provably correct, so it needs exhaustive unit
tests without Spring, JPA, or HTTP in the way — and layering rules that live
only in a style guide erode.

## Decision

Ports & Adapters: `domain/` (pure Java, zero framework imports),
`application/service/` (use cases, domain ports only), `adapter/in|out/`
(web, SSE, JPA), `infrastructure/` (wiring, security). ArchUnit tests in the
normal build fail on any violation of these dependency rules.

## Consequences

- The domain reaches 100% coverage with plain, fast JUnit tests; framework
  leakage breaks the build instead of surfacing in review.
- Cost: entity↔domain mapping layers and single-implementation port
  interfaces — accepted as the price of a framework-free core.
