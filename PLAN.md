# ShopMate — Implementation Plan (reconstructed)

> Reconstructed 2026-07-12 from `CLAUDE.md`, the codebase, and git history.
> There was no original plan file; this documents the goal, the architecture,
> the phases already built, and the work remaining.

## Goal & the twist

**ShopMate is a collaborative, real-time, multi-user shopping-list app** — think
a shared grocery list that several people (a household) edit at the same time,
online or offline, on multiple devices.

**The twist / defining technical bet:** the whole thing is built on a **CRDT
(Conflict-free Replicated Data Type) data structure as its core component**.
Concurrent edits from multiple users *never* conflict and *always* converge to
the same state without locks, server-side merge logic, or "last save wins"
clobbering. This is the differentiator — not the UI, not the feature set, but
correctness under concurrent, offline-tolerant editing.

### CRDT model (the heart of the system)
- **LWW-Register per field** (Kleppmann 2020). Each mutable field of an item —
  `name`, `quantity`, `checked`, `deleted` (tombstone), `sortKey` — carries its
  own `(value, timestamp, modifiedBy)`. Concurrent edits to *different* fields
  both survive; concurrent edits to the *same* field resolve by higher
  server-assigned timestamp, with UUID tie-break on equal timestamps.
  → `domain/model/LwwField.java`, `ShoppingItem.merge()` / `applyChange()`.
- **Ordering via fractional index.** Item position is a `sortKey` string
  (fractional index), itself an LWW register. Reordering is *always* a
  `SORT_KEY` update — **never delete + reinsert** (that duplicates the item on
  concurrent moves; it's a correctness bug, per Kleppmann PaPoC '20 §2–3).
  → `domain/crdt/FractionalIndex.java`.
- **Server-assigned timestamps.** The client never supplies a timestamp. The
  REST adapter stamps `System.currentTimeMillis()` at point of receipt before
  building an `ItemChange`. This gives a single monotonic clock authority and
  sidesteps client clock skew.
- **Per-item vector clock** (`Map<UUID,Long>`) merged via element-wise max —
  carried on `ShoppingItem` for causality tracking / dedup.

## Architecture (locked in by CLAUDE.md, enforced by ArchUnit)

Hexagonal / Ports & Adapters + Clean Architecture:

- `domain/` — pure Java, zero framework imports (model, CRDT, in/out ports).
- `application/service/` — use cases; depends only on domain ports.
- `adapter/in/web` (REST), `adapter/in/sse` (real-time), `adapter/out/persistence`
  (JPA).
- `infrastructure/config` + `infrastructure/security`.

**Contract-first:** `api/openapi.yaml` is the single source of truth. Backend
generates Spring interfaces; frontend generates a typed client. Generated code
is never hand-edited and is excluded from coverage gates.

**Quality gates:** 90% line+branch coverage both ends (domain target 100%),
enforced by JaCoCo (`./gradlew check`) and Vitest (`npm run test:coverage`).

## Phases

Each phase = one meaningful commit (per the commit convention).

| # | Phase | Status | Evidence |
|---|-------|--------|----------|
| 0 | Repo scaffold: CLAUDE.md, .gitignore, dirs, docker-compose, .env.example | ✅ done | `75042dd` |
| 1 | **OpenAPI 3.1 contract** — all endpoints, the source of truth | ✅ done | `921c9a1` (`api/openapi.yaml`, 11 endpoints) |
| 2 | Backend skeleton: Gradle, Spring Boot, hexagonal packages, OpenAPI codegen, ArchUnit test | ✅ done | `206155f` |
| 3 | **Domain + CRDT core**: ShoppingList/Item, LwwField, ItemChange, FractionalIndex, ShoppingListService, full unit tests | ✅ done | `65574c3` |
| 4 | Persistence: Flyway V1, JPA entities, Spring Data repos, repository adapters, out-ports, Testcontainers IT | ✅ done | `514f66f`, `0b3c934` |
| 5 | Web/real-time/security: REST controllers, SSE endpoint + publisher, SecurityConfig, Google SSO, JWT/auth-code + SSE-token exchange | ✅ done | `d872ab2` |
| 6 | **Frontend** (Vite + React + TS + Tailwind): auth flow, lists + list pages, item CRUD, client-side CRDT utils (fractionalIndex, lwwMerge), SSE live sync, tests | ⚠️ **scaffolded but UNCOMMITTED** | `frontend/src/**` (working tree) |
| 7 | End-to-end wiring / verification / deploy | ⬜ not started | — |

## Endpoints (from `api/openapi.yaml`)
`GET /users/me` · `POST /auth/exchange` · `GET|POST /lists` ·
`GET /lists/{id}` · `POST /lists/{id}/items` ·
`PATCH /lists/{id}/items/{itemId}` (single-field CRDT change) ·
`DELETE .../items/{itemId}` (tombstone) · `POST|DELETE .../members[/{userId}]` ·
`GET .../sse-token` · `GET .../events` (SSE stream).

## Security model
- JWTs never in URLs. OAuth2 (Google) callback issues a short-lived single-use
  auth code; frontend exchanges it via `POST /api/auth/exchange` for a JWT.
- SSE auth uses a *separate* short-lived JWT scoped to `(userId, listId)`,
  15-min TTL, passed as `?token=` (EventSource can't set headers).
- `GOOGLE_CLIENT_ID/SECRET`, `JWT_SECRET` from env only; `.env.example` → `.env`.

## Current state & remaining work
1. **Commit the frontend (Phase 6).** It's complete in the working tree (auth
   context/callback/guard, ListsPage, ShoppingListPage, AddItemForm, ItemRow/List,
   `useShoppingList` hook, `fractionalIndex` + `lwwMerge` utils, matching tests)
   but not yet committed. Verify it builds (`npm run build`), passes tests +
   90% coverage, then commit as `feat: add frontend …`.
2. **End-to-end verification (Phase 7):** `docker compose up --build`, exercise
   the concurrent-edit story across two browser sessions — the point of the whole
   project — and confirm CRDT convergence + live SSE sync.
3. Confirm backend `./gradlew check` still green (coverage gate).

## Key invariants to never break
- Reorder = `SORT_KEY` LWW update, never delete+reinsert.
- Timestamps are server-assigned only.
- `domain/` stays framework-free (ArchUnit fails the build otherwise).
- Never hand-edit generated OpenAPI code; regenerate from `api/openapi.yaml`.
