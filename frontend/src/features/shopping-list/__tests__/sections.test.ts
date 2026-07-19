import { describe, it, expect } from 'vitest'
import {
  SECTIONS,
  FALLBACK_SECTION_CODE,
  sectionInfo,
  sectionOf,
  groupBySection,
  computeMove,
} from '../utils/sections'
import type { ShoppingItem } from '../utils/lwwMerge'

const USER_ID = '00000000-0000-0000-0000-000000000001'

function makeItem(id: string, sortKey: string, section: string): ShoppingItem {
  return {
    id,
    listId: 'list-1',
    name: { value: id, timestamp: 100, modifiedBy: USER_ID },
    quantity: { value: '1', timestamp: 100, modifiedBy: USER_ID },
    checked: { value: false, timestamp: 100, modifiedBy: USER_ID },
    deleted: { value: false, timestamp: 100, modifiedBy: USER_ID },
    sortKey: { value: sortKey, timestamp: 100, modifiedBy: USER_ID },
    section: { value: section, timestamp: 100, modifiedBy: USER_ID },
  }
}

describe('SECTIONS taxonomy', () => {
  it('has exactly 14 sections', () => {
    expect(SECTIONS).toHaveLength(14)
  })

  it('is in walk-order 1..14', () => {
    expect(SECTIONS.map((s) => s.walkOrder)).toEqual([...Array(14)].map((_, i) => i + 1))
  })

  it('has unique codes', () => {
    const codes = new Set(SECTIONS.map((s) => s.code))
    expect(codes.size).toBe(14)
  })

  it('has SONSTIGES last with the highest walk order', () => {
    const last = SECTIONS[SECTIONS.length - 1]
    expect(last.code).toBe('SONSTIGES')
    expect(last.walkOrder).toBe(14)
    expect(FALLBACK_SECTION_CODE).toBe('SONSTIGES')
  })
})

describe('sectionInfo', () => {
  it('returns the matching section for a known code', () => {
    expect(sectionInfo('OBST_GEMUESE').label).toBe('Obst & Gemüse')
  })

  it('falls back to Sonstiges for an unknown code', () => {
    expect(sectionInfo('NOT_A_REAL_CODE').code).toBe('SONSTIGES')
  })
})

describe('sectionOf', () => {
  it('returns the item section value when it is a known code', () => {
    const item = makeItem('i1', 'a0', 'TIEFKUEHL')
    expect(sectionOf(item)).toBe('TIEFKUEHL')
  })

  it('falls back to SONSTIGES for an unrecognized section code', () => {
    const item = makeItem('i1', 'a0', 'GARBAGE')
    expect(sectionOf(item)).toBe('SONSTIGES')
  })
})

describe('groupBySection', () => {
  it('returns empty array for no items', () => {
    expect(groupBySection([])).toEqual([])
  })

  it('groups items into buckets in walk order, not input order', () => {
    const items = [
      makeItem('a', 'a0', 'SONSTIGES'),
      makeItem('b', 'b0', 'OBST_GEMUESE'),
      makeItem('c', 'c0', 'MOLKEREI_EIER'),
    ]
    const buckets = groupBySection(items)
    expect(buckets.map((b) => b.code)).toEqual(['OBST_GEMUESE', 'MOLKEREI_EIER', 'SONSTIGES'])
  })

  it('hides empty sections (only non-empty buckets appear)', () => {
    const items = [makeItem('a', 'a0', 'TIEFKUEHL')]
    const buckets = groupBySection(items)
    expect(buckets).toHaveLength(1)
    expect(buckets[0].code).toBe('TIEFKUEHL')
  })

  it('preserves relative order of items within a bucket', () => {
    const items = [
      makeItem('a', 'a0', 'OBST_GEMUESE'),
      makeItem('b', 'b0', 'OBST_GEMUESE'),
      makeItem('c', 'c0', 'OBST_GEMUESE'),
    ]
    const buckets = groupBySection(items)
    expect(buckets[0].items.map((i) => i.id)).toEqual(['a', 'b', 'c'])
  })

  it('buckets unknown section codes into SONSTIGES', () => {
    const items = [makeItem('a', 'a0', 'MADE_UP')]
    const buckets = groupBySection(items)
    expect(buckets).toHaveLength(1)
    expect(buckets[0].code).toBe('SONSTIGES')
  })

  it('carries label and walkOrder through onto the bucket', () => {
    const items = [makeItem('a', 'a0', 'GETRAENKE')]
    const buckets = groupBySection(items)
    expect(buckets[0].label).toBe('Getränke')
    expect(buckets[0].walkOrder).toBe(11)
  })
})

describe('computeMove', () => {
  it('moves within a section, between two neighbors', () => {
    const items = [
      makeItem('a', 'a0', 'OBST_GEMUESE'),
      makeItem('b', 'e0', 'OBST_GEMUESE'),
      makeItem('c', 'f0', 'OBST_GEMUESE'),
    ]
    const buckets = groupBySection(items)
    // Move 'c' to index 1 (between a and b)
    const result = computeMove(buckets, 'c', 'OBST_GEMUESE', 1)
    expect(result.sectionChanged).toBe(false)
    expect(result.newSortKey > 'a0').toBe(true)
    expect(result.newSortKey < 'e0').toBe(true)
  })

  it('moves an item to the first position within its section', () => {
    const items = [
      makeItem('a', 'b0', 'OBST_GEMUESE'),
      makeItem('b', 'c0', 'OBST_GEMUESE'),
    ]
    const buckets = groupBySection(items)
    const result = computeMove(buckets, 'b', 'OBST_GEMUESE', 0)
    expect(result.sectionChanged).toBe(false)
    expect(result.newSortKey < 'b0').toBe(true)
  })

  it('moves an item to the last position within its section', () => {
    const items = [
      makeItem('a', 'b0', 'OBST_GEMUESE'),
      makeItem('b', 'c0', 'OBST_GEMUESE'),
    ]
    const buckets = groupBySection(items)
    const result = computeMove(buckets, 'a', 'OBST_GEMUESE', 1)
    expect(result.sectionChanged).toBe(false)
    expect(result.newSortKey > 'c0').toBe(true)
  })

  it('crosses into a different, non-empty section', () => {
    const items = [
      makeItem('a', 'a0', 'OBST_GEMUESE'),
      makeItem('b', 'b0', 'MOLKEREI_EIER'),
      makeItem('c', 'c0', 'MOLKEREI_EIER'),
    ]
    const buckets = groupBySection(items)
    const result = computeMove(buckets, 'a', 'MOLKEREI_EIER', 1)
    expect(result.sectionChanged).toBe(true)
    expect(result.newSortKey > 'b0').toBe(true)
    expect(result.newSortKey < 'c0').toBe(true)
  })

  it('crosses into an empty section (not currently rendered as a bucket)', () => {
    const items = [makeItem('a', 'a0', 'OBST_GEMUESE')]
    const buckets = groupBySection(items)
    // TIEFKUEHL has no bucket at all yet
    const result = computeMove(buckets, 'a', 'TIEFKUEHL', 0)
    expect(result.sectionChanged).toBe(true)
    expect(typeof result.newSortKey).toBe('string')
    expect(result.newSortKey.length).toBeGreaterThan(0)
  })

  it('dropping in the same section at the same relative position keeps sectionChanged false', () => {
    const items = [
      makeItem('a', 'a0', 'OBST_GEMUESE'),
      makeItem('b', 'b0', 'OBST_GEMUESE'),
    ]
    const buckets = groupBySection(items)
    const result = computeMove(buckets, 'a', 'OBST_GEMUESE', 0)
    expect(result.sectionChanged).toBe(false)
  })

  it('clamps an out-of-range target index to the end of the bucket', () => {
    const items = [
      makeItem('a', 'a0', 'OBST_GEMUESE'),
      makeItem('b', 'b0', 'OBST_GEMUESE'),
    ]
    const buckets = groupBySection(items)
    const result = computeMove(buckets, 'a', 'OBST_GEMUESE', 999)
    expect(result.newSortKey > 'b0').toBe(true)
  })

  it('clamps a negative target index to the start of the bucket', () => {
    const items = [
      makeItem('a', 'a0', 'OBST_GEMUESE'),
      makeItem('b', 'b0', 'OBST_GEMUESE'),
    ]
    const buckets = groupBySection(items)
    const result = computeMove(buckets, 'b', 'OBST_GEMUESE', -5)
    expect(result.newSortKey < 'a0').toBe(true)
  })

  it('treats an item not found in any bucket as a section change', () => {
    const items = [makeItem('a', 'a0', 'OBST_GEMUESE')]
    const buckets = groupBySection(items)
    const result = computeMove(buckets, 'ghost', 'OBST_GEMUESE', 0)
    expect(result.sectionChanged).toBe(true)
  })
})
