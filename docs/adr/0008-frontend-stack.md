# ADR-0008: Frontend stack — Vite + React 18 + TypeScript + Tailwind

Date: 2026-07-12 · Status: Accepted

## Context

The frontend is a SPA that consumes the generated API client (ADR-0004),
mirrors the CRDT merge logic client-side, and holds an SSE connection. It
needs strict typing against the contract and a fast test loop with the same
90% coverage bar as the backend.

## Decision

Vite + React 18 + TypeScript, Tailwind for styling, `openapi-fetch` over the
generated `schema.ts` for end-to-end typed API calls, react-router for
navigation, Vitest + Testing Library for tests and the coverage gate. The
CRDT utilities (LWW merge, fractional index) are reimplemented in TypeScript
rather than shared via wasm/transpilation.

## Consequences

- Mainstream, well-documented stack; typed contract makes API drift a
  compile error.
- Duplicating CRDT logic across Java and TS risks divergence — this
  materialized as BUG-8 (incompatible fractional-index algorithms); shared
  cross-language test vectors are the planned guard.
