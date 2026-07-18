# ShopMate — Implementation Plan

> Originally reconstructed 2026-07-12 from `CLAUDE.md`, the codebase, and git
> history; last updated 2026-07-12 (evening) after the Docker stack was built
> and verified. Documents the goal, the architecture, the phases built so far,
> and the work remaining.

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
| 6 | **Frontend** (Vite + React + TS + Tailwind): auth flow, lists + list pages, item CRUD, client-side CRDT utils (fractionalIndex, lwwMerge), SSE live sync, tests | ✅ done | `3bbb762` |
| 7 | End-to-end wiring & verification: API-level CRDT convergence verified (concurrent adds by two users), SSE broadcast contract-correct, bug fixes incl. deterministic equal-sortKey ordering | ✅ done | `11cbf77`, `779e2ee` |
| 8 | Docs + Docker deployment stack: README, Dockerfiles, nginx edge proxy, compose file, actuator health, forwarded headers; full stack smoke-tested in Docker (colima) incl. live SSE through the proxy | ✅ done | `1e3a068`, `4fe852e`, `c9369ff`, `4e726a2` |
| 9 | CI: GitHub Actions — build + test gates (backend `check`, frontend `test:coverage` + `build`, docker compose build) on every push/PR | ✅ done | `3d7edc8`…`dada2b5`; run #4 all green |
| 10 | CD: deploy to AWS via GitHub Actions — single EC2 + compose (decided 2026-07-12) | ⬜ next | — |

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

## Current state (2026-07-18)

- **Phases 0–9 done.** `main` is pushed to `github.com:christianuhlcc/shopmate`
  and **CI is green** (`.github/workflows/ci.yml`, three jobs: backend
  `./gradlew check`, frontend `test:coverage` + `build`, `docker compose build`).
  On failure, the backend job emits failing tests as workflow annotations
  (readable via the public checks API) and uploads the test reports artifact.
- **Phase 9 findings (fixed en route):**
  - `gradle-wrapper.jar` was never committed — the `.gitignore` negation was
    rooted at the repo top level, not `backend/`. Broke every fresh checkout.
  - The backend coverage gate had **never actually run**: `contextLoads()` used
    a `test` profile that excluded the DataSource, so Spring context load
    failed and `:test` aborted before `jacocoTestCoverageVerification`; local
    "green" runs were stale Gradle state. Real coverage was ~60% line.
    Fixed by booting the smoke test on Testcontainers postgres
    (`integration-test` profile) and adding the missing unit tests
    (controllers, security services, SSE, service happy paths, LWW persistence
    merge). Now 98% line / 94% branch, gate enforced.
  - testcontainers-bom bumped 1.20.3 → 1.21.3 (old docker-java speaks Docker
    API 1.32; daemons ≥ 28 reject it).
- **Running backend tests locally (colima):** the test JVM needs
  `DOCKER_HOST=unix://$HOME/.colima/default/docker.sock` and
  `JAVA_TOOL_OPTIONS=-Dapi.version=1.44` (colima's Docker 29 daemon rejects
  docker-java's default API version even after the bump);
  `TESTCONTAINERS_RYUK_DISABLED=true` avoids ryuk startup issues.
- **Docker stack verified end-to-end** on this machine (colima, 2026-07-12).
- **Google auth**: wired and redirect-verified, but a real sign-in has not been
  exercised locally yet — needs real credentials; setup guide in
  `docs/google-auth-setup.md`.

## Remaining work

1. **Verify Google auth locally** — follow `docs/google-auth-setup.md`: create
   the OAuth client (register both redirect URIs), fill `.env`, sign in through
   the compose stack, confirm user upsert + JWT exchange.
2. **Phase 10 — CD to AWS (GitHub Actions).** **Decided 2026-07-12: single
   EC2 instance running the compose stack** (~10–15 €/mo; reuses the verified
   `docker-compose.yml` nearly verbatim). Rejected: ECS Fargate + RDS + ALB
   (~50–80 €/mo, overkill for household scale); App Runner (its ~120 s
   response limit kills long-lived SSE streams). Building blocks:
   - GH Actions builds & pushes both images to **ECR**, then deploys via
     **SSM Run Command** (`docker compose pull && docker compose up -d` on
     the instance) — no SSH keys in GitHub.
   - GH Actions authenticates via **OIDC role** — no long-lived AWS keys.
   - Runtime secrets (`JWT_SECRET`, `GOOGLE_*`, `DB_PASSWORD`) in **SSM
     Parameter Store**, rendered into the instance's `.env` at deploy time.
   - Postgres data on the instance's **EBS volume** (compose named volume);
     snapshot/backup story before real household use.
   - A small production compose override (`docker-compose.prod.yml`): images
     from ECR instead of `build:`, TLS via **Let's Encrypt** on the edge
     nginx (443 + HTTP→HTTPS redirect), `PUBLIC_PORT`/`FRONTEND_BASE_URL`
     set to the public domain.
   - Google OAuth redirect URI registered for the public domain; HTTPS
     mandatory (tokens in headers + SSE query param).
3. **Open bugs** (pre-existing, not deploy-blocking): BUG-8 — Java
   `FractionalIndex` and TS `fractionalIndex.ts` use incompatible algorithms
   (no reorder UI wired yet; fix = unify algorithm + shared cross-language
   test vectors). Related, found 2026-07-18 while writing coverage tests:
   `FractionalIndex.between("b0", "b0a")` returns `"b0am"`, which sorts
   *after* `"b0a"` — violates the strictly-between contract (`between` is
   unused by production code today; fold the fix into BUG-8). Minor: 405
   mapped to 500 by `ApiExceptionHandler`; `SseEventPublisher.send` only
   catches `IOException`.

## Key invariants to never break
- Reorder = `SORT_KEY` LWW update, never delete+reinsert.
- Timestamps are server-assigned only.
- `domain/` stays framework-free (ArchUnit fails the build otherwise).
- Never hand-edit generated OpenAPI code; regenerate from `api/openapi.yaml`.
