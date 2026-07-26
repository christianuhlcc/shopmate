# Picnic Export — Implementation Plan

**Status:** Phases A, B, C, D1 done and pushed to
`claude/picnic-app-export-odrgzg` (backend, frontend, config/docs plumbing —
all coverage gates green, `tsc` clean). **D2 (manual end-to-end verification
against a real Picnic account) is the only remaining step — see §6, run it
locally.** No PR has been opened yet.

## Context

Decision record: [ADR-0014](../adr/0014-picnic-export-beta.md) — read that
first, this doc is the *how*. (ADR-0015, generalizing this into a
multi-provider `GroceryExportPort`, is a **draft on hold** — build this
Picnic-specific, do not pre-generalize.)

Users convert a ShoppingList's active items into a Picnic cart. Picnic has no
official API; we re-implement the reverse-engineered mobile-app endpoints
directly in Java (no Python sidecar, no client-side calls — ADR-0014). Item
names are freitext, so per-item matching to Picnic's catalog needs a human:
the backend fetches up to 5 search candidates per item, the frontend shows a
picker, the user confirms or skips before anything is added to a cart.

**Ships to all users, no feature flag.** Rollout gating is explicitly out of
scope (its own future ADR if ever needed) — do not add a config toggle.

## Product decisions (settled, do not reopen)

- Germany only: hardcoded `storefront-prod.de.picnicinternational.com`.
- Credentials are per-user (not per-group): a Picnic account is personal.
- Per-item top-5 manual picker. No auto-add-highest-match.
- No persisted/learned mapping in this iteration — every export re-searches.
- No Picnic session caching — each export logs in fresh (low-frequency,
  user-initiated action; simpler than token refresh handling).
- Quantity → cart count: leading integer parsed from the freitext `quantity`
  field, default `1` if nothing parses. No unit conversion.
- The MD5 password digest is Picnic's own login wire format, not our choice —
  it is a bearer credential and must be encrypted at rest.

## Verified codebase facts

- Backend hexagonal, ArchUnit-enforced (`domain` no Spring/JPA imports;
  `application` only `domain` + `java.*` + `org.slf4j.*`). Latest migration is
  `V5__group_scoping_enforced.sql` — this feature adds `V6`.
- Controllers implement generated OpenAPI interfaces with class-level
  `@RequestMapping("/api")` (`ShoppingListController implements ListsApi,
  ItemsApi` is the template).
- `ApiExceptionHandler` is one `@RestControllerAdvice` with one
  `@ExceptionHandler` method per domain exception, each returning a generated
  `ApiError(code, message, timestamp)`.
- Inbound ports are plain interfaces in `domain/port/in`
  (`ShoppingListUseCase` is the template); `SecurityContextHelper
  .getCurrentUserId()` is how controllers get the caller.
- No HTTP-stubbing test library exists yet (no WireMock/MockWebServer) —
  needed for testing the new outbound Picnic HTTP adapter in isolation.
- Secrets follow `${ENV_VAR:dev-default}` in `application.yml`
  (`shopmate.jwt.secret`, `BOOTSTRAP_INVITE_CODE` are the templates), passed
  through both compose files + `.env.example`.
- Frontend: feature folders under `src/features/*`; the sheet idiom is
  `sheet-backdrop`/`sheet-panel` (`GroupSheet.tsx`, `SectionSheet.tsx` are the
  templates); preview harness is `src/test/preview-main.tsx`.
- Coverage gates: backend 90% line+branch (`./gradlew check`), frontend 90%
  (`npm run test:coverage`); domain package target 100%.

---

## 1. OpenAPI contract changes (`api/openapi.yaml`)

**New schemas:**
- `PicnicCredentialsRequest {email, password}` — write-only; password never
  echoed back.
- `PicnicCredentialsStatus {linked: boolean, email?: string}`.
- `ArticleSuggestion {id, name, imageUrl?, priceCents?, unit?}`.
- `ItemSuggestions {itemId, itemName, suggestions: ArticleSuggestion[]}`.
- `ExportSuggestionsResponse {items: ItemSuggestions[]}`.
- `ExportSelection {itemId, articleId?}` (`articleId` absent = skip).
- `ExportRequest {selections: ExportSelection[]}`.
- `ExportResult {added: int, skipped: int, failures: [{itemId, reason}]}`.

**New paths** (tag `PicnicExport` → generated `PicnicExportApi`):
- `PUT /users/me/picnic-credentials` → 204; 422 `PICNIC_LOGIN_FAILED` if
  Picnic rejects the login before we persist anything.
- `DELETE /users/me/picnic-credentials` → 204.
- `GET /users/me/picnic-credentials` → 200 `PicnicCredentialsStatus`.
- `POST /lists/{listId}/picnic/suggestions` → 200
  `ExportSuggestionsResponse`; 403 `NO_GROUP`/`ACCESS_FORBIDDEN`; 422
  `PICNIC_CREDENTIALS_MISSING`; 502 `PICNIC_UNAVAILABLE`.
- `POST /lists/{listId}/picnic/export` → 200 `ExportResult`; same error set
  as suggestions.

## 2. Database migration

**`V6__picnic_credentials.sql`** — purely additive:
```sql
CREATE TABLE picnic_credentials (
    user_id UUID PRIMARY KEY REFERENCES users(id),
    email VARCHAR(255) NOT NULL,
    password_md5_encrypted BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```
One row per user (PK is `user_id` directly — no provider column; that's
ADR-0015's shape, on hold).

## 3. Backend design

**Domain (`domain/model/`, pure Java):**
- `PicnicCredentials(String email, String passwordMd5Hex)` — plain record,
  the *unencrypted* digest as used in memory/in-flight.
- `ArticleSuggestion(String id, String name, String imageUrl, Integer
  priceCents, String unit)`.
- New exceptions: `PicnicCredentialsMissingException` (422),
  `PicnicLoginFailedException` (422), `PicnicUnavailableException` (502).

**Ports:**
- `port/out/PicnicClientPort`: `void verifyLogin(PicnicCredentials)` (throws
  `PicnicLoginFailedException`); `List<ArticleSuggestion>
  searchArticles(PicnicCredentials, String term)`; `void
  addToCart(PicnicCredentials, String articleId, int count)`.
- `port/out/PicnicCredentialsRepository`: `findByUserId`, `save`, `delete`.
- `port/in/PicnicExportUseCase`: `linkCredentials(userId, email,
  rawPassword)`, `unlinkCredentials(userId)`, `getCredentialsStatus(userId)`,
  `getSuggestions(listId, userId)`, `export(listId, userId,
  List<ExportSelectionCommand>)`.

**Application (`application/service/PicnicExportService`):**
- `linkCredentials`: MD5-hash the raw password (`MessageDigest.getInstance
  ("MD5")`), call `picnicClientPort.verifyLogin` to fail fast on a wrong
  password *before* persisting, then save via the repository. Raw password is
  never persisted or logged — hashed and discarded in the same method.
- `getSuggestions`: load the list (reuse `ShoppingListUseCase.getList`),
  filter items where `checked=false && deleted=false`, call
  `searchArticles(credentials, item.name())` per item, cap at 5 results each.
- `export`: for each selection with a non-null `articleId`, parse the item's
  `quantity` LWW value for a leading integer (default 1), call `addToCart`.
  Per-item failures are caught and collected into `ExportResult.failures`
  rather than aborting the whole batch — one bad SKU shouldn't sink the rest
  of a real cart.
- Test: `PicnicExportServiceTest` (Mockito, matching `ShoppingListServiceTest`
  style) — missing-credentials path, login-failure path, partial-failure
  export, quantity-parsing edge cases (no digit, "2x", "500g").

**Persistence adapter:**
- `adapter/out/persistence/entity/PicnicCredentialsEntity(userId, email,
  passwordMd5Encrypted byte[], createdAt, updatedAt)`.
- New `infrastructure/security/CredentialCipher` — AES-GCM encrypt/decrypt
  keyed by `shopmate.picnic.credential-enc-key`
  (env `PICNIC_CREDENTIAL_ENC_KEY`, dev default in `application.yml`,
  alongside `shopmate.jwt.secret`). Unit test: encrypt→decrypt round trip,
  and that tampering with ciphertext bytes fails decryption (GCM auth tag).
- `PicnicCredentialsRepositoryAdapter implements PicnicCredentialsRepository`
  — encrypts on save, decrypts on read, via `CredentialCipher`.
- Test: `PicnicCredentialsRepositoryAdapterIT` (Testcontainers, matching
  `GroupInviteRepositoryAdapterIT` style) — save/find/delete; assert the
  stored bytes are not the plaintext digest.

**Picnic HTTP adapter:**
- `adapter/out/picnic/PicnicHttpAdapter implements PicnicClientPort` — JDK
  `java.net.http.HttpClient` against
  `https://storefront-prod.de.picnicinternational.com/api/15`. Three calls
  only: `POST /user/login` (`{key: email, secret: md5Hex}` → session token in
  response, echoed back as `x-picnic-auth` header on subsequent calls),
  `GET /search?search_term=...` (parse nested categories → flatten to
  articles, map to `ArticleSuggestion`), `POST /cart/add_product
  {product_id, count}`. Jackson (already on the classpath) for JSON parsing.
  Non-2xx / malformed JSON → `PicnicUnavailableException`; a 4xx specifically
  on `/user/login` → `PicnicLoginFailedException`.
- Add `testImplementation("org.wiremock:wiremock:3.9.1")` (new dependency —
  nothing currently stubs outbound HTTP in this codebase). `PicnicHttpAdapter
  Test` stubs `/user/login`, `/search`, `/cart/add_product`, including bad
  credentials, a 500, and a truncated/invalid JSON body.

**Web:**
- New `PicnicExportController implements PicnicExportApi`,
  `@RequestMapping("/api")`, same shape as `ShoppingListController`.
- `ApiExceptionHandler`: three new `@ExceptionHandler` methods
  (`PICNIC_CREDENTIALS_MISSING` 422, `PICNIC_LOGIN_FAILED` 422,
  `PICNIC_UNAVAILABLE` 502).

**Config:**
- `application.yml`: `shopmate.picnic.credential-enc-key:
  ${PICNIC_CREDENTIAL_ENC_KEY:dev-key-change-me-32-bytes-long!}`.
- `.env.example` + both compose files: `PICNIC_CREDENTIAL_ENC_KEY`.

## 4. Frontend design

- New `src/features/picnic-export/`:
  - `PicnicCredentialsSheet.tsx` — link/unlink form (email + password),
    reusing the `GroupSheet.tsx`/`SectionSheet.tsx` sheet idiom. Shows linked
    status (masked email) when already linked.
  - `PicnicExportSheet.tsx` — opened from a new "Export to Picnic" action in
    the list header. Three states: loading suggestions → per-item top-5
    picker (radio group + "skip" per item, thumbnail/name/price) → confirm
    result summary (added/skipped/failures).
- `PICNIC_CREDENTIALS_MISSING` response routes the user to
  `PicnicCredentialsSheet` first instead of showing a bare error.
  `PICNIC_LOGIN_FAILED`/`PICNIC_UNAVAILABLE` render as an inline banner —
  they must not crash the sheet.
- Regenerate `src/api/schema.ts` (`npm run generate-api`) after the contract
  lands.
- Preview harness (`src/test/preview-main.tsx`): new screens
  `picnic-credentials`, `picnic-suggestions`, `picnic-result` with mock data;
  `&sheet=picnic`. Update the screen-state list in `CLAUDE.md`.

---

## 5. Task breakdown (orchestration: lead session, Sonnet 5 subagents)

Gates: **backend** `cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21
./gradlew check` · **frontend** `cd frontend && npm run lint && npm run
test:coverage && npm run build`. Every **Gate** must be fully green before the
next phase starts. One commit per phase.

**Dependency graph:** A1 ∥ A2 → A3 ∥ A4 → *Gate A* → B1 → B2 → *Gate B* → C1
→ C2 ∥ C3 → C4 → *Gate C* → D1 ∥ D2. "∥" pairs touch disjoint files — safe as
parallel subagents, or just run back-to-back on one branch.

### Phase A — OpenAPI contract + domain groundwork (additive, backend-only)

**A1 — OpenAPI contract** *(∥ A2)*
File: `api/openapi.yaml` per §1. Verify `./gradlew openApiGenerate` emits
`PicnicExportApi` and the new schemas; no manual edits to generated code.

**A2 — Domain model, ports, exceptions** *(∥ A1)*
Files: `domain/model/{PicnicCredentials,ArticleSuggestion}.java`; 3 new
exceptions; `domain/port/out/{PicnicClientPort,PicnicCredentialsRepository}
.java`; `domain/port/in/PicnicExportUseCase.java`. No framework imports.

**A3 — Migration + persistence adapter** *(after A1+A2, ∥ A4)*
Files: `V6__picnic_credentials.sql`; `infrastructure/security/
CredentialCipher.java` (+ unit test: round-trip, tamper detection);
`adapter/out/persistence/entity/PicnicCredentialsEntity.java`;
`PicnicCredentialsRepositoryAdapter.java` + `PicnicCredentialsRepositoryAdapterIT`
(Testcontainers, per §3).

**A4 — Picnic HTTP adapter** *(after A2, ∥ A3)*
Files: `adapter/out/picnic/PicnicHttpAdapter.java`; add WireMock test
dependency to `build.gradle`; `PicnicHttpAdapterTest` (stub all 3 endpoints +
failure cases, per §3).
**Gate A.** Commit.

### Phase B — Application + web (backend)

**B1 — `PicnicExportService`** *(after Gate A)*
File: `application/service/PicnicExportService.java` implementing
`PicnicExportUseCase`, per §3 (link/unlink/status/suggestions/export,
quantity parsing, partial-failure aggregation). `PicnicExportServiceTest`
covering every branch listed in §3.

**B2 — Controller + error mapping** *(after B1)*
Files: `adapter/in/web/PicnicExportController.java`; `ApiExceptionHandler`
(+3 mappings) + its test; controller test (Mockito/MockMvc style matching
`ShoppingListController`'s test).
**Gate B.** Commit.

### Phase C — Frontend (after Gate B)

**C1 — Schema regen + credentials sheet** *(first)*
Files: regenerate `src/api/schema.ts`; new
`src/features/picnic-export/PicnicCredentialsSheet.tsx` + tests (link
success, wrong-password error, unlink).

**C2 — Suggestions step** *(after C1, ∥ C3)*
File: `PicnicExportSheet.tsx` (suggestions-loading + top-5-picker states) +
tests (loading, per-item picker interaction, skip, credentials-missing
redirect).

**C3 — Confirm/result step** *(after C1, ∥ C2)*
Same file, confirm + result-summary state + tests (success summary, partial
failures rendered, Picnic-unavailable banner doesn't crash the sheet).

**C4 — Entry point + preview harness** *(after C2+C3)*
Files: "Export to Picnic" action in the list header
(`ShoppingListPage.tsx`/`ListsPage.tsx`, wherever the header actions live);
`src/test/preview-main.tsx` (3 new screens + `&sheet=picnic`); `CLAUDE.md`
screen-state list.
**Gate C.** Commit.

### Phase D — Docs + end-to-end (∥)

**D1 — Config/docs plumbing**
Files: `.env.example` + both compose files (`PICNIC_CREDENTIAL_ENC_KEY`);
confirm ADR-0014's consequences still match what was actually built (edit
only if the implementation deviated from the ADR).

**D2 — End-to-end verification** — see §6 below.
Final commit.

---

## 6. End-to-end verification (D2 — run this locally, not in a sandbox)

This needs Docker and a real Picnic account, neither of which are available
in the sandbox this feature was built in (backend Testcontainers ITs and this
whole section were skipped there for that reason). Run it on your machine.

### Prerequisites

- `git checkout claude/picnic-app-export-odrgzg && git pull`.
- Docker running locally (`docker compose up --build` needs it).
- `.env` already exists locally with working `GOOGLE_CLIENT_ID`/`SECRET` and
  `JWT_SECRET` from prior development — if not, copy `.env.example` to `.env`
  and fill those in first (unrelated to this feature).
- **New this feature:** set a real `PICNIC_CREDENTIAL_ENC_KEY` in `.env`
  (generate one with `openssl rand -base64 32` — any length works, see the
  comment above it in `.env.example`). If you skip this, the dev default is
  used, which is fine for a local-only test but must never be used in prod.
- A real Picnic account (NL/DE/BE) you're willing to log into and add real
  items to a real cart with. **Use an account/cart you don't mind having
  items added to** — step 5 below adds real products to a real Picnic cart.
  Don't complete a real checkout/order as part of this test unless you mean
  to.

### Steps

1. `docker compose up --build`. Confirm the stack comes up clean and Flyway
   applies `V6__picnic_credentials.sql` with no errors
   (`docker compose logs backend | grep -i flyway`).
2. Log in via Google, land in a group with at least one list (create one if
   needed), add a handful of real German grocery items to it (e.g. "Milch",
   "Bananen", "Butter", "Vollkornbrot").
3. Open that list, click the basket icon in the header ("Export to Picnic").
   Since no Picnic account is linked yet, you should land on (or be routed
   to) the **credentials sheet** — both sheets now show a small "Beta — uses
   an unofficial Picnic API" badge next to the title; confirm it's visible
   (this was a gap ADR-0014 called for and D1 just added).
4. **Wrong-password check first:** enter your real Picnic email with a
   deliberately wrong password. Expect an inline "Picnic rejected these
   credentials" error, not a crash. Confirm nothing was persisted:
   `docker compose exec postgres psql -U shopmate -d shopmate -c "select count(*) from picnic_credentials;"`
   should be `0`.
5. Now link with the **correct** password. Expect the sheet to switch to the
   linked view. Click the export button again → suggestions should load,
   showing up to 5 real Picnic products per item (name, and where available,
   image/price/unit).
   - **Important spot-check** (flagged by ADR-0014 as unverified
     reverse-engineering): open browser devtools → Network, find the
     `POST /api/lists/{listId}/picnic/suggestions` call, and separately check
     the backend logs or add a temporary breakpoint/log in
     `PicnicHttpAdapter.searchArticles` to see the **raw response Picnic's
     `/search` endpoint actually returns**. Compare its real field names
     (`unit_quantity`, `display_price`, `image_id`, the nested category
     structure) against what `PicnicHttpAdapter` assumes. If real Picnic
     data uses different field names or a different nesting than the code
     expects, suggestions will silently come back with missing
     name/price/image data (not a crash — the mapping is defensive) — note
     the actual field names for a follow-up fix if they differ.
   - Also check whether the image URLs actually resolve (open one in a new
     tab) — the image URL template in the code is an explicit guess, flagged
     in a code comment in `PicnicHttpAdapter`.
6. Pick a mix: accept the default (top) suggestion for one item, change the
   selection for another, explicitly skip at least one item, and confirm.
   Check the result summary's added/skipped counts match your picks.
7. **Ultimate check:** open the real Picnic app or picnic.app in a browser,
   log into the same account, and confirm the picked products are actually
   in the cart with the expected quantities.
8. Unlink the Picnic account from the credentials sheet. Confirm the row is
   gone (`select count(*) from picnic_credentials;` → `0` again) and that
   opening the export sheet now routes back to the credentials prompt
   (`PICNIC_CREDENTIALS_MISSING`).
9. *(Optional, lower priority)* Outage path: temporarily override
   `SHOPMATE_PICNIC_BASE_URL` (Spring relaxed-binding env var for
   `shopmate.picnic.base-url`) to an unreachable address for the `backend`
   service and retry suggestions/export — expect a friendly "Picnic isn't
   available right now" banner, not a crash. Revert the override afterward.

### If something's off

This is explicitly a beta feature built against reverse-engineered behavior
(ADR-0014) — a mismatch in step 5's spot-check is the expected kind of issue
to find, not a sign the whole approach is broken. If you find field-name or
image-URL drift, fix `PicnicHttpAdapter`'s parsing to match reality and add a
regression case to `PicnicHttpAdapterTest` (WireMock stub with the *real*
response shape you observed) rather than adjusting it blind — then re-run
this checklist from step 5.

### After D2 passes

No PR exists yet for this branch. Once you're satisfied, open one manually
(or ask Claude Code to) — the feature is one PR per the original brief, so
everything from Phase A through D1 plus your D2 fixes (if any) ships together.

## Critical files

`api/openapi.yaml` · `backend/src/main/java/com/shopmate/application/service/
PicnicExportService.java` (new) · `backend/src/main/java/com/shopmate/
adapter/out/picnic/PicnicHttpAdapter.java` (new) · `backend/src/main/java/
com/shopmate/infrastructure/security/CredentialCipher.java` (new) ·
`backend/src/main/resources/db/migration/V6__picnic_credentials.sql` (new) ·
`frontend/src/features/picnic-export/PicnicExportSheet.tsx` (new) ·
`docs/adr/0014-picnic-export-beta.md`
