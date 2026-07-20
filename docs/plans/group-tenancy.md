# Multi-Tenancy (Groups + Invite Codes) — Implementation Plan

## Context

ShopMate today has no tenant boundary: any Google account can sign in and is auto-provisioned as a user. List visibility is per-list (owner + explicitly shared members via the Share sheet), so a household ends up sharing every list manually — and any stranger who logs in lands in the same flat user space. The goal is real multi-tenancy:

- Users belong to exactly **one group** (tenant). All lists belong to a group; every group member sees all of the group's lists.
- Signup is gated by **invite codes**, obtainable in-app by any group member. Two types:
  - **JOIN_GROUP** — redeemer joins the issuer's group.
  - **NEW_GROUP** — redeemer creates a brand-new group and names it at redemption (first joiner names the group).
- Google login stays open (auto-provisioning unchanged), but a user without a group can do nothing until they redeem a code.

### Key design decisions (captured in ADR-0013, written as part of this plan)

1. **Group membership resolved from the DB per request, not a JWT claim.** A claim would be stale the moment a fresh user redeems a code right after login (JWTs live 24 h). JWT stays subject-only.
2. **Group supersedes per-list membership.** `shopping_lists.group_id` becomes the scoping boundary; the `list_members` table, `addMember`/`removeMember` endpoints, and the frontend Share sheet are **removed**. `owner_id` kept for display. *(This deletes the existing share-by-email feature — intentional: group membership makes it redundant.)*
3. **Invite codes are single-use, 7-day expiry**, 8 chars from `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` (no ambiguous I/O/0/1), generated with `SecureRandom`.
4. **Backfill migration:** prod has a live household — Flyway creates one default group (name `ShopMate`, renameable later if we ever add rename) and assigns all existing users and lists to it.
5. **Bootstrap for fresh environments:** optional property `shopmate.invites.bootstrap-code` (env `BOOTSTRAP_INVITE_CODE`, empty = disabled) acts as a reusable NEW_GROUP code — needed because codes otherwise only come from existing members. Docs say to unset after bootstrapping.
6. **SSE authorization fix folded in** (pre-existing gap, verified in code): `SseTokenController.getSseToken` issues tokens for any listId without a membership check, and `SseController.subscribe` never compares the token's listId claim to the path. Both get fixed with group checks.
7. **Contract-first:** all API changes land in `api/openapi.yaml` first; codegen on both ends (`./gradlew openApiGenerate`, `npm run generate-api`).
8. **No code-inspection endpoint:** `POST /invites/redeem` without `groupName` on a NEW_GROUP code returns `400 GROUP_NAME_REQUIRED`; the frontend reveals the "name your group" step and resubmits. One endpoint, no code-type leakage.

### Verified codebase facts

- Backend hexagonal (ArchUnit-enforced: domain must not depend on adapter/Spring/JPA/application; application must not depend on adapter). Latest Flyway migration **V3**; `ddl-auto: validate`. Controllers implement generated OpenAPI interfaces with class-level `@RequestMapping("/api")`.
- `ShoppingListService.getListsForUser` → `listRepository.findAllByMemberId(userId)`; `addMember`/`removeMember` + `requireMember`/`requireOwner` exist as described (backend/src/main/java/com/shopmate/application/service/ShoppingListService.java).
- Auth: `GoogleOAuth2SuccessHandler` auto-provisions by email and **rebuilds the User record on every login** — must preserve `groupId` (bug trap). `SecurityContextHelper.getCurrentUserId()` = JWT subject.
- Frontend: `AuthContext` hydrates via `GET /users/me`, JWT in localStorage; no settings screen exists; sheets follow the `sheet-backdrop`/`sheet-panel` idiom with `SectionSheet.tsx` as reusable template; preview harness `src/test/preview-main.tsx`.
- Coverage gates: backend 90 % line+branch (`./gradlew check`), frontend 90 % (`npm run test:coverage`); domain target 100 %.

---

## 1. OpenAPI contract changes (`api/openapi.yaml`)

**Removed:** `/lists/{listId}/members` (POST), `/lists/{listId}/members/{userId}` (DELETE), `AddMemberRequest`, `Members` tag.

**Changed schemas:**
- `UserProfile`: add optional `group` → `GroupSummary` (absent/null = no group).
- `ShoppingList`: remove `members`; add required `groupId` (uuid).

**New schemas:** `GroupSummary {id, name}` · `GroupResponse {id, name, createdAt, members: UserProfile[]}` · `InviteType` enum `[JOIN_GROUP, NEW_GROUP]` · `CreateInviteRequest {type}` · `InviteCodeResponse {code, type, expiresAt}` · `RedeemInviteRequest {code (required), groupName (optional, maxLength 100)}`.

**New paths** (tags `Groups`, `Invites` → generated `GroupsApi`, `InvitesApi`):
- `GET /groups/me` → 200 `GroupResponse`; 403 `NO_GROUP`.
- `POST /invites` → 201 `InviteCodeResponse`; 403 `NO_GROUP`.
- `POST /invites/redeem` → 200 `UserProfile` (updated, `group` set — frontend updates AuthContext in one shot); 400 `GROUP_NAME_REQUIRED`; 409 `ALREADY_IN_GROUP`; 422 `INVITE_INVALID` / `INVITE_EXPIRED`.

**Group-less-user contract:** any list/group/invite-creation call with `groupId == null` → **403 with `code: "NO_GROUP"`** (distinct from `ACCESS_FORBIDDEN`, so the frontend can route to onboarding). No `SecurityConfig` changes needed (`anyRequest().authenticated()` already covers the new paths).

## 2. Database migrations

**`V4__groups_and_invites.sql`** — purely additive (Phase A):
- `groups (id UUID PK, name VARCHAR(100) NOT NULL, created_at)`.
- `invite_codes (id UUID PK, code VARCHAR(16) UNIQUE, type CHECK IN ('JOIN_GROUP','NEW_GROUP'), group_id UUID NULL REFERENCES groups, created_by REFERENCES users, created_at, expires_at, used_by NULL REFERENCES users, used_at NULL)`.
- `users.group_id UUID NULL REFERENCES groups` (stays nullable), `shopping_lists.group_id UUID NULL REFERENCES groups` (nullable **until V5**).
- Backfill: insert default group `'ShopMate'` (fixed UUID `…0001`) **only if users exist**; `UPDATE users/shopping_lists SET group_id = …` where null. Indexes on both `group_id` columns.

**`V5__group_scoping_enforced.sql`** — same commit as the entity switch (Phase B): `ALTER shopping_lists.group_id SET NOT NULL; DROP TABLE list_members;`

*Why split:* `ddl-auto: validate` + the `@ManyToMany` join table means dropping `list_members` in V4 would break the app and every Testcontainers IT until the entity change lands, forcing one big-bang task. V4 ships with Phase A green; V5 ships atomically with the entity/domain switch. Both deploy together as one release.

## 3. Backend design

**Domain (`domain/model/`, pure Java):**
- `Group(id, name, createdAt)`; `InviteType` enum; `InviteCode(id, code, type, groupId, createdBy, createdAt, expiresAt, usedBy, usedAt)` with `isExpired(now)`/`isUsed()`.
- New exceptions: `NoGroupException` (403 NO_GROUP), `InviteInvalidException` (422), `InviteExpiredException` (422), `AlreadyInGroupException` (409), `GroupNameRequiredException` (400).
- `User` gains nullable `UUID groupId`. `ShoppingList`: `memberIds` → `UUID groupId`; drop `isMember`, keep `isOwner`.

**Ports:**
- `port/in/GroupUseCase`: `getGroupForUser(userId)`, `getGroupMembers(userId)`.
- `port/in/InviteUseCase`: `createInvite(userId, type)`, `redeemInvite(userId, code, groupName)`.
- `port/in/ShoppingListUseCase`: remove `addMember`/`removeMember`; add `assertListAccess(listId, userId)` (for SSE token issuance).
- `port/out/GroupRepository` (`findById`, `save`); `port/out/InviteCodeRepository` (`findByCode`, `save`, `markUsed(inviteId, usedBy, usedAt)` — conditional `UPDATE … WHERE used_by IS NULL`, race-safe single-use); `UserRepository.findAllByGroupId`; `ShoppingListRepository.findAllByMemberId` → `findAllByGroupId`.

**Application services:**
- `ShoppingListService`: every method starts with `requireUserWithGroup(requestingUserId)` (throws `NoGroupException`); `getListsForUser` → `findAllByGroupId`; `createList` stamps caller's groupId; `requireMember` → `requireSameGroup(list, user)`; delete `addMember`/`removeMember`; new `assertListAccess`.
- `GroupService`, `InviteService` per the flows above; bootstrap code checked before DB lookup, treated as reusable NEW_GROUP (redeem always requires a group-less caller, so it's intrinsically bounded).

**Persistence adapters:** `GroupEntity`, `InviteCodeEntity` (plain-UUID columns, following the existing `owner_id` pattern — no JPA associations); `UserEntity.groupId`; `ShoppingListEntity` drops `@ManyToMany members`, gains `group_id`; adapter drops the member-sync + "owner is always a member" injection.

**Web/SSE:**
- `ShoppingListController` stops implementing `MembersApi`, drops member-profile mapping (and its `UserRepository` dep), adds `groupId` to DTOs.
- New `GroupController implements GroupsApi`, `InviteController implements InvitesApi` (same `@RequestMapping("/api")` pattern).
- `UserController` populates `UserProfile.group`. `ApiExceptionHandler`: five new mappings.
- `GoogleOAuth2SuccessHandler`: **preserve `existing.groupId()` on re-login** (it currently rebuilds the User record — the one subtle bug trap).
- `SseTokenController`: `assertListAccess` before issuing. `SseController`: reject token whose `listId` claim ≠ path listId (403).
- Config: `shopmate.invites.bootstrap-code: ${BOOTSTRAP_INVITE_CODE:}` in `application.yml`; pass-through in both compose files + `.env.example`.

## 4. Frontend design

- `AuthContext`: `UserProfile.group?: {id, name} | null`; add `refreshUser()`; used after redemption.
- New `RequireGroup` guard (sibling of `ProtectedRoute`): grouped user passes; group-less → `/welcome`. Routes `/lists`, `/lists/:listId` become `ProtectedRoute > RequireGroup > page`; new `/welcome` = `ProtectedRoute > OnboardingPage` (navigates to `/lists` if user already has a group).
- `OnboardingPage` (`src/features/onboarding/`): code entry → `POST /invites/redeem {code}`; on `GROUP_NAME_REQUIRED` reveal name field and resubmit with `groupName`; on success update user + navigate `/lists`; friendly messages for `INVITE_INVALID`/`INVITE_EXPIRED`/`ALREADY_IN_GROUP`. Style per marigold system (`PRODUCT.md`/`DESIGN.md`).
- `GroupSheet` (`src/features/group/`): opened from a new settings entry in the `ListsPage` header; shows group name + members (`GET /groups/me`); two actions ("Invite to this group" / "Invite for a new group") → `POST /invites {type}` → code + expiry + copy-to-clipboard. Reuse the `SectionSheet` sheet idiom.
- **Removals:** Share sheet block in `ShoppingListPage.tsx`; `members` in `ListsPage.tsx`'s local interface/UI.
- Preview harness: fixtures for the new endpoints; screens `welcome`, `welcome-name`; `&sheet=group` replaces `&sheet=share`; update the state list in `CLAUDE.md`.

---

## 5. Task breakdown (orchestration: Opus lead, Sonnet executors)

Gates: **backend** `cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew check` · **frontend** `cd frontend && npm run lint && npm run test:coverage && npm run build`. Build may be red only where marked mid-phase; every **Gate** must be fully green before the next phase. One commit per phase (repo convention).

**Dependency graph:** A1 ∥ A2 → A3 → A4 → *Gate A* → B1 → B2 → B3 ∥ B4 → *Gate B* → C1 → C2 ∥ C3 → C4 → *Gate C* → D1 ∥ D2. "∥" pairs touch disjoint files and are safe to run as parallel agents; if working a single branch sequentially, just run them back-to-back.

### Phase A — Additive backend groundwork (green throughout)

**A1 — Flyway V4 migration + backfill IT** *(∥ A2)*
Files: `backend/src/main/resources/db/migration/V4__groups_and_invites.sql` (new, per §2); new `V4BackfillMigrationIT.java`.
Reuse: Testcontainers postgres:16 setup from `ShoppingListRepositoryAdapterIT` (`@ActiveProfiles("integration-test")`).
Tests: use the Flyway API — migrate to target V3, seed user+list+list_members via JDBC, migrate to V4, assert default group exists and user/list have `group_id`; assert V4 on an empty DB creates no group. Existing suite stays green (additive columns, entities untouched).

**A2 — Domain additions + `User.groupId` ripple** *(∥ A1)*
Files: new `domain/model/{Group,InviteCode,InviteType}.java` + 5 exceptions; new `domain/port/in/{GroupUseCase,InviteUseCase}.java`, `domain/port/out/{GroupRepository,InviteCodeRepository}.java`; extend `User.java`, `UserRepository.java` (`findAllByGroupId`).
Ripple: `GoogleOAuth2SuccessHandler` (**preserve `existing.groupId()`**, null for new users) + its test (add groupId-preservation case); every `new User(...)` call site in tests/adapters.
Tests: `InviteCode.isExpired/isUsed` unit tests (domain 100 %).
Note: `UserRepositoryAdapter.findAllByGroupId` needs the A3 entity column — fold the adapter method into A3; A2 keeps the port + a stub only if needed to compile.

**A3 — Persistence for groups/invites/user.group** *(after A2)*
Files: new `adapter/out/persistence/entity/{GroupEntity,InviteCodeEntity}.java`; `UserEntity` + `group_id`; new Spring Data repos (incl. `@Modifying` conditional-update `markUsed`) + `{Group,InviteCode}RepositoryAdapter`; finish `UserRepositoryAdapter`.
Tests: `GroupInviteRepositoryAdapterIT` — save/find group, find invite by code, **`markUsed` true once then false**, `findAllByGroupId`.

**A4 — GroupService + InviteService** *(after A3)*
Files: new `application/service/{GroupService,InviteService}.java`; `application.yml` bootstrap-code property.
Reuse: constructor-injection + Mockito style of `ShoppingListService`/`-Test`.
Tests: `InviteServiceTest` — JOIN happy path; NEW_GROUP happy path; missing groupName → `GroupNameRequiredException`; unknown/used → `InviteInvalidException`; expired → `InviteExpiredException`; already grouped → `AlreadyInGroupException`; bootstrap path; code format (8 chars, alphabet); collision retry. `GroupServiceTest` — `NoGroupException`, members listing.
**Gate A.** Commit.

### Phase B — Breaking backend switch

**B1 — OpenAPI contract rewrite** *(red after, by design)*
File: `api/openapi.yaml` per §1. Verify `./gradlew openApiGenerate` emits `GroupsApi`/`InvitesApi` and no `MembersApi`; `npm run generate-api` succeeds.

**B2 — Group-scoped list domain + persistence + service** *(after B1; largest task)*
Files: `domain/model/ShoppingList.java` (memberIds → groupId), `domain/port/in/ShoppingListUseCase.java`, `ShoppingListEntity`, `ShoppingListRepositoryAdapter`, `SpringDataShoppingListRepository` (`findAllByGroupId` derived query), `ShoppingListService` (per §3), **new `V5__group_scoping_enforced.sql`**, plus `ShoppingListServiceTest`, `ShoppingListApplyChangeTest`, `ShoppingListRepositoryAdapterIT` (fixtures now need a group + grouped users).
Tests: group-less caller → `NoGroupException` on every op; cross-group → `AccessForbiddenException`; same-group non-owner can mutate; `createList` stamps group; `assertListAccess` both paths; IT: `findAllByGroupId` includes lists owned by other members.
Note: `ShoppingListController` won't compile until B3 — expected.

**B3 — Controllers + error mapping** *(after B2, ∥ B4)*
Files: `ShoppingListController` (drop `MembersApi` + member mapping, add `groupId`), new `GroupController`, `InviteController`, `UserController` (populate `group`), `ApiExceptionHandler` (+5 mappings) and their tests (plain-Mockito style, mock `SecurityContextHelper`).

**B4 — SSE hardening** *(after B2, ∥ B3)*
Files: `SseTokenController` (call `assertListAccess`), `SseController` (claim-vs-path 403), their tests.
Tests: issuance 403 for other group's list and for group-less user; subscribe with mismatched-list token → 403; matching token → 200.
**Gate B.** Commit.

### Phase C — Frontend (after Gate B)

**C1 — Schema regen + de-share + user shape** *(first)*
Files: regenerate `src/api/schema.ts`; `AuthContext.tsx` (`group`, `refreshUser`); `ShoppingListPage.tsx` (remove Share sheet: state, button, sheet block); `ListsPage.tsx` (remove `members`); fix affected tests (`/users/me` mocks now include `group`).

**C2 — Onboarding flow + RequireGroup guard** *(after C1, ∥ C3)*
Files: new `src/features/auth/RequireGroup.tsx`, new `src/features/onboarding/OnboardingPage.tsx`, `src/main.tsx` (routes); tests for both.
Reuse: `ProtectedRoute` guard pattern; `LoginPage` centered-card layout.
Tests: guard redirect + pass-through; JOIN success → `/lists`; `GROUP_NAME_REQUIRED` reveals name step, resubmit succeeds; invalid/expired messages.

**C3 — GroupSheet + invite generation** *(after C1, ∥ C2)*
Files: new `src/features/group/GroupSheet.tsx`; `ListsPage.tsx` header entry point; tests (stub `navigator.clipboard`).
Reuse: `SectionSheet.tsx` sheet idiom.
Tests: renders name/members; per-type invite → code + expiry; copy calls clipboard; error state.

**C4 — Preview harness + CLAUDE.md** *(after C2+C3)*
Files: `src/test/preview-main.tsx` (fixtures + screens `welcome`, `welcome-name`, `&sheet=group`, drop `share`); `CLAUDE.md` screen-state list.
**Gate C.** Commit.

### Phase D — Docs + end-to-end (∥)

**D1 — ADR-0013 + docs**
Files: new `docs/adr/0013-group-tenancy-invite-codes.md` (outline in §6, match ADR-0012's Nygard format exactly); `docs/adr/README.md` index row; one-line superseded-pointer in ADR-0012 ("no household entity" remark); `CLAUDE.md` (architecture + security notes: invite gating, SSE checks); `.env.example` + both compose files (`BOOTSTRAP_INVITE_CODE`).

**D2 — E2E verification** — see §7.
Final commit.

---

## 6. ADR-0013 outline

`docs/adr/0013-group-tenancy-invite-codes.md` — `# ADR-0013: Group tenancy with single-use invite codes` · `Date: 2026-07-20 · Status: Accepted`.

- **Context:** household app; per-list email sharing forces re-sharing every list and any Google account can sign up into a usable app; ADR-0012 noted "there is no household entity". Options:
  - **A.** Keep per-list sharing + signup allow-list — sharing friction remains, allow-list needs ops per user. Rejected.
  - **B.** Group tenancy + static per-group join code — leaked code is a permanent door, manual revocation. Rejected.
  - **C.** Group tenancy + single-use expiring codes (JOIN_GROUP/NEW_GROUP), issued in-app by any member. **Chosen.**
  - Sub-decision: group from DB per request vs JWT claim — claim is stale the instant a fresh user redeems post-login; one indexed PK read per request is cheap.
- **Decision:** groups as single scoping boundary; list membership = group membership; `list_members` + share feature removed; code mechanics (single-use, 7 d, 8 chars A–Z2–9); bootstrap-code + backfill migration; SSE issuance/subscribe verification.
- **Consequences:** breaking API change (single deployable unit per ADR-0009 makes this acceptable); one-way data migration (V4/V5); no multi-group membership or cross-group sharing by design; "group-less authenticated user" is a new state every future endpoint must consider (`NO_GROUP` 403 contract); ADR-0012 remark superseded; bootstrap code must be unset after use.

## 7. End-to-end verification

1. Both coverage gates green (commands in §5 header).
2. **Migration on live-like data:** `docker compose up --build` against a volume with pre-V4 data (or seed V3 state as in the A1 IT): Flyway applies V4+V5; default group exists; users/lists carry it; `list_members` gone (`docker compose exec postgres psql -U shopmate -c '\d shopping_lists'`).
3. **API flow without Google** (mint dev-secret HS256 JWTs, subject = seeded user UUID — see memory note "Local E2E Test Setup"):
   - Fresh user: `GET /api/lists` → 403 `NO_GROUP`; `POST /api/invites` → 403 `NO_GROUP`.
   - With `BOOTSTRAP_INVITE_CODE=SETUP123`: redeem without name → 400 `GROUP_NAME_REQUIRED`; with `groupName` → 200, `group` set.
   - Create list; `POST /api/invites {JOIN_GROUP}` → code; second user redeems → sees first user's list; code reuse → 422 `INVITE_INVALID`.
   - Third user redeems NEW_GROUP code → sees zero lists; sse-token for other group's list → 403; subscribe with mismatched-list token → 403.
4. **Preview harness:** `preview.html?screen=welcome`, `welcome-name`, `lists&sheet=group`; Share button absent on `screen=list`.
5. **Browser smoke (optional, Google creds):** login → `/welcome` → redeem → create list → JOIN code → second account redeems → sees the list live via SSE.

## Critical files

`api/openapi.yaml` · `backend/src/main/java/com/shopmate/application/service/ShoppingListService.java` · `backend/src/main/java/com/shopmate/adapter/out/persistence/ShoppingListRepositoryAdapter.java` · `backend/src/main/resources/db/migration/V4__groups_and_invites.sql` (new) · `backend/src/main/java/com/shopmate/infrastructure/security/GoogleOAuth2SuccessHandler.java` · `frontend/src/features/auth/AuthContext.tsx` · `frontend/src/main.tsx` · `docs/adr/0013-group-tenancy-invite-codes.md` (new)
