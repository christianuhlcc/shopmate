# ShopMate — Developer Guide

## Architecture

ShopMate follows **Hexagonal Architecture** (Ports & Adapters) and **Clean Architecture**:

```
domain/          ← pure Java, zero framework imports
  model/         ← immutable records: ShoppingList, ShoppingItem, LwwField, ItemChange,
                   Group, InviteCode
  port/in/       ← inbound ports (interfaces): ShoppingListUseCase, GroupUseCase, InviteUseCase
  port/out/      ← outbound ports (interfaces): ShoppingListRepository, UserRepository,
                   GroupRepository, InviteCodeRepository, EventPublisher
  crdt/          ← CRDT utilities: FractionalIndex

application/
  service/       ← use cases: ShoppingListService (implements domain ports, depends only on them)

adapter/
  in/web/        ← REST controllers (implement generated OpenAPI interfaces)
  in/sse/        ← SSE endpoint + SseEventPublisher (implements EventPublisher port)
  out/persistence/ ← JPA entities, Spring Data repos, repository adapters

infrastructure/
  config/        ← Spring beans, Jackson config
  security/      ← SecurityConfig, GoogleOAuth2SuccessHandler, AuthCodeService, SseTokenService
```

### Layer rules (enforced by ArchUnit at build time)
- `domain` must not import anything from `adapter`, `application`, `org.springframework.*`, or `jakarta.persistence.*`
- `application` depends only on `domain`, `java.*`, and `org.slf4j.*`
- Controllers depend on the generated API interfaces, not on each other

## CRDT Design

Items use **LWW-Register per field** (Kleppmann 2020). Each mutable field carries `(value, timestamp, modifiedBy)`. On concurrent edits to the same field, the higher server-assigned timestamp wins; UUID tie-break for equal timestamps.

**Fields:** `name`, `quantity`, `checked`, `deleted` (tombstone), `sortKey` (fractional index for ordering), `section` (supermarket section code)

`section` is classified server-side at item creation via a bundled German dictionary + per-list learned corrections (ADR-0012); a user correction is an ordinary `SECTION` LWW change.

**Move invariant (Kleppmann PaPoC '20 §2–3):**
- Item reordering is always a `SORT_KEY` LWW update — **never delete+reinsert**.
- Delete+reinsert on concurrent moves duplicates the item. This is a correctness bug, not a UX issue.

**Timestamps are always server-assigned.** The client never supplies a timestamp. The REST adapter stamps `System.currentTimeMillis()` at the point of receipt before constructing `ItemChange`.

## Tenancy

Every user belongs to **exactly one group**, and every list belongs to a group
(ADR-0013). Group membership *is* list access — there is no per-list sharing.
Any member of a group can read and mutate every list in it; `owner_id` is kept
for display only.

- `ShoppingListService` resolves the caller's group before every operation.
  A group-less user gets `NoGroupException` → `403 NO_GROUP`.
- Membership is read from the DB per request, never from a JWT claim — a claim
  goes stale the moment a fresh user redeems an invite, and JWTs live 24 h.
- `GoogleOAuth2SuccessHandler` rebuilds the `User` record on every login, so it
  must preserve `groupId`. Dropping it silently ejects returning users from
  their group.

## OpenAPI Contract

`api/openapi.yaml` is the single source of truth. **Never manually edit generated code.**

- Backend: `./gradlew openApiGenerate` → generates Spring interfaces into `build/generated/openapi/`
- Frontend: `npm run generate-api` → generates `src/api/schema.ts`

Both run automatically before compile/build steps.

## Running Locally

```bash
# Full stack
docker compose up --build

# Backend only (requires postgres running)
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew bootRun

# Frontend only
cd frontend && npm run dev

# Visual preview without a backend (mock data, all screen states):
#   npm run dev, then open http://localhost:3000/preview.html?screen=<state>
#   states: login | welcome | welcome-name | lists | lists-empty | lists-loading
#           | list | list-empty | list-loading | list-error | callback-error
#           (+ &sheet=create|group)

# Backend tests
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew test

# Backend coverage report
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew jacocoTestReport

# Frontend tests
cd frontend && npm test

# Frontend coverage
cd frontend && npm run test:coverage

# Regenerate API client (frontend)
cd frontend && npm run generate-api
```

## Test Coverage Gates

- Backend: 90% line + branch coverage via JaCoCo (`./gradlew check` enforces it)
- Frontend: 90% statements/branches/functions/lines via Vitest (`npm run test:coverage` enforces it)
- Domain package target: 100%
- Generated code is excluded from both gates

## Commit Convention

```
feat:   new user-visible functionality
fix:    bug fix
test:   adding or fixing tests
chore:  build, config, tooling changes
```

One meaningful commit per completed phase.

## Observability (prod only)

Traces, metrics, and logs go to Dash0 through an OTel Collector sidecar
(`observability/otelcol.yaml` + `docker-compose.prod.yml`). Backend = OTel Java
agent (in the image, activated only in prod); frontend = `@dash0/sdk-web`
(`src/observability.ts`, enabled only on prod https, exports via the same-origin
`/telemetry/` nginx proxy); infra logs via fluentd log driver. Local dev exports
nothing. Details: `docs/aws-deploy.md`.

## Security Notes

- JWTs are never placed in URLs. The OAuth2 callback issues a short-lived single-use auth code; the frontend exchanges it for a JWT via `POST /api/auth/exchange`.
- SSE auth uses a separate short-lived JWT scoped to `(userId, listId)`, 15-min TTL, passed as `?token=` query param (EventSource cannot set headers). Token issuance asserts the caller's group has access to the list, and `subscribe` rejects a token whose `listId` claim doesn't match the path — **both checks are required**; either one alone leaves the stream reachable across groups.
- Signup is gated by invite codes (ADR-0013). Google login stays open, but a user with no group can do nothing: every list operation returns `403` with code `NO_GROUP`, which is deliberately distinct from `ACCESS_FORBIDDEN` so the frontend can route to onboarding. Any new endpoint must decide what it does for a group-less caller.
- Invite codes are single-use and expire after 7 days. Single-use is enforced by a conditional `UPDATE ... WHERE used_by IS NULL`, not read-then-write, so concurrent redemption can't double-spend a code.
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `JWT_SECRET` come from environment variables only. Never commit secrets.
- `BOOTSTRAP_INVITE_CODE` is a reusable NEW_GROUP code for bootstrapping a fresh environment. While set it is a standing credential — **unset it once the first group exists**.
- Copy `.env.example` to `.env` and fill in real values for local development.

## Design Context

- `PRODUCT.md` — strategic design context: register (product), platform (web), users, positioning, brand personality, anti-references, design principles. Read it before any UI/UX work.
- `DESIGN.md` — visual system (currently a SEED: committed marigold palette, warm humanist sans, responsive motion). The previous white+indigo Tailwind-default look is retired; re-run `/impeccable document` after the redesign lands to capture real tokens.
