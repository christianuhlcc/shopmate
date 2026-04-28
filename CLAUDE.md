# ShopMate — Developer Guide

## Architecture

ShopMate follows **Hexagonal Architecture** (Ports & Adapters) and **Clean Architecture**:

```
domain/          ← pure Java, zero framework imports
  model/         ← immutable records: ShoppingList, ShoppingItem, LwwField, ItemChange
  port/in/       ← inbound ports (interfaces): ShoppingListUseCase
  port/out/      ← outbound ports (interfaces): ShoppingListRepository, UserRepository, EventPublisher
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

**Fields:** `name`, `quantity`, `checked`, `deleted` (tombstone), `sortKey` (fractional index for ordering)

**Move invariant (Kleppmann PaPoC '20 §2–3):**
- Item reordering is always a `SORT_KEY` LWW update — **never delete+reinsert**.
- Delete+reinsert on concurrent moves duplicates the item. This is a correctness bug, not a UX issue.

**Timestamps are always server-assigned.** The client never supplies a timestamp. The REST adapter stamps `System.currentTimeMillis()` at the point of receipt before constructing `ItemChange`.

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

## Security Notes

- JWTs are never placed in URLs. The OAuth2 callback issues a short-lived single-use auth code; the frontend exchanges it for a JWT via `POST /api/auth/exchange`.
- SSE auth uses a separate short-lived JWT scoped to `(userId, listId)`, 15-min TTL, passed as `?token=` query param (EventSource cannot set headers).
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `JWT_SECRET` come from environment variables only. Never commit secrets.
- Copy `.env.example` to `.env` and fill in real values for local development.
