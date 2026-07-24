# Implementation Plan: Copy a shopping list

**Status:** Not started — ready for implementation.

No ADR: this is a feature-level change that folds under
[ADR-0013](../adr/0013-group-tenancy-invite-codes.md) (group tenancy).
No DB migration and no schema change — copy reuses the existing
`shopping_lists` / `shopping_items` tables via the existing whole-graph `save`,
and `createdAt` already exists end-to-end.

## Context

After a shopping trip, users want to start next week's list without re-typing
everything — ~80% of the items repeat. Today the only way to make a list is an
empty `POST /lists`, so people rebuild the same groceries from scratch every week.

This feature adds **"copy list"**: duplicate an existing list's items into a new
list, ready for a fresh trip.

## Product decisions (settled, do not reopen)

- **Copy scope:** all active (non-deleted) items — the old list is a template.
- **Checked state:** copied items are reset to **unchecked** (fresh trip).
- **Naming:** copying opens a small sheet pre-filled with `"<name> (copy)"` that
  the user can edit before confirming.
- **List ordering:** lists are ordered **newest-first** by `createdAt`, so the
  list you just copied appears at the top.

Group-less-caller behavior (per CLAUDE.md, every new endpoint must decide this):
`copyList` goes through `requireUserWithGroup`, so a group-less user gets
`403 NO_GROUP`, and a cross-group source list gets `403 ACCESS_FORBIDDEN` — same
as every other list op.

---

## Backend

### 1. OpenAPI contract — `api/openapi.yaml` (source of truth)

Add a copy operation as a new path under the `/lists/{listId}` block:

```yaml
  /lists/{listId}/copy:
    post:
      tags: [Lists]
      operationId: copyList
      summary: Copy a list's active items into a new list
      parameters:
        - $ref: '#/components/parameters/ListId'   # source list
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CopyListRequest'
      responses:
        "201": { description: Created list, content: { application/json: { schema: { $ref: '#/components/schemas/ShoppingList' } } } }
        "400": { $ref: '#/components/responses/BadRequest' }
        "401": { $ref: '#/components/responses/Unauthorized' }
        "403": { $ref: '#/components/responses/Forbidden' }
        "404": { $ref: '#/components/responses/NotFound' }
```

Add schema `CopyListRequest` next to `CreateListRequest`: one field `name`,
`type: string, minLength: 1, maxLength: 100` (bean-validation handles length,
mirroring how `CreateListRequest` is validated — there is no name check in the
service). Returns `ShoppingList` (header only, like `createList`).

Regeneration is automatic on build (`./gradlew openApiGenerate`), which
regenerates `ListsApi` with a `copyList` default method to override.

### 2. Inbound port — `domain/port/in/ShoppingListUseCase.java`

Add: `ShoppingList copyList(UUID sourceListId, String newName, UUID requestingUserId);`

### 3. Service — `application/service/ShoppingListService.java`

Implement `copyList`, mirroring `createList` for the header and `addItem` for the
items. Reuse existing helpers: `requireUserWithGroup`, `findListOrThrow`,
`requireSameGroup`.

```java
@Override
public ShoppingList copyList(UUID sourceListId, String newName, UUID requestingUserId) {
    User user = requireUserWithGroup(requestingUserId);
    ShoppingList source = findListOrThrow(sourceListId);
    requireSameGroup(source, user);

    UUID newListId = UUID.randomUUID();
    long ts = System.currentTimeMillis();
    Map<UUID, ShoppingItem> items = new HashMap<>();

    // source.activeItems() is already deleted-filtered and sorted by sortKey.
    for (ShoppingItem src : source.activeItems()) {
        UUID itemId = UUID.randomUUID();
        items.put(itemId, new ShoppingItem(itemId, newListId,
            new LwwField<>(src.name().value(),     ts, requestingUserId),
            new LwwField<>(src.quantity().value(), ts, requestingUserId),
            new LwwField<>(false,                  ts, requestingUserId), // checked reset
            new LwwField<>(false,                  ts, requestingUserId), // deleted
            new LwwField<>(src.sortKey().value(),  ts, requestingUserId), // preserve order
            new LwwField<>(src.section().value(),  ts, requestingUserId), // keep learned section
            Map.of()));
    }

    ShoppingList copy = new ShoppingList(newListId, newName, requestingUserId,
        user.groupId(), Map.copyOf(items), Instant.now());
    return listRepository.save(copy);   // single whole-graph upsert
}
```

Notes:
- **Reuse the source `sortKey` values** — they are valid fractional indices and
  preserve order without recomputing.
- **Copy the stored `section` value directly** — it already holds the
  classified/learned result; no need to re-run `SectionClassifier` or consult
  `sectionCorrectionRepository` (a brand-new list has no corrections yet).
- **No SSE publish** — the new list has no subscribers; unlike `addItem`, don't
  broadcast item changes.
- Source with up to `MAX_ITEMS` (100) active items copies to exactly 100 — within
  limit, no capacity check needed.

### 4. Ordering — newest-first

- `adapter/out/persistence/repository/SpringDataShoppingListRepository.java`:
  rename `findAllByGroupId` → `findAllByGroupIdOrderByCreatedAtDesc(UUID groupId)`.
- `adapter/out/persistence/ShoppingListRepositoryAdapter.java`: update the call
  site to the new method name (the out-port method name on
  `ShoppingListRepository` can stay `findAllByGroupId`; only the Spring Data query
  method changes).
- No index needed (per-group list volume is tiny).

### 5. Controller — `adapter/in/web/ShoppingListController.java`

Override the generated `copyList(UUID listId, CopyListRequest body)`:
resolve caller via `securityContextHelper.getCurrentUserId()`, call
`useCase.copyList(listId, body.getName(), userId)`, return
`ResponseEntity.status(201).body(toDto(saved))` — same shape as `createList`.

### 6. Backend tests (90% coverage gate)

- `ShoppingListServiceTest`: copy duplicates all active items with new UUIDs and
  `newListId`; **checked reset to false**; deleted items are **not** copied; new
  `createdAt`; sortKey/section/name/quantity carried over; source not mutated.
  Error paths: `NoGroupException` (group-less caller), `AccessForbiddenException`
  (cross-group source), `ListNotFoundException` (missing source).
- Controller/MockMvc test for `POST /lists/{listId}/copy`: 201 + body, 400 on
  blank/too-long name, 403/404 propagation.

---

## Frontend — `frontend/src/features/shopping-list/components/ListsPage.tsx`

Regenerate the client first: `cd frontend && npm run generate-api` (adds the
`/lists/{listId}/copy` POST path to `src/api/schema.ts`).

### 1. Render order

Backend now returns newest-first. Update the two client-side inserts so new lists
land at the **top**, matching server order:
- create success: `setLists((prev) => [data, ...prev])`
- copy success (new): same prepend.

### 2. Per-list Copy action (first list-level action in the app)

The list row is currently a single `<button>`; a button can't nest a button.
Least-invasive approach: make the `<li>` (or a wrapping `<div>`) `relative`, keep
the full-width navigate `<button>`, and add a **sibling** absolutely-positioned
copy icon `<button>` at the right edge (just inside the chevron), with
`aria-label="Copy list"`. Give the navigate button extra right padding so the
name text doesn't underlap the copy button. Style the icon button after the
inline icon-button pattern in `ItemRow.tsx` (icon `<button>` + `aria-label`,
`min-h-touch min-w-touch`, focus-visible outline).

### 3. Copy sheet (prompt for name)

Add state `copySource: ShoppingList | null` and `copyName: string`. Clicking a
row's Copy button sets `copySource = list` and `copyName = `${list.name} (copy)``.
Render a sheet that **mirrors the existing create sheet** — backdrop,
`role="dialog"`, `aria-modal`, text input `maxLength={100}`, Cancel / Copy
buttons, Escape-to-close (extend the existing key handler or add one).

Submit handler `handleCopy`:
```ts
const { data, error } = await apiClient.POST('/lists/{listId}/copy', {
  params: { path: { listId: copySource.id } },
  body: { name: copyName.trim() },
})
if (!error && data) {
  setLists((prev) => [data, ...prev])
  setCopySource(null)
  navigate(`/lists/${data.id}`)
}
```
Guard on `!copyName.trim()`, disable the confirm button while in-flight (reuse
the `creating`-style pattern).

### 4. Frontend tests — `__tests__/ListsPage.test.tsx` (90% coverage gate)

- Add `POST` branching in the `apiClient` mock (it already mocks `GET`/`POST`;
  branch `POST` on the called path so `/lists` vs `/lists/{listId}/copy` differ,
  as the group-sheet test already branches `GET` on `url`).
- Copy flow: click a row's "Copy list" button → sheet opens with input value
  `"<name> (copy)"` → submit → assert `POST` called with the source `listId` path
  param and edited `name` → assert navigation to the new list id.
- Ordering: assert a newly created list renders **first** (prepend), reusing the
  `makeList(id, name)` helper which already sets `createdAt`.

---

## Files to touch (summary)

Backend:
- `api/openapi.yaml` — `copyList` op + `CopyListRequest` schema
- `domain/port/in/ShoppingListUseCase.java` — `copyList` signature
- `application/service/ShoppingListService.java` — `copyList` impl
- `adapter/out/persistence/repository/SpringDataShoppingListRepository.java` — order by `createdAt` desc
- `adapter/out/persistence/ShoppingListRepositoryAdapter.java` — call-site rename
- `adapter/in/web/ShoppingListController.java` — endpoint override
- `application/service/ShoppingListServiceTest.java` (+ controller test) — coverage

Frontend:
- `src/api/schema.ts` — regenerated (`npm run generate-api`)
- `src/features/shopping-list/components/ListsPage.tsx` — copy button, copy sheet, prepend
- `src/features/shopping-list/__tests__/ListsPage.test.tsx` — copy + ordering tests

No DB migration. No new ADR.

---

## Verification

1. **Backend build + tests + coverage gate:**
   `cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew check`
   (runs ArchUnit layer rules + JaCoCo 90% line/branch).
2. **Frontend tests + coverage:**
   `cd frontend && npm run test:coverage` (90% gate).
3. **End-to-end (Docker):** `docker compose up --build`, log in, create a list
   with a few items, check some off, then use the row's Copy button. Confirm:
   - the copy opens with `"<name> (copy)"` pre-filled and is editable;
   - the new list contains **all** items from the source, **all unchecked**, in
     the same order and same sections;
   - the source list is unchanged;
   - back on the lists page, the newest list (the copy) is at the **top**.
4. **Visual preview (no backend):** the mock preview
   (`npm run dev` → `preview.html?screen=lists`) still renders the rows; verify
   the new Copy button doesn't break row layout or the navigate tap target.
