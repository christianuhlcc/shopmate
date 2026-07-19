# ADR-0007: Auth — Google SSO, single-use auth-code exchange, stateless JWTs

Date: 2026-04-28 · Status: Accepted

## Context

A household app shouldn't manage passwords. The frontend is a separate SPA,
so the OAuth2 callback must hand the session to the browser app somehow —
and the common shortcut of redirecting with a JWT in the URL leaks tokens
into history, logs, and Referer headers.

## Decision

- **Google OAuth2** is the only login (Spring `oauth2-client`); no local
  passwords.
- The OAuth2 success handler issues a **short-lived, single-use auth code**
  and redirects to the SPA with only that code; the SPA exchanges it via
  `POST /api/auth/exchange` for a **stateless HS256 JWT** used as a Bearer
  token (Spring resource server). **JWTs never appear in URLs.**
- Secrets (`GOOGLE_CLIENT_ID/SECRET`, `JWT_SECRET`) come from environment
  variables only.

## Consequences

- No password storage or reset flows; session validation is stateless — no
  server-side session store.
- Stateless JWTs can't be revoked before expiry — accepted at this scale.
- Google is a hard dependency for login, and the redirect URI must be
  registered per environment (a recurring deployment gotcha).
