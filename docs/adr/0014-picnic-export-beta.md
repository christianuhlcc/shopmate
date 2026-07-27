# ADR-0014: Picnic export (beta) — Java re-implementation of the unofficial API, per-item manual picker

Date: 2026-07-26 · Status: Accepted, amended 2026-07-26 (see
[Amendment: second-factor authentication](#amendment-2026-07-26-second-factor-authentication))
and 2026-07-27 (per-item fetching, 20 suggestions, editable search term).

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
- **Credential storage:** ~~email + MD5(password)~~ **superseded by the
  amendment below — the stored secret is the Picnic session key, not the
  password digest.** Unchanged: whatever is stored is encrypted at rest with
  a `PICNIC_CREDENTIAL_ENC_KEY` secret (env var only, alongside
  `JWT_SECRET`/`GOOGLE_CLIENT_SECRET` per ADR-0007), and the plaintext
  password is never persisted or logged.
- **Contract-first as usual (ADR-0004):** new endpoints
  (`POST /api/lists/{listId}/picnic/suggestions`,
  `POST /api/lists/{listId}/picnic/export`,
  `PUT/DELETE /api/users/me/picnic-credentials`) are added to
  `api/openapi.yaml` first; controllers implement the generated interfaces.
- **No session caching.** ~~Each export logs in fresh.~~ **Superseded by the
  amendment below: fresh logins are impossible without user interaction, so
  the session must be persisted.**

## Amendment (2026-07-26): second-factor authentication

End-to-end verification against a real account (the first time this design met
the live API) found that **Picnic requires a second factor, and the original
credential model cannot work.** Three facts, each verified directly:

1. `POST /user/login` returns 200 with an auth key **and**
   `second_factor_authentication_required: true`. The key it issues is
   refused with 403 by every real endpoint. A login that "succeeds" therefore
   proves nothing — the original design persisted credentials on exactly that
   signal.
2. The factor clears via `POST /user/2fa/generate {channel:"SMS"}` → 204,
   then `POST /user/2fa/verify {otp}` → 204, which returns a **new** auth key.
   That key works, and keeps working on later calls.
3. **A subsequent login re-triggers 2FA anyway**, even reusing the same
   device id. There is no "trusted device" escape: the only way to obtain a
   working key is a human reading an SMS.

Fact 3 is the decisive one. It means a stored password buys nothing — it
cannot be exchanged for a usable session unattended — while remaining the
most dangerous thing we could hold. So:

- **The stored secret becomes the Picnic session key, not the password
  digest.** `picnic_credentials` holds `(user_id, email, device_id,
  auth_key_encrypted, status)`; the MD5 digest is not persisted at all. This
  is a security *improvement*: a session key is revocable (`POST
  /user/logout`) and scoped, whereas the digest is a password-equivalent that
  grants full account access until the user changes their password.
- **The device id (`x-picnic-did`) becomes per-user and persisted.** The
  reference wrappers share one hardcoded constant; reusing it across all
  ShopMate users would make our traffic trivially correlatable and let one
  abusive user get the identifier blocked for everyone. Generate one per
  account at link time.
- **Linking becomes a two-step flow** (password → SMS code), so it needs a
  `SECOND_FACTOR_REQUIRED` state in the contract and a code-entry step in the
  sheet. The provisional key from step 1 is persisted with a pending status,
  because `/user/2fa/verify` needs it and a backend restart mid-link must not
  strand the user.
- **Session expiry becomes a user-visible event.** When the stored key is
  refused there is no silent recovery: the contract needs a distinct
  `PICNIC_SESSION_EXPIRED` error so the frontend can route to re-linking
  rather than showing a generic failure.

Also corrected while verifying: `GET /search` no longer exists (404 on api/15,
/17 and /19); the live endpoint is `GET /pages/search-page-results`, which
returns a page-block tree rather than a product list. Product data itself is
unchanged from what this ADR assumed — the mapped fields are all present and
correct. These are wire-level details, recorded in
`docs/plans/picnic-export.md`, not architecture.

**One more decision above changed with it.** "Per-item top-5 picker" was
written as settled; the *top-5* half did not survive contact. Each Picnic
search costs ~2–3 s of their server-side render, and no page-size parameter is
honoured, so fetching a whole list up front made the wait grow linearly with
list size and spent searches on items the user then skipped. Suggestions are
now fetched **one item per request**, with the client stepping through items
and prefetching the next while the user decides — time-to-first-choice becomes
constant instead of proportional to list length. The picker also shows up to 20
rather than 5: Picnic returns ~120 per search and we parse the response in full
regardless, so depth costs nothing, and five was routinely too few to contain
the right product. The *manual picker* half of the decision is unchanged and
still the point.

**The query is the user's, not the item's.** This ADR assumed the item name
*is* the search term. In practice item names are freitext written for a human
reading a list, not for a product search — "test" matches a pregnancy test —
and no amount of result depth repairs a bad query. The search term is now an
editable field per item, seeded with the item name and backed by Picnic's own
`/suggest` autocomplete. It stays a *query*, deliberately: overriding it
changes what we ask Picnic and nothing on the list, so a bad guess costs one
search rather than renaming an item the whole group sees. Option C below
(remembering a confirmed pick per item name) is still deferred and would
largely dissolve this problem for repeat items.

## Consequences

- **No SLA on the underlying integration.** Picnic can change endpoints,
  rate-limit, or block an account with no warning. Failures surface as a
  single `502 GROCERY_EXPORT_UNAVAILABLE`-style error; the feature must read
  as "beta" in the UI so users don't treat it as guaranteed.
- **A new class of standing secret exists per user** (post-amendment: the
  Picnic session key), not just per deployment. It needs its own
  encryption-at-rest mechanism and key management, which didn't exist before
  this ADR — this is new surface, not a reuse of the JWT/OAuth machinery.
- **Export can break on a schedule we don't control, and recovery needs the
  user.** Because a refused session key can only be replaced by a human
  reading an SMS, key lifetime directly sets how often people are interrupted.
  If Picnic expires keys aggressively, the feature is annoying in a way no
  amount of backend work can fix — that, not match quality, is now the main
  thing to watch in the beta.
- **A linked account is a second way to lose access to someone's groceries.**
  Storing a live, working session key means a compromise of our database
  yields usable Picnic sessions, not just credentials that still face 2FA.
  Revocation (ours and Picnic's) matters more than it did under the original
  design.
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
