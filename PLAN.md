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
| 10 | CD: deploy to AWS via GitHub Actions — single EC2 + compose, IaC in Terraform | ✅ done | `deploy/terraform/`, `.github/workflows/deploy.yml`, `docs/aws-deploy.md`; live at https://shopmate.uhl-steine-scherben.org |

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

1. **Register the prod Google OAuth redirect URI** (manual, Google Cloud
   console):
   `https://shopmate.uhl-steine-scherben.org/login/oauth2/code/google` on the
   existing OAuth client, then verify a real sign-in in prod (and locally per
   `docs/google-auth-setup.md` — still never exercised end-to-end).
2. ~~**Phase 10 — CD to AWS**~~ ✅ done 2026-07-18 — as decided 2026-07-12
   (single EC2 + compose, ECR, OIDC deploy role, SSM Run Command + Parameter
   Store, Let's Encrypt on edge nginx), with **IaC in Terraform**
   (`deploy/terraform/`, local gitignored state). See `docs/aws-deploy.md`.
   Follow-up before real household use: EBS snapshot/backup story (DLM) for
   the postgres volume.
3. ~~**Observability (Dash0)**~~ ✅ done — implemented 2026-07-18 (traces +
   metrics + logs + frontend RUM via OTel Collector sidecar; see
   `docs/aws-deploy.md` → Observability), confirmed working in prod
   2026-07-19: both SSM parameters (`DASH0_AUTH_TOKEN`, `DASH0_ENDPOINT`)
   set, deploy green, telemetry flowing to Dash0.
4. ~~**Section grouping**~~ ✅ phases 0–5 complete 2026-07-19 — auto-group
   items by supermarket section via bundled German dictionary + per-list
   learned corrections (ADR-0012, now Accepted). Phased plan:
   `docs/plans/section-grouping.md`. Backend + contract (phases 0–3) and
   frontend grouped UI (phase 4) both landed and merged; phase 5 hardening
   ran both coverage gates green and API-level two-user convergence checks.
   Feature is code-complete and ready to deploy backend-first (old frontend
   bundles ignore unknown `SECTION` SSE events harmlessly; old backend + new
   frontend would 400 on `SECTION` PATCHes). **Before deploying, see BUG-9 in
   Open bugs below** — phase 5 convergence testing surfaced a pre-existing,
   general (not section-specific) lost-update race that phase 5 did not fix.
5. **Group tenancy (multi-tenancy)** ✅ complete 2026-07-20. Users belong to
   exactly one group; all lists belong to a group and every member sees them
   all; signup is gated by single-use invite codes (`JOIN_GROUP` /
   `NEW_GROUP`). Replaces per-list email sharing, which is **removed**
   (`list_members` dropped in V5 — one-way). ADR-0013. Phased plan:
   `docs/plans/group-tenancy.md`. Backend phases A–B landed (`66c4a1b`,
   `6e444f7`, `2316d43`); frontend phase C landed (`d8f8d49`). E2E verified
   2026-07-20: V4+V5 applied to real pre-V4 data (2 users, 2 lists) —
   default group created, both users and lists backfilled, `list_members`
   dropped; a user now sees a group peer's list she never had access to.
   Full API flow green (403 NO_GROUP → bootstrap redeem → JOIN invite → peer
   sees list → reuse 422 → cross-group 403 → SSE issuance/claim-mismatch
   403), plus live SSE fan-out of all 6 LWW fields from a non-owner peer.
   Folded in: a **pre-existing SSE authorization hole** — token issuance never
   checked access to the requested `listId`, and `subscribe` never compared the
   token's `listId` claim to the path, so any authenticated user could stream
   another household's list live. Both halves fixed in phase B.
   Deploy note: breaking API change, frontend and backend must ship together.
6. **Open bugs** (pre-existing, not deploy-blocking): ~~BUG-8~~ **fixed
   2026-07-19** (Phase 0 of `docs/plans/section-grouping.md`) — Java
   `FractionalIndex` and TS `fractionalIndex.ts` were byte-for-byte identical
   mirrors of the same padding-based algorithm, which broke the
   strictly-between contract whenever the shorter key, padded with the
   alphabet's lowest character, collided with the longer key exactly (e.g.
   `between("b0", "b0a")` used to return `"b0am"`, sorting *after* `"b0a"`).
   Replaced with a base-36 (`0-9a-z`) digit-walk algorithm that treats "past
   the end of the string" as a true sentinel (never a stand-in for the
   lowest real digit) and never lets a generated key end in the alphabet's
   minimum digit, so it can't set up an unsolvable future pairing either.
   Pinned identically across languages by `shared/fractional-index-vectors.json`,
   loaded by both `FractionalIndexTest` (JUnit) and `fractionalIndex.test.ts`
   (Vitest), plus property tests in both suites. Minor: 405
   mapped to 500 by `ApiExceptionHandler`; `SseEventPublisher.send` only
   catches `IOException`.
   - **BUG-9 (found 2026-07-19, phase 5 convergence testing, NOT FIXED —
     pre-existing, deploy-relevant):** concurrent PATCHes to **different**
     LWW fields of the same item can silently lose one of the two writes,
     violating the "concurrent edits to different fields both survive"
     guarantee (CLAUDE.md, PLAN.md CRDT model). Root cause:
     `ShoppingListRepositoryAdapter.save()` does read (`findById`) → in-memory
     field-by-field timestamp-gated merge → `save()`, but the JPA entity has
     no `@DynamicUpdate`, so Hibernate's flush issues a full-column `UPDATE`
     using *every* field currently held in that entity instance — including
     fields the request never touched, carrying whatever (possibly stale,
     pre-dating a concurrent commit) value was read at the top of that
     transaction. Whichever of two truly-concurrent requests commits last
     wins outright and clobbers the other's already-committed, unrelated
     field back to its own stale snapshot. Confirmed via live two-user API
     testing (concurrent `SECTION` + `SORT_KEY` PATCH on the same item, 3/3
     trials lost one field), and confirmed **not** section-specific —
     concurrent `CHECKED` + `QUANTITY` PATCH on the same item reproduces it
     identically (3/3 trials). Pre-dates section-grouping; the new `section`
     field just inherits the existing flaw in `mergeLwwFields`. Earlier
     end-to-end convergence verification (phase 7) only exercised concurrent
     *adds* by two users, not concurrent edits to different fields of an
     *existing* shared item, so this was never previously exercised.
     Same-field conflicts (e.g. two `SECTION` PATCHes to the same field) are
     not subject to data loss this way (the field being compared is the one
     being written) but the "last commit wins" mechanics mean the documented
     UUID tie-break in `LwwField.merge()` is not actually what decides ties
     under true concurrency at the persistence layer — wall-clock commit
     order does. Needs a deliberate fix (e.g. `@DynamicUpdate`, per-field
     `UPDATE` statements, optimistic locking with retry, or row-level
     locking) plus dedicated concurrency tests before relying on it net-new;
     not fixed as part of section-grouping phase 5 per explicit scope
     (verify + document, not fix).
   - **BUG-10 (found 2026-07-19, phase 5 convergence testing, NOT FIXED):**
     `SectionCorrectionRepositoryAdapter.upsert()` does a non-atomic
     check-then-insert (`findById` → `if empty, insert`) rather than a real
     SQL upsert. Two concurrent `SECTION` PATCHes that normalize to the same
     `(list_id, normalized_name)` both see "no existing row," both attempt
     `INSERT`, and the second hits the `section_corrections_pkey` unique
     constraint. The `ConstraintViolationException` is uncaught and surfaces
     as an opaque `500 INTERNAL_ERROR` to that client — misleadingly, since
     by that point the item's own `SECTION` field mutation had already
     committed successfully in the earlier, separate `save()` call, so the
     "failing" client's edit silently succeeded despite the error response.
     Same root-cause family as BUG-9 (non-atomic read-then-write under
     concurrency); needs a real `INSERT ... ON CONFLICT DO UPDATE` (or
     equivalent) plus exception handling.

## Key invariants to never break
- Reorder = `SORT_KEY` LWW update, never delete+reinsert.
- Timestamps are server-assigned only.
- `domain/` stays framework-free (ArchUnit fails the build otherwise).
- Never hand-edit generated OpenAPI code; regenerate from `api/openapi.yaml`.
