# Implementation Plan: Auto-group items by supermarket section

**Status:** All five phases completed 2026-07-19. Phase 5 (hardening) ran
both coverage gates green and live two-user API convergence checks; it
surfaced a pre-existing, general (not section-specific) concurrent-write
lost-update bug (BUG-9, PLAN.md Open bugs) — documented, not fixed, per
phase 5's scope.

Decision record: [ADR-0012](../adr/0012-section-classification-dictionary-plus-learned-corrections.md).
Read that first — this doc is the *how*, the ADR is the *what/why*.

Five phases, each an independently verifiable task for a subagent. Phases 1–3
are backend and ship dark (items carry sections, UI unchanged). Phase 4 is the
UI. The recurring template: **copy the SORT_KEY field's path through every
layer** — it threads identically through contract, domain, service,
controller, entity, adapter, and frontend merge code.

## Product decisions (settled, do not reopen)

- Item names are German only.
- Fixed canonical 14-section taxonomy in default walk order (below).
- Items display grouped under section headers; drag-to-reorder works within a
  section; dragging across sections reassigns the item's section.
- Misclassified/unknown items land in Sonstiges; a user correction is
  remembered per list.

## Canonical taxonomy (walk order)

| # | Code | German label (frontend-only) |
|---|------|------------------------------|
| 1 | `OBST_GEMUESE` | Obst & Gemüse |
| 2 | `BROT_BACKWAREN` | Brot & Backwaren |
| 3 | `MOLKEREI_EIER` | Molkerei & Eier |
| 4 | `FLEISCH_FISCH` | Fleisch, Wurst & Fisch |
| 5 | `TIEFKUEHL` | Tiefkühl |
| 6 | `VORRAT` | Nudeln, Reis & Konserven |
| 7 | `GEWUERZE_SOSSEN` | Gewürze, Öle & Soßen |
| 8 | `FRUEHSTUECK` | Frühstück & Aufstrich |
| 9 | `SUESSES_SNACKS` | Süßes & Snacks |
| 10 | `KAFFEE_TEE` | Kaffee & Tee |
| 11 | `GETRAENKE` | Getränke |
| 12 | `DROGERIE` | Drogerie & Hygiene |
| 13 | `HAUSHALT` | Haushalt & Reinigung |
| 14 | `SONSTIGES` | Sonstiges (fallback, always last) |

Labels stay German in the otherwise-English UI — items are German and these
are store-domain terms. Deliberate; note it in the phase-4 PR description.
Display labels live in the frontend only; the domain knows codes + walk order.

## Ordering model (no new sort machinery)

Display order = `(walkOrder(section), sortKey, id)`. The existing global
fractional `sortKey` is reused unchanged: within a section bucket, adjacent
items always satisfy `prev.key < next.key`, so `between()` works as-is for
within-section moves. New items get `append(globalLastKey)` as today and land
at the bottom of whatever section they classify into. A cross-section drag is
one `SECTION` change + one `SORT_KEY` change (computed from the target
bucket's neighbors). This preserves the ADR-0002 move invariant: **never
delete+reinsert**.

## Phase 0 — Prerequisite: fix BUG-8 (fractional index)

PLAN.md documents BUG-8: Java `FractionalIndex` and TS `fractionalIndex.ts`
use **incompatible algorithms**, and Java
`between("b0", "b0a")` returns `"b0am"` which sorts *after* `"b0a"` —
violating the strictly-between contract. This was harmless while no reorder
UI existed; phase 4 makes `between()` load-bearing on both sides.

- Unify the algorithm across `backend/src/main/java/com/shopmate/domain/crdt/FractionalIndex.java`
  and `frontend/src/features/shopping-list/utils/fractionalIndex.ts`.
- Add shared cross-language test vectors (same inputs/outputs asserted in
  JUnit and Vitest; e.g. a checked-in JSON vector file used by both suites).
- Property test the contract: `before < between(before, after) < after`.

**Verify:** both test suites green against the shared vectors.

## Phase 1 — Domain taxonomy, dictionary, classifier (backend-only, no API change)

New files:

- `backend/src/main/java/com/shopmate/domain/section/Section.java` — enum of
  the 14 codes with an int `walkOrder` and `fromCode(String)` returning
  `SONSTIGES` for unknown/null. No display labels.
- `backend/src/main/java/com/shopmate/domain/section/SectionClassifier.java` —
  pure domain service. API: `static String normalize(String name)` (lowercase,
  trim, ß→ss, collapse whitespace, strip leading quantity/unit tokens like
  "500g", "2x") and `Section classify(String name)`. Pipeline: exact →
  plural-stem retries (strip `-n/-en/-e/-er/-s`) → longest-suffix compound
  match (min suffix length 4; iterate suffixes of the normalized input against
  the dictionary — "Vollkornbrot"→"brot", "Kirschtomaten"→"tomaten") →
  last-word retry for multiword names ("Milch 3,5%") → `SONSTIGES`.
- `backend/src/main/java/com/shopmate/domain/section/SectionDictionary.java` —
  loads `sections/dictionary.tsv` via
  `SectionDictionary.class.getResourceAsStream(...)` using plain
  `java.io`/`java.nio.charset` only (ArchUnit: domain must stay
  framework-free; same Gradle module, so `src/main/resources` is on the
  domain classpath). Fail fast on malformed lines or unknown codes.
- `backend/src/main/resources/sections/dictionary.tsv` — format
  `term<TAB>CODE`, `#` comments. Target ~1,000–1,200 entries: ~600–800 base
  nouns + plurals/synonyms/regional variants (Brötchen/Semmel/Schrippe),
  including high-value compound heads: brot, tomaten, kaese, wurst, saft,
  mehl, joghurt, schokolade, … Store keys pre-normalized (loader normalizes
  again as a safety net).

Tests: `SectionTest`, `SectionClassifierTest` with a representative
~100-item German basket asserting section outcomes (compounds, plurals,
umlauts, multiword, unknown→SONSTIGES), plus a dictionary-integrity test
(parses, valid codes, no duplicate keys post-normalization).
**Pick the basket items *before* writing the dictionary** — it is the quality
gate; don't teach to the test.

**Verify:** `./gradlew test` green, ArchUnit green, domain coverage 100%.

## Phase 2 — SECTION LWW field end-to-end

Contract (`api/openapi.yaml`):
- Add `SECTION` to the `ItemField` enum (~line 512).
- Add required `section` (`LwwFieldString`) to the `ShoppingItem` schema
  (~line 446).
- `AddItemRequest` unchanged — the server classifies; clients never send a
  section at creation.
- Regenerate: `./gradlew openApiGenerate` and `npm run generate-api`.

Backend (each mirrors the existing sort_key handling):
- `domain/model/ItemField.java`: add `SECTION`.
- `domain/model/ShoppingItem.java`: add `LwwField<String> section` record
  component; extend `merge()` and add `case SECTION` to the exhaustive
  `applyChange` switch.
- `domain/model/ShoppingList.java`: `newItemFromChange` seeds
  `section = new LwwField<>("SONSTIGES", 0L, by)`.
- `application/service/ShoppingListService.java`: inject `SectionClassifier`;
  `addItem` classifies the name, seeds the sixth field at the same `ts`, and
  publishes a sixth `ItemChange(SECTION)` (see the five existing publishes at
  lines 113–122). `applyItemChange`: validate `SECTION` values strictly —
  reject codes where `Section.fromCode` falls back, mirroring the NAME
  validation block (lines 132–136), with `InvalidItemException`.
- `adapter/in/web/ShoppingListController.java`: `mapField` `case SECTION`,
  `toItemDto` sixth field.
- `adapter/in/sse/SseEventPublisher.java`: no change (field-agnostic).
- `adapter/out/persistence/entity/ShoppingItemEntity.java`: `sectionValue` /
  `sectionTs` / `sectionModifiedBy` (copy the sort_key block).
- `adapter/out/persistence/ShoppingListRepositoryAdapter.java`: extend
  `mergeLwwFields`, `toDomain`, `toEntity`.
- New `backend/src/main/resources/db/migration/V2__item_section.sql`:

  ```sql
  ALTER TABLE shopping_items
    ADD COLUMN section_value VARCHAR(40) NOT NULL DEFAULT 'SONSTIGES',
    ADD COLUMN section_ts BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN section_modified_by UUID;
  UPDATE shopping_items SET section_modified_by = name_modified_by;
  ALTER TABLE shopping_items
    ALTER COLUMN section_modified_by SET NOT NULL,
    ADD CONSTRAINT fk_items_section_modified_by
      FOREIGN KEY (section_modified_by) REFERENCES users(id);
  ```

Prod backfill (existing items): new
`infrastructure/config/SectionBackfillRunner.java`, an idempotent
`ApplicationRunner` that selects items with `section_ts = 0`, classifies
`name_value`, and writes the section with `section_ts = 1` — any real user
change (always stamped `currentTimeMillis`) wins LWW forever. Infrastructure
may depend on adapters (ArchUnit only constrains domain/application); add a
finder to the Spring Data item repository. Keeps the migration pure DDL.

Frontend (merge layer only, no UI):
- `utils/lwwMerge.ts`: `ShoppingItem.section`, `'SECTION'` in the `ItemField`
  union, sixth field in `mergeItem`, `case 'SECTION'` in `applyChange`,
  default seed `{value: 'SONSTIGES', timestamp: 0, ...}`.
- `hooks/useShoppingList.ts`: `toClientItem` mapping gains `section`.
- Fix type errors in test fixtures (`__tests__/`, `src/test/preview-main.tsx`).

Tests: extend `ShoppingItemMergeTest`, `ShoppingListApplyChangeTest`,
`ShoppingListServiceTest` (addItem publishes **six** changes;
"Kirschtomaten" → `OBST_GEMUESE`; invalid SECTION PATCH → 400),
`ShoppingListControllerTest`, `ShoppingListRepositoryAdapterIT` (round-trip +
merge-at-save), new `SectionBackfillRunnerTest`.

**Verify:** `docker compose up --build`; POST "Kirschtomaten" → response has
`section.value == "OBST_GEMUESE"`; SSE stream shows the SECTION event; V2
applies cleanly against a copy of prod data.

## Phase 3 — Learned corrections

- New port `domain/port/out/SectionCorrectionRepository.java`:
  `Optional<Section> find(UUID listId, String normalizedName)` and
  `void upsert(UUID listId, String normalizedName, Section section, long ts, UUID modifiedBy)`.
- `ShoppingListService`: `addItem` resolution becomes learned → classifier →
  fallback. `applyItemChange` with `field == SECTION` additionally upserts a
  correction keyed by `SectionClassifier.normalize(item.name().value())` at
  the change's `ts`. Auto-classification never writes corrections.
- New `SectionCorrectionEntity` (composite id `(listId, normalizedName)`),
  Spring Data repo, and adapter — upsert is LWW: only overwrite when the
  incoming `ts` is higher (mirrors `mergeLwwFields`).
- New `V3__section_corrections.sql`:

  ```sql
  CREATE TABLE section_corrections (
      list_id         UUID NOT NULL REFERENCES shopping_lists(id) ON DELETE CASCADE,
      normalized_name VARCHAR(100) NOT NULL,
      section         VARCHAR(40) NOT NULL,
      ts              BIGINT NOT NULL,
      modified_by     UUID NOT NULL REFERENCES users(id),
      PRIMARY KEY (list_id, normalized_name)
  );
  ```

Tests: `ShoppingListServiceTest` (correct "Hefe" → `BROT_BACKWAREN`, then
`addItem("Hefe")` lands there; LWW upsert ordering), adapter IT alongside
`ShoppingListRepositoryAdapterIT`.

**Verify:** PATCH an item's section, delete it, re-add the same name → it
arrives in the corrected section.

## Phase 4 — Frontend: grouped rendering, section sheet, drag-to-reorder

Dependency: add `@dnd-kit/core` + `@dnd-kit/sortable`. This is the app's
first drag affordance; dnd-kit ships keyboard + touch sensors and
accessibility announcements (PRODUCT.md demands keyboard navigability and
generous touch targets) — hand-rolling those is the single riskiest part of
the feature.

- New `utils/sections.ts`: `SECTIONS` (code, German label, walkOrder),
  `sectionOf(item)` (unknown → SONSTIGES), `groupBySection(items)` returning
  non-empty buckets in walk order, and pure
  `computeMove(buckets, itemId, targetSection, targetIndex) → {newSortKey, sectionChanged}`
  using `between()` on target-bucket neighbors. **All drag math lives here**,
  fully unit-testable.
- `hooks/useShoppingList.ts`: `setSection(itemId, code)` — optimistic
  value-only update (copy the `checkItem` pattern, lines 176–196; never
  fabricate client timestamps) then PATCH `SECTION`. Replace the dead-wired
  `moveItem` with `moveItemTo(itemId, targetSection, targetIndex)`: optimistic
  `SORT_KEY` (+ `SECTION` when crossing groups), then 1–2 PATCHes through the
  existing `reconcileServerItem` flow.
- `components/ItemList.tsx`: unchecked panel becomes one pad per non-empty
  section (`bg-panel rounded-2xl border border-line divide-y divide-line`)
  preceded by the existing heading idiom (`text-label font-semibold
  text-ink-soft`, as used for "In the cart"). Checked panel stays flat and
  last — the shopper doesn't walk checked items. Wrap in `DndContext` +
  per-section `SortableContext`; sections themselves droppable so drops onto
  empty section space work.
- `components/ItemRow.tsx`: dedicated drag handle (name tap = edit, checkbox
  tap = check → a handle avoids gesture conflicts), ≥44px target,
  `touch-action: none`, wired to `useSortable`; sensors Pointer + Touch
  (250 ms delay) + Keyboard. Plus a small tappable section affordance opening
  the sheet.
- New `components/SectionSheet.tsx`: bottom sheet listing all 14 sections,
  current highlighted — the accessible non-drag reassignment path. Reuse the
  `sheet-backdrop`/`sheet-panel` idiom from `ShoppingListPage.tsx` (~lines
  148–157).
- Update `src/test/preview-main.tsx` so `preview.html?screen=list` shows
  grouped sections.

Tests: full coverage on `sections.ts` (grouping, walk order, `computeMove`
incl. cross-section + empty-bucket edges); `ItemList` (headings in walk
order, empty sections hidden), `SectionSheet`, `useShoppingList`
(setSection/moveItemTo PATCH payloads). Keep DnD wiring thin — dnd-kit in
jsdom is the 90%-coverage hot spot; the pure logic carries the gate.
Exercise the keyboard sensor path in ItemRow tests.

**Verify:** `npm run test:coverage` green; preview screens render grouped;
manual drag within + across sections in the browser.

## Phase 5 — Hardening, docs, deploy

- Both gates: `./gradlew check` (90% + ArchUnit + domain 100%) and
  `npm run test:coverage` (90%).
- Two-browser convergence test (see memory: local E2E setup with seeded
  alice/bob): concurrent cross-section drag vs. rename; correct-then-readd;
  offline check-off during regroup.
- Flip ADR-0012 to Accepted; update the CLAUDE.md CRDT "Fields" line to
  include `section`.
- Deploy backend + contract first (phases 0–3 ship dark), then phase 4. The
  backfill runner self-executes on first prod boot. Old frontend bundles
  ignore unknown SECTION SSE events harmlessly; old backend + new frontend
  would 400 on SECTION PATCHes — hence backend first.
