# ADR-0014: Picnic export (beta) — Java re-implementation of the unofficial API, per-item top-5 picker

Date: 2026-07-26 · Status: Accepted

## Context

Users want to turn a ShopMate list into a Picnic order without retyping it.
Picnic (grocery delivery, NL/DE/BE) has **no official developer API**. What
exists is a set of community wrappers (`python-picnic-api`, the `picnic-api`
npm package) that reverse-engineered the mobile app's endpoints:
`storefront-prod.{country}.picnicinternational.com/api/{version}`, MD5-hashed
password login, `GET /search`, `POST /cart/add_product`. Unofficial and
undocumented — Picnic can change or block it without notice.

Two problems compound this:

1. **Language mismatch.** The backend is Java/Spring (ADR-0005); the
   reference implementations are Python/Node. There is nothing to add as a
   dependency — the relevant surface (login, search, cart-add) is maybe a
   dozen HTTP calls.
2. **Freitext-to-catalog mapping is inherently ambiguous.** `ShoppingItem`
   names are user-typed German freitext ("Milch", "Bio-Äpfel");
   Picnic's catalog is a structured product list (brand, size, variant). No
   algorithm resolves this with confidence — a human has to pick.

Given both, and that this is explicitly an **experimental feature** (not
core list-sync) built on an unofficial API, the bar is: ship something
narrow, not a fully automated pipeline. It ships for everyone, no gate —
rollout gating, if it's ever needed, is its own future ADR, not decided
here.

Options considered for the client:

- **A. Re-implement the needed subset directly in Java**, calling Picnic's
  HTTP endpoints from a new outbound adapter. No new runtime, full control
  over what's logged/retried, matches the existing stack. Con: we own
  tracking Picnic's endpoint changes ourselves, same as the OSS wrappers do.
- **B. Vendor a small Python sidecar running `python-picnic-api`.** Reuses
  maintained code, but adds a second language and process to the deployment
  unit, contradicting ADR-0009 (one Compose stack, one deployable) for a beta
  feature. Rejected.
- **C. Call Picnic directly from the browser.** Rejected: Picnic's API isn't
  CORS-enabled for third-party origins, and it would push credential/session
  handling into client-side code instead of a controlled backend boundary —
  the opposite of what ADR-0007 does for our own auth.

Options considered for mapping:

- **A. Fully automatic** (take the top search hit). Fast, but silently wrong
  picks (wrong brand/size) land in a real paid order — too risky to ship
  first.
- **B. Per-item top-5 suggestion picker in the frontend.** Backend fetches
  Picnic search results per item, frontend shows up to 5 candidates
  (name, image, price), user confirms or skips. Slower, but no order line is
  added without a human looking at it. Chosen for beta.
- **C. B plus a persisted learned mapping** (same shape as ADR-0012's
  per-list section corrections: remember a confirmed choice, skip the picker
  next time). Attractive once we have data on hit quality, but adds a table
  and a resolution order before we know if search-match quality even
  warrants it. Deferred to a follow-up ADR once the beta has usage.

*(ADR-0015 proposed generalizing the port below into a provider-agnostic
`GroceryExportPort` ahead of any second exporter existing. Put on hold: we
don't yet know the shape of a second grocery exporter, and guessing the
abstraction before seeing one risks generalizing on the wrong axis. This
ADR — the Picnic-specific port, table, and endpoints — is the active design.
Revisit ADR-0015 once a second exporter is actually being built.)*

## Decision

Adopt **A + B**. This ships to all users — no feature flag. Rollout gating
(if the unofficial-API risk ever calls for a kill switch) is deliberately
out of scope here and would be its own ADR.

- **New outbound port** `domain/port/out/PicnicClientPort`: `login`,
  `searchArticles(term)`, `addToCart(articleId, count)`. Plain records for
  the DTOs (`PicnicArticleSuggestion`, `PicnicCredentials`) live under
  `domain/model` — no framework imports, so ArchUnit's domain-purity rule
  (ADR-0003) still holds even though the shapes mirror an external API.
- **New adapter** `adapter/out/picnic/PicnicHttpAdapter`: Java `HttpClient`
  calls to `storefront-prod.de.picnicinternational.com/api/15` (Germany
  only, matching the German-only item names from ADR-0012). Implements just
  the three calls above — no delivery slots, no order history, nothing else
  from the reference wrappers.
- **New inbound port + service** `PicnicExportUseCase` /
  `PicnicExportService` (`application/service`): for a given list, fetch
  active items (`checked=false`, `deleted=false`), call `searchArticles` per
  item name, return up to 5 candidates each. A confirm step takes the
  user's per-item selections and calls `addToCart` for each. **No learned
  mapping is stored in this iteration** — every export re-runs search and
  re-asks, by design (see Option C above).
- **Quantity mapping:** extract a leading integer from the freitext
  `quantity` field as the cart count; default to `1` if nothing parses. No
  unit conversion (g/kg/Stück) is attempted in beta.
- **Credentials are per-user, not per-group.** A Picnic account is personal
  (delivery address, payment method); group membership (ADR-0013) governs
  list access, not who owns which Picnic login. Each group member who wants
  to export links their own account.
- **Credential storage:** email + MD5(password) in a new
  `picnic_credentials(user_id PK/FK, email, password_md5, ...)` table. The
  MD5 digest is Picnic's own login wire format (from the reference clients),
  not a security choice we're making — it functions as a bearer credential
  for Picnic's API, so it's encrypted at rest with a new
  `PICNIC_CREDENTIAL_ENC_KEY` secret (env var only, alongside
  `JWT_SECRET`/`GOOGLE_CLIENT_SECRET` per ADR-0007). The plaintext password
  is hashed on receipt and never persisted or logged.
- **Contract-first as usual (ADR-0004):** new endpoints
  (`POST /api/lists/{listId}/picnic/suggestions`,
  `POST /api/lists/{listId}/picnic/export`,
  `PUT/DELETE /api/users/me/picnic-credentials`) are added to
  `api/openapi.yaml` first; controllers implement the generated interfaces.
- **No session caching.** Each export logs in fresh. This is a low-frequency,
  user-initiated action, not a hot path — trading a redundant login call for
  not having to build token refresh/expiry handling in the beta.

## Consequences

- **No SLA on the underlying integration.** Picnic can change endpoints,
  rate-limit, or block an account with no warning. Failures surface as a
  single `502 GROCERY_EXPORT_UNAVAILABLE`-style error; the feature must read
  as "beta" in the UI so users don't treat it as guaranteed.
- **A new class of standing secret exists per user** (the MD5 digest), not
  just per deployment. It needs its own encryption-at-rest mechanism and key
  management, which didn't exist before this ADR — this is new surface, not
  a reuse of the JWT/OAuth machinery.
- **Match quality is unproven.** The top-5 picker is the mitigation for
  ambiguity, not a solution to it; if hit rates are poor in practice, that's
  a signal to revisit before ever building the learned-mapping follow-up
  (Option C).
- **Germany-only, Picnic-only.** Hardcoding `de` and one grocer keeps the
  beta small; multi-country or multi-grocer support is out of scope until
  this proves worth generalizing.
- **No automated tests against the real Picnic API** — the adapter is tested
  against a mocked HTTP layer only. An upstream breaking change won't be
  caught by our CI; it'll surface as a user-facing failure first. There is no
  feature flag to fall back on if this proves too fragile — that's an
  accepted trade for shipping to everyone now (rollout gating is deferred,
  see Decision).
- **Per-user, not per-group, credentials** means two members of the same
  household each link their own Picnic account; there's no shared
  "household Picnic account" concept, and nothing here changes that.
