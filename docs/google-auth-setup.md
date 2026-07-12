# Google Auth — Local Setup Guide

ShopMate signs users in with Google (OAuth2 / OpenID Connect). This guide walks
through creating Google credentials and wiring them into a local ShopMate
instance, for both run modes (Docker Compose and Vite + `bootRun`).

## How the flow works (so the config makes sense)

1. The frontend sends the browser to `/oauth2/authorization/google` (backend endpoint).
2. Spring redirects to Google's consent screen, passing a **redirect URI** that
   Google must recognize — this is the part you register in the Google console.
3. Google redirects back to `/login/oauth2/code/google` on the backend.
4. The backend upserts the user (by email), mints a 24h app JWT, and redirects
   the browser to `<FRONTEND_BASE_URL>/auth/callback?code=<single-use-code>` —
   the JWT itself is never placed in a URL.
5. The frontend exchanges the code via `POST /api/auth/exchange` and stores the JWT.

The only Google-facing configuration is therefore: **client ID, client secret,
and the registered redirect URI(s).**

## 1. Create the OAuth client in Google Cloud

1. Go to [console.cloud.google.com](https://console.cloud.google.com), create
   (or pick) a project, e.g. `shopmate-dev`.
2. **APIs & Services → OAuth consent screen** (Google Auth Platform → Branding):
   - User type: **External**
   - App name, support email, developer email: anything sensible
   - Scopes: none to add manually — ShopMate only uses `openid`, `profile`,
     `email`, which are non-sensitive and need no verification
   - **Audience → Test users:** add the Google account(s) you'll sign in with.
     While the app is in "Testing" status, only these accounts can log in.
3. **APIs & Services → Credentials → Create credentials → OAuth client ID**:
   - Application type: **Web application**
   - Name: e.g. `shopmate-local`
   - **Authorized redirect URIs** — add both so one client covers both run modes:
     - `http://localhost:3000/login/oauth2/code/google` (Docker Compose stack)
     - `http://localhost:8080/login/oauth2/code/google` (Vite dev + `bootRun`)
   - Authorized JavaScript origins can stay empty (the flow is server-side).
4. Copy the **Client ID** and **Client secret**.

> Why two redirect URIs? Behind the compose stack's nginx proxy the backend
> sees the public host (`localhost:3000`) via forwarded headers. In dev mode
> the Vite proxy rewrites the Host header to the backend's own
> `localhost:8080`, so Spring builds the redirect URI with that. Registering
> both keeps one client working everywhere.

## 2. Configure ShopMate

```bash
cp .env.example .env
```

Edit `.env`:

```bash
GOOGLE_CLIENT_ID=<your-client-id>.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=<your-client-secret>
JWT_SECRET=<any-random-string-of-at-least-32-characters>
```

### Run mode A: Docker Compose

```bash
docker compose up --build
```

Compose reads `.env` automatically. Open **http://localhost:3000** and sign in.
(If you override `PUBLIC_PORT`, register a matching redirect URI:
`http://localhost:<port>/login/oauth2/code/google`.)

### Run mode B: Vite dev server + bootRun

`bootRun` does **not** read `.env` — export the variables into the shell first:

```bash
cd backend
set -a; source ../.env; set +a
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew bootRun
```

```bash
cd frontend && npm run dev
```

Open **http://localhost:3000** (the Vite server) and sign in. After Google's
consent screen you'll briefly bounce through `localhost:8080` (the backend
callback) before landing back on `localhost:3000/auth/callback` — that's
expected.

## 3. Verify

- `curl -s -D - -o /dev/null http://localhost:3000/oauth2/authorization/google | grep -i location`
  should be a 302 to `accounts.google.com` with **your** client ID and a
  `redirect_uri` matching one you registered.
- After signing in, the app shows your Google display name; `users` table has a
  row with your email.

## Troubleshooting

| Symptom | Cause / fix |
|---------|-------------|
| Google page: **Error 401: invalid_client** | Client ID/secret not picked up — still the `placeholder` default. Check `.env`, and for `bootRun` remember it doesn't read `.env` (export the vars). Restart the backend after changes. |
| Google page: **Error 400: redirect_uri_mismatch** | The `redirect_uri` in the error details isn't registered. Add exactly that URI in the console (scheme, host, port, and path must all match). Compose: also check `PUBLIC_PORT`. |
| Google page: **Access blocked: … has not completed the verification process** | Your Google account isn't in the consent screen's test users. Add it (Audience → Test users). |
| Callback lands on the wrong host/port | `FRONTEND_BASE_URL` doesn't match where the frontend is served, or (compose) the proxy isn't forwarding the client host — the edge nginx must send `Host`/`X-Forwarded-*` from `$http_host` (see `nginx/nginx.conf`). |
| Sign-in loops back to the login page | The single-use auth code expired or was already redeemed (e.g. React StrictMode double-effect in a modified callback). Check the backend log around `POST /api/auth/exchange`. |
| Works in compose, 500 on `bootRun` login | Backend can't reach `accounts.google.com` (offline) — Spring fetches Google's OIDC discovery document on first login. |

## Notes

- Secrets live only in `.env` (gitignored) / environment variables — never
  commit them. Rotate the client secret in the console if it leaks.
- No Google credentials at hand? You can still exercise the app by seeding
  users and minting dev-secret JWTs — see "Manual multi-user testing without
  Google credentials" in the [README](../README.md).
