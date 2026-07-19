/**
 * Supermarket section taxonomy + grouping/move math for the frontend.
 *
 * The 14-section taxonomy is fixed and app-defined (ADR-0012); codes and walk
 * order must match the backend's `Section` enum exactly. Display labels are
 * frontend-only (German, deliberately — see docs/plans/section-grouping.md).
 *
 * All drag math lives here so it's fully unit-testable without React or
 * dnd-kit: `groupBySection` turns a flat, already-sorted item list into
 * per-section buckets in walk order, and `computeMove` turns a drop target
 * (section + index within that section) into the single SORT_KEY value the
 * dragged item should take on, using the same `between()` fractional-index
 * arithmetic as within-section reordering already used.
 */
import { between } from './fractionalIndex'
import type { ShoppingItem } from './lwwMerge'

export interface Section {
  code: string
  label: string
  walkOrder: number
}

/** Canonical 14-section taxonomy, in default store-walk order. SONSTIGES is the fallback and always last. */
export const SECTIONS: Section[] = [
  { code: 'OBST_GEMUESE', label: 'Obst & Gemüse', walkOrder: 1 },
  { code: 'BROT_BACKWAREN', label: 'Brot & Backwaren', walkOrder: 2 },
  { code: 'MOLKEREI_EIER', label: 'Molkerei & Eier', walkOrder: 3 },
  { code: 'FLEISCH_FISCH', label: 'Fleisch, Wurst & Fisch', walkOrder: 4 },
  { code: 'TIEFKUEHL', label: 'Tiefkühl', walkOrder: 5 },
  { code: 'VORRAT', label: 'Nudeln, Reis & Konserven', walkOrder: 6 },
  { code: 'GEWUERZE_SOSSEN', label: 'Gewürze, Öle & Soßen', walkOrder: 7 },
  { code: 'FRUEHSTUECK', label: 'Frühstück & Aufstrich', walkOrder: 8 },
  { code: 'SUESSES_SNACKS', label: 'Süßes & Snacks', walkOrder: 9 },
  { code: 'KAFFEE_TEE', label: 'Kaffee & Tee', walkOrder: 10 },
  { code: 'GETRAENKE', label: 'Getränke', walkOrder: 11 },
  { code: 'DROGERIE', label: 'Drogerie & Hygiene', walkOrder: 12 },
  { code: 'HAUSHALT', label: 'Haushalt & Reinigung', walkOrder: 13 },
  { code: 'SONSTIGES', label: 'Sonstiges', walkOrder: 14 },
]

export const FALLBACK_SECTION_CODE = 'SONSTIGES'

const SECTION_BY_CODE = new Map(SECTIONS.map((s) => [s.code, s]))

/** Looks up display info for a section code; unknown codes fall back to Sonstiges. */
export function sectionInfo(code: string): Section {
  return SECTION_BY_CODE.get(code) ?? SECTION_BY_CODE.get(FALLBACK_SECTION_CODE)!
}

/** The section code an item renders under; unknown/unrecognized codes fall back to Sonstiges. */
export function sectionOf(item: ShoppingItem): string {
  return SECTION_BY_CODE.has(item.section.value) ? item.section.value : FALLBACK_SECTION_CODE
}

export interface SectionBucket {
  code: string
  label: string
  walkOrder: number
  items: ShoppingItem[]
}

/**
 * Groups items by section, returning only non-empty buckets in walk order.
 * `items` must already be in display order (see `sortItems` in lwwMerge.ts) —
 * this function preserves relative order within each bucket, it doesn't sort.
 */
export function groupBySection(items: ShoppingItem[]): SectionBucket[] {
  const byCode = new Map<string, ShoppingItem[]>()
  for (const item of items) {
    const code = sectionOf(item)
    const bucket = byCode.get(code)
    if (bucket) {
      bucket.push(item)
    } else {
      byCode.set(code, [item])
    }
  }
  return SECTIONS.filter((s) => byCode.has(s.code)).map((s) => ({
    code: s.code,
    label: s.label,
    walkOrder: s.walkOrder,
    items: byCode.get(s.code)!,
  }))
}

export interface MoveResult {
  /** New fractional sort key for the dragged item, computed from the target bucket's neighbors. */
  newSortKey: string
  /** Whether the item is crossing into a different section (needs a SECTION change alongside SORT_KEY). */
  sectionChanged: boolean
}

/**
 * Pure move computation: given the current buckets, the item being dragged,
 * and where it's being dropped (a target section + an index within that
 * section's item list, with the dragged item itself excluded from the
 * count), returns the new sort key and whether the section changed.
 *
 * `targetIndex` is clamped into range, so dropping past the last item (or
 * into an empty section) lands at the end. Uses `between()` on the target
 * bucket's neighbors — never delete+reinsert (ADR-0002 move invariant).
 */
export function computeMove(
  buckets: SectionBucket[],
  itemId: string,
  targetSection: string,
  targetIndex: number,
): MoveResult {
  let currentSection: string | undefined
  for (const bucket of buckets) {
    if (bucket.items.some((i) => i.id === itemId)) {
      currentSection = bucket.code
      break
    }
  }

  const targetBucket = buckets.find((b) => b.code === targetSection)
  const targetItems = (targetBucket?.items ?? []).filter((i) => i.id !== itemId)
  const clampedIndex = Math.max(0, Math.min(targetIndex, targetItems.length))

  const prevItem = clampedIndex > 0 ? targetItems[clampedIndex - 1] : null
  const nextItem = clampedIndex < targetItems.length ? targetItems[clampedIndex] : null

  const newSortKey = between(prevItem?.sortKey.value ?? null, nextItem?.sortKey.value ?? null)

  return {
    newSortKey,
    sectionChanged: currentSection !== targetSection,
  }
}
