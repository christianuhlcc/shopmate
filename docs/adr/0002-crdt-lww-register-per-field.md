# ADR-0002: CRDT with LWW-register per field as the core data model

Date: 2026-04-28 · Status: Accepted

## Context

Several household members edit the same list concurrently, possibly offline.
Edits must converge on every replica without merge dialogs. Whole-item
last-save-wins clobbers concurrent edits; Operational Transformation requires
central operation ordering and is hard to get right; CRDTs converge by
construction.

## Decision

Model each item as one **LWW register per mutable field** (Kleppmann 2020):
`name`, `quantity`, `checked`, `deleted` (tombstone), `sortKey` — each
carrying `(value, timestamp, modifiedBy)`. Same-field conflicts resolve by
higher timestamp with UUID tie-break; different-field edits both survive.
Deletion is a tombstone. A per-item vector clock (element-wise max) tracks
causality.

Two invariants follow and must never be broken:

- **Timestamps are server-assigned.** The REST adapter stamps changes at
  receipt; clients never supply timestamps. One clock authority, no client
  skew.
- **Ordering is data, not structure.** Position is a fractional-index
  `sortKey` register; a move is always a `SORT_KEY` update, never
  delete + reinsert (which duplicates items under concurrent moves,
  Kleppmann PaPoC '20 §2–3).

## Consequences

- Merges are commutative and idempotent; the server applies changes without
  merge logic, and the common race (rename vs. check-off) loses nothing.
- Only same-field races drop a value — the accepted LWW trade-off.
- Tombstones accumulate; fine at household scale.
- Server-assigned timestamps mean offline edits are ordered by arrival, not
  by when they were made — accepted for simplicity.
