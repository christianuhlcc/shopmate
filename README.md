# ShopMate

A collaborative, real-time shopping-list app. Several people edit the same list at
the same time — online or offline, on multiple devices — and every edit converges
without conflicts, locks, or "last save wins" clobbering.

The core technical bet: list items are a **CRDT** (Conflict-free Replicated Data
Type). Each mutable field of an item (`name`, `quantity`, `checked`, `deleted`,
`sortKey`) is an independent LWW register carrying `(value, timestamp, modifiedBy)`
(Kleppmann 2020). Concurrent edits to *different* fields both survive; concurrent
edits to the *same* field resolve deterministically by server-assigned timestamp
with a UUID tie-break. Ordering uses fractional indexes, so a reorder is always a
`sortKey` update — never delete + reinsert. Live updates fan out over SSE.

## Tech stack

| Layer      | Tech |
|------------|------|
| Backend    | Java 21, Spring Boot, Spring Security (Google OAuth2 + JWT), Spring Data JPA, Flyway, SSE |
| Frontend   | React 18, TypeScript, Vite, Tailwind, `openapi-fetch`, EventSource |
| Database   | PostgreSQL |
| Contract   | `api/openapi.yaml` — single source of truth; both sides generate typed code from it |
| Tests      | JUnit 5 / MockMvc / ArchUnit (backend), Vitest + Testing Library (frontend) |

## Repository layout

```
api/            OpenAPI contract (openapi.yaml)
backend/        Spring Boot service (hexagonal architecture, enforced by ArchUnit)
frontend/       Vite + React SPA
docker-compose.yml   (placeholder — full compose stack is planned, see Deployment)
PLAN.md         implementation plan and phase status
CLAUDE.md       developer guide (architecture and layer rules)
```

The backend follows Hexagonal / Clean Architecture: `domain/` is pure Java with
zero framework imports, `application/` holds the use cases, `adapter/` holds
REST/SSE/JPA adapters, and `infrastructure/` holds Spring config and security.
See `CLAUDE.md` for the layer rules.

## Prerequisites

- **Java 21** (e.g. `brew install openjdk@21`)
- **Node.js 20+** and npm
- **PostgreSQL 16** (e.g. `brew install postgresql@16`)
- Optional for real sign-in: a **Google OAuth2 client** (ID + secret)

## Local setup

### 1. Database

```bash
brew services start postgresql@16
/opt/homebrew/opt/postgresql@16/bin/psql -d postgres <<'SQL'
CREATE USER shopmate WITH PASSWORD 'shopmate';
CREATE DATABASE shopmate OWNER shopmate;
SQL
```

The backend defaults match this (`jdbc:postgresql://localhost:5432/shopmate`,
user/password `shopmate`/`shopmate`). Override with `DB_URL`, `DB_USER`,
`DB_PASSWORD` if yours differ. Flyway runs migrations automatically on startup.

### 2. Environment variables

```bash
cp .env.example .env   # then fill in real values
```

| Variable | Purpose | Local default |
|----------|---------|---------------|
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Google SSO | placeholder (app starts, sign-in won't work) |
| `JWT_SECRET` | Signs app JWTs, min 32 chars | dev secret baked into `application.yml` |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Postgres connection | `localhost:5432/shopmate`, `shopmate`/`shopmate` |
| `FRONTEND_BASE_URL` | OAuth2 redirect target | `http://localhost:3000` |

Never commit secrets — everything sensitive comes from the environment.

### 3. Backend (port 8080)

```bash
cd backend
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew bootRun
```

The OpenAPI generator runs automatically before compilation
(`./gradlew openApiGenerate` to run it alone). Never edit generated code.

### 4. Frontend (port 3000)

```bash
cd frontend
npm install
npm run dev
```

Vite proxies `/api`, `/oauth2`, and `/login` to `localhost:8080`, so the app is
served entirely from `http://localhost:3000`. `npm run generate-api` regenerates
the typed client (`src/api/schema.ts`) from `api/openapi.yaml`; the build does
this automatically.

## Testing

### Backend

```bash
cd backend
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew test               # unit + integration tests
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew check              # tests + coverage gate + ArchUnit rules
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew jacocoTestReport   # HTML report → build/reports/jacoco
```

### Frontend

```bash
cd frontend
npm test                 # run once
npm run test:watch       # watch mode
npm run test:coverage    # with coverage gate; HTML report → coverage/
npm run lint             # type-check (tsc --noEmit)
```

### Coverage gates

- Backend: **90% line + branch** (JaCoCo, enforced by `./gradlew check`); domain package targets 100%
- Frontend: **90% statements/branches/functions/lines** (Vitest)
- Generated code is excluded on both sides

### Manual multi-user testing without Google credentials

You can exercise the collaborative flow locally without a Google OAuth2 client:

1. Seed users directly into the `users` table.
2. Mint HS256 JWTs signed with the dev `JWT_SECRET` from `application.yml`,
   with `sub` set to the seeded user's UUID, and place them in the browser's
   `localStorage` under `auth_token`.
3. For two concurrent users, open one window on `http://localhost:3000` and one
   on `http://127.0.0.1:3000` — different origins mean separate `localStorage`,
   so each window can act as a different user. Start Vite with
   `npm run dev -- --host` so both hostnames are served.

Then edit the same list in both windows and watch changes sync live over SSE.

## Deployment

> `docker-compose.yml` is currently a placeholder. A full compose stack
> (postgres + backend + frontend + nginx) is planned — see `PLAN.md`. Until
> then, deploy the two build artifacts directly:

### Build

```bash
# Backend — self-contained fat jar
cd backend && JAVA_HOME=/path/to/jdk21 ./gradlew bootJar
# → build/libs/*.jar

# Frontend — static assets
cd frontend && npm ci && npm run build
# → dist/
```

### Run

1. **Backend:** `java -jar shopmate-backend.jar` with the environment variables
   above set to production values. Requirements:
   - a reachable PostgreSQL (Flyway migrates the schema on startup)
   - a real `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET`, with
     `https://<your-domain>/login/oauth2/code/google` registered as an
     authorized redirect URI in the Google Cloud console
   - a strong, unique `JWT_SECRET` (≥ 32 chars)
   - `FRONTEND_BASE_URL=https://<your-domain>`
2. **Frontend:** serve `dist/` from any static host or nginx.
3. **Reverse proxy:** route `/api`, `/oauth2`, and `/login` to the backend
   (port 8080) and everything else to the static frontend — the same shape as
   the Vite dev proxy. SSE endpoints need proxy buffering disabled
   (`proxy_buffering off;` in nginx) and a generous read timeout so event
   streams aren't cut off.
4. **TLS is required** in production: tokens are sent as bearer headers and,
   for SSE, as short-lived query-param tokens.

### Security model (summary)

- JWTs never appear in URLs during login. The OAuth2 callback issues a
  short-lived single-use auth code; the frontend exchanges it for a JWT via
  `POST /api/auth/exchange`.
- SSE uses a separate 15-minute JWT scoped to `(userId, listId)`, passed as
  `?token=` because EventSource cannot set headers.

## API

`api/openapi.yaml` defines the full contract (auth, users, lists, items,
membership, SSE events). All timestamps are server-assigned epoch milliseconds —
clients never supply timestamps.
