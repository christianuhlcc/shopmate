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
| Observability | OpenTelemetry (Java agent, Collector, `@dash0/sdk-web` RUM) → [Dash0](https://www.dash0.com) — prod only |

## Repository layout

```
api/            OpenAPI contract (openapi.yaml)
backend/        Spring Boot service (hexagonal architecture, enforced by ArchUnit)
frontend/       Vite + React SPA
docker-compose.yml   full stack: postgres + backend + frontend + nginx edge proxy
docker-compose.prod.yml  production override: ECR images, TLS, certbot, OTel collector
nginx/          edge reverse-proxy config used by the compose stack
observability/  OpenTelemetry Collector config (prod telemetry → Dash0)
deploy/         Terraform infra + AWS deploy scripts (see docs/aws-deploy.md)
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

## Quick start (Docker)

```bash
cp .env.example .env   # fill in real values (or keep dev defaults; Google sign-in needs real credentials)
docker compose up --build
```

To make Google sign-in work locally, see
[docs/google-auth-setup.md](docs/google-auth-setup.md).

This starts postgres, the backend, the static frontend, and an nginx edge proxy
on `http://localhost:3000` (override with `PUBLIC_PORT`). The proxy routes
`/api`, `/oauth2`, and `/login` to the backend and everything else to the SPA;
SSE streams pass through unbuffered.

## Local setup (without Docker)

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
For obtaining Google credentials and registering the redirect URIs, see
[docs/google-auth-setup.md](docs/google-auth-setup.md) — note `bootRun` does
not read `.env`; export the variables (`set -a; source .env; set +a`) first.

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

The reference production deployment (single EC2 instance, Terraform, GitHub
OIDC, SSM Run Command, Let's Encrypt) is documented in
[docs/aws-deploy.md](docs/aws-deploy.md) and runs at
https://shopmate.uhl-steine-scherben.org.

### Docker Compose (recommended)

```bash
cp .env.example .env   # production values: real Google credentials, strong JWT_SECRET, strong DB_PASSWORD
docker compose up --build -d
```

Compose environment knobs:

| Variable | Purpose | Default |
|----------|---------|---------|
| `PUBLIC_PORT` | Host port the nginx edge proxy listens on | `3000` |
| `DB_PASSWORD` | Postgres password (also used by the backend) | `shopmate` |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Google SSO | placeholder |
| `JWT_SECRET` | Signs app JWTs, min 32 chars | dev secret |

Images:
- `backend/Dockerfile` — multi-stage Gradle build → JRE 21 Alpine runtime,
  non-root user, healthcheck on `/actuator/health`
- `frontend/Dockerfile` — Node build → nginx serving `dist/` with SPA fallback
- Both builds use the **repo root as context** because they read
  `api/openapi.yaml` during code generation

For real production, terminate TLS in front of (or instead of) the bundled
nginx and register `https://<your-domain>/login/oauth2/code/google` as an
authorized redirect URI in the Google Cloud console. The backend honors
`X-Forwarded-*` headers (`server.forward-headers-strategy: framework`), so
OAuth2 redirects use the public URL.

### Manual (without Docker)

#### Build

```bash
# Backend — self-contained fat jar
cd backend && JAVA_HOME=/path/to/jdk21 ./gradlew bootJar
# → build/libs/*.jar

# Frontend — static assets
cd frontend && npm ci && npm run build
# → dist/
```

#### Run

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

## Observability

In production, all three signals flow to [Dash0](https://www.dash0.com) through
an OpenTelemetry Collector sidecar (`observability/otelcol.yaml`); local dev
exports nothing.

- **Backend** — OTel Java agent (baked into the image, activated only in prod):
  traces, JVM/HTTP metrics, and trace-correlated logs.
- **Infrastructure** — nginx/postgres/certbot container logs (fluentd log
  driver), EC2 host metrics, and per-container docker stats.
- **Frontend** — real-user monitoring via `@dash0/sdk-web` (web vitals, JS
  errors, fetch spans linked to backend traces), sent to a same-origin
  `/telemetry/` path that nginx proxies to the collector — the Dash0 auth
  token stays server-side, never in the browser bundle.

Setup details: [docs/aws-deploy.md](docs/aws-deploy.md) → Observability.

## API

`api/openapi.yaml` defines the full contract (auth, users, lists, items,
membership, SSE events). All timestamps are server-assigned epoch milliseconds —
clients never supply timestamps.
