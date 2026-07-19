# ADR-0001: Record architecture decisions as ADRs

Date: 2026-07-19 · Status: Accepted

## Context

Key decisions (CRDT model, auth flow, deployment shape) were documented only
implicitly in CLAUDE.md, PLAN.md, and commit messages; the reasoning was at
risk of being lost.

## Decision

Keep ADRs in `docs/adr/`, one per decision, Nygard format, numbered
sequentially. ADR-0002 onward are backfilled from git history and carry their
original decision dates. Superseded ADRs are marked, never deleted.

## Consequences

The "why" travels with the repo; new decisions get an ADR when made.
