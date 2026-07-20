# ADR-0013: Group tenancy with single-use invite codes

Date: 2026-07-20 · Status: Accepted

## Context

ShopMate had no tenant boundary. Any Google account that signed in was
auto-provisioned into one flat user space, and list visibility was per-list
(owner plus explicitly shared members). A household therefore re-shared every
new list by email, and nothing stopped a stranger who signed in from holding a
usable account. ADR-0012 noted the gap directly: "there is no household
entity; the shared list is the closest proxy."

Options considered:

- **A. Keep per-list sharing, add a signup allow-list.** Smallest change, but
  the sharing friction — the actual daily annoyance — remains, and the
  allow-list needs an ops action per person. Rejected.
- **B. Group tenancy with a static per-group join code.** Fixes sharing, but a
  leaked code is a permanent open door; revocation means rotating the code for
  everyone. Rejected.
- **C. Group tenancy with single-use expiring invite codes**, issued in-app by
  any member. Fixes sharing, and a leaked code is worth at most one join and
  expires on its own. Chosen.

Sub-decision — **where group membership is resolved.** A JWT claim avoids a
read but is stale the instant a fresh user redeems a code right after login,
and JWTs live 24 h; the user would appear group-less for a day. One indexed
primary-key read per request is cheap. Membership is resolved from the DB.

## Decision

Adopt **C**. The group is the single scoping boundary.

- **A user belongs to exactly one group; every list belongs to a group.**
  `shopping_lists.group_id` replaces per-list membership entirely — the
  `list_members` table, the `addMember`/`removeMember` endpoints, and the
  frontend Share sheet are removed. `owner_id` is kept for display only. Any
  member of a group may read and mutate any of that group's lists.
- **Invite codes are single-use and expire after 7 days**, 8 characters from
  `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` (no ambiguous I/O/0/1 — these get read
  aloud and retyped), generated with `SecureRandom`. Two types: `JOIN_GROUP`
  (redeemer joins the issuer's group) and `NEW_GROUP` (redeemer creates a
  group and names it at redemption). Single-use is enforced by a conditional
  `UPDATE … WHERE used_by IS NULL`, not read-then-write, so concurrent
  redemption cannot double-spend a code.
- **Google login stays open; authorization is what changes.** A signed-in user
  without a group can do nothing with lists. Every such call returns **403
  with code `NO_GROUP`**, deliberately distinct from `ACCESS_FORBIDDEN` so the
  frontend can route to onboarding rather than showing an error.
- **Group membership is resolved from the DB per request.** The JWT stays
  subject-only.
- **Migration is two-step.** `V4` adds the tables and nullable `group_id`
  columns and backfills a default group when users already exist (prod has a
  live household); `V5` sets `group_id NOT NULL` and drops `list_members`.
  The split exists because `ddl-auto: validate` means dropping the join table
  before the entity change would break the app and every integration test.
- **`shopmate.invites.bootstrap-code`** (env `BOOTSTRAP_INVITE_CODE`, empty =
  disabled) acts as a reusable `NEW_GROUP` code, because codes otherwise only
  come from existing members and a fresh environment has none. It is bounded
  by redemption requiring a group-less caller, and must be unset once used.
- **SSE authorization is fixed as part of this change** (pre-existing, not
  introduced here): token issuance now asserts list access, and `subscribe`
  rejects a token whose `listId` claim does not match the path.

## Consequences

- Breaking API change. Acceptable because frontend and backend ship as one
  deployable unit (ADR-0009); they must be deployed together.
- One-way data migration. `list_members` is dropped; per-list sharing cannot
  be restored without a new migration.
- No multi-group membership and no cross-group sharing, by design. A user who
  belongs to two households is not modelled.
- **"Group-less authenticated user" is a new state every future endpoint must
  consider.** The `NO_GROUP` 403 contract is now part of the API surface.
- ADR-0012's "there is no household entity" remark is superseded; the learned
  section-correction map stays per-list rather than per-group, which is now a
  deliberate narrowing rather than a missing concept.
- The bootstrap code is a standing credential while set. Docs say to unset it
  after bootstrapping.
