import { describe, it, expect } from 'vitest'
import {
  mergeLwwField,
  mergeItem,
  applyChange,
  parseValue,
  sortItems,
  type LwwField,
  type ShoppingItem,
  type ItemChangeEvent,
} from '../utils/lwwMerge'

const USER_A = '00000000-0000-0000-0000-000000000001'
const USER_B = '00000000-0000-0000-0000-000000000002'
const LIST_ID = 'list-1'
const ITEM_ID = 'item-1'

function makeStringField(value: string, timestamp: number, modifiedBy: string): LwwField<string> {
  return { value, timestamp, modifiedBy }
}

function makeBoolField(value: boolean, timestamp: number, modifiedBy: string): LwwField<boolean> {
  return { value, timestamp, modifiedBy }
}

function makeItem(overrides: Partial<ShoppingItem> = {}): ShoppingItem {
  return {
    id: ITEM_ID,
    listId: LIST_ID,
    name: makeStringField('Milk', 100, USER_A),
    quantity: makeStringField('2', 100, USER_A),
    checked: makeBoolField(false, 100, USER_A),
    deleted: makeBoolField(false, 100, USER_A),
    sortKey: makeStringField('m', 100, USER_A),
    ...overrides,
  }
}

describe('mergeLwwField', () => {
  it('higher timestamp wins', () => {
    const a = makeStringField('old', 100, USER_A)
    const b = makeStringField('new', 200, USER_B)
    expect(mergeLwwField(a, b)).toEqual(b)
    expect(mergeLwwField(b, a)).toEqual(b)
  })

  it('when timestamps are equal, lexicographically larger modifiedBy UUID wins', () => {
    const a = makeStringField('from-a', 100, USER_A)
    const b = makeStringField('from-b', 100, USER_B)
    // USER_B > USER_A lexicographically
    expect(mergeLwwField(a, b)).toEqual(b)
    expect(mergeLwwField(b, a)).toEqual(b)
  })

  it('same timestamp and same modifiedBy returns a (or b — same value)', () => {
    const a = makeStringField('same', 100, USER_A)
    const b = makeStringField('same', 100, USER_A)
    const result = mergeLwwField(a, b)
    expect(result.value).toBe('same')
  })
})

describe('mergeItem', () => {
  it('merges each field independently — newer side wins per field', () => {
    const existing = makeItem({
      name: makeStringField('Local Name', 300, USER_A), // local is newer
      checked: makeBoolField(false, 100, USER_A), // incoming is newer
    })
    const incoming = makeItem({
      name: makeStringField('Server Name', 200, USER_B),
      checked: makeBoolField(true, 250, USER_B),
      quantity: makeStringField('5', 400, USER_B),
    })

    const merged = mergeItem(existing, incoming)

    expect(merged.name.value).toBe('Local Name') // ts 300 > 200
    expect(merged.checked.value).toBe(true) // ts 250 > 100
    expect(merged.quantity.value).toBe('5') // ts 400 > 100
    expect(merged.deleted).toEqual(incoming.deleted) // equal ts + user → stable
    expect(merged.id).toBe(ITEM_ID)
    expect(merged.listId).toBe(LIST_ID)
  })

  it('an authoritative server item with higher timestamps wins every field', () => {
    const existing = makeItem()
    const incoming = makeItem({
      name: makeStringField('Server', 500, USER_B),
      quantity: makeStringField('9', 500, USER_B),
      checked: makeBoolField(true, 500, USER_B),
      deleted: makeBoolField(true, 500, USER_B),
      sortKey: makeStringField('z', 500, USER_B),
    })
    const merged = mergeItem(existing, incoming)
    expect(merged.name).toEqual(incoming.name)
    expect(merged.quantity).toEqual(incoming.quantity)
    expect(merged.checked).toEqual(incoming.checked)
    expect(merged.deleted).toEqual(incoming.deleted)
    expect(merged.sortKey).toEqual(incoming.sortKey)
  })

  it('does not let a stale incoming item overwrite newer local fields', () => {
    const existing = makeItem({
      name: makeStringField('Fresh', 900, USER_A),
      sortKey: makeStringField('b', 900, USER_A),
    })
    const stale = makeItem({
      name: makeStringField('Stale', 50, USER_B),
      sortKey: makeStringField('q', 50, USER_B),
    })
    const merged = mergeItem(existing, stale)
    expect(merged.name.value).toBe('Fresh')
    expect(merged.sortKey.value).toBe('b')
  })
})

describe('applyChange', () => {
  it('creates a new item when not found', () => {
    const change: ItemChangeEvent = {
      itemId: 'new-item',
      listId: LIST_ID,
      field: 'NAME',
      value: 'Eggs',
      timestamp: 200,
      modifiedBy: USER_A,
    }
    const result = applyChange([], change)
    expect(result).toHaveLength(1)
    expect(result[0].id).toBe('new-item')
    expect(result[0].name.value).toBe('Eggs')
  })

  it('merges NAME change into existing item — higher timestamp wins', () => {
    const item = makeItem()
    const change: ItemChangeEvent = {
      itemId: ITEM_ID,
      listId: LIST_ID,
      field: 'NAME',
      value: 'Oat Milk',
      timestamp: 200,
      modifiedBy: USER_B,
    }
    const result = applyChange([item], change)
    expect(result[0].name.value).toBe('Oat Milk')
  })

  it('does not overwrite with stale NAME change', () => {
    const item = makeItem({ name: makeStringField('Fresh Name', 300, USER_A) })
    const stale: ItemChangeEvent = {
      itemId: ITEM_ID,
      listId: LIST_ID,
      field: 'NAME',
      value: 'Old Name',
      timestamp: 200,
      modifiedBy: USER_B,
    }
    const result = applyChange([item], stale)
    expect(result[0].name.value).toBe('Fresh Name')
  })

  it('applies QUANTITY change', () => {
    const item = makeItem()
    const change: ItemChangeEvent = {
      itemId: ITEM_ID,
      listId: LIST_ID,
      field: 'QUANTITY',
      value: '6',
      timestamp: 200,
      modifiedBy: USER_A,
    }
    const result = applyChange([item], change)
    expect(result[0].quantity.value).toBe('6')
  })

  it('applies CHECKED change', () => {
    const item = makeItem()
    const change: ItemChangeEvent = {
      itemId: ITEM_ID,
      listId: LIST_ID,
      field: 'CHECKED',
      value: 'true',
      timestamp: 200,
      modifiedBy: USER_A,
    }
    const result = applyChange([item], change)
    expect(result[0].checked.value).toBe(true)
  })

  it('applies DELETED change', () => {
    const item = makeItem()
    const change: ItemChangeEvent = {
      itemId: ITEM_ID,
      listId: LIST_ID,
      field: 'DELETED',
      value: 'true',
      timestamp: 200,
      modifiedBy: USER_A,
    }
    const result = applyChange([item], change)
    expect(result[0].deleted.value).toBe(true)
  })

  it('applies SORT_KEY change', () => {
    const item = makeItem()
    const change: ItemChangeEvent = {
      itemId: ITEM_ID,
      listId: LIST_ID,
      field: 'SORT_KEY',
      value: 'p',
      timestamp: 200,
      modifiedBy: USER_A,
    }
    const result = applyChange([item], change)
    expect(result[0].sortKey.value).toBe('p')
  })

  it('concurrent SORT_KEY moves converge to one position (higher timestamp wins)', () => {
    const item = makeItem()

    const moveA: ItemChangeEvent = {
      itemId: ITEM_ID,
      listId: LIST_ID,
      field: 'SORT_KEY',
      value: 'c',
      timestamp: 300,
      modifiedBy: USER_A,
    }
    const moveB: ItemChangeEvent = {
      itemId: ITEM_ID,
      listId: LIST_ID,
      field: 'SORT_KEY',
      value: 'x',
      timestamp: 200,
      modifiedBy: USER_B,
    }

    // Apply A then B
    const r1 = applyChange(applyChange([item], moveA), moveB)
    // Apply B then A
    const r2 = applyChange(applyChange([item], moveB), moveA)

    expect(r1[0].sortKey.value).toBe('c')
    expect(r2[0].sortKey.value).toBe('c')
  })
})

describe('parseValue', () => {
  it('parses boolean fields', () => {
    expect(parseValue('CHECKED', 'true')).toBe(true)
    expect(parseValue('CHECKED', 'false')).toBe(false)
    expect(parseValue('DELETED', 'true')).toBe(true)
  })

  it('passes string fields through', () => {
    expect(parseValue('NAME', 'Milk')).toBe('Milk')
    expect(parseValue('SORT_KEY', 'b0')).toBe('b0')
  })
})

describe('sortItems', () => {
  it('filters out deleted items', () => {
    const items: ShoppingItem[] = [
      makeItem({ id: 'a', deleted: makeBoolField(false, 100, USER_A), sortKey: makeStringField('a', 100, USER_A) }),
      makeItem({ id: 'b', deleted: makeBoolField(true, 100, USER_A), sortKey: makeStringField('b', 100, USER_A) }),
      makeItem({ id: 'c', deleted: makeBoolField(false, 100, USER_A), sortKey: makeStringField('c', 100, USER_A) }),
    ]
    const sorted = sortItems(items)
    expect(sorted).toHaveLength(2)
    expect(sorted.map((i) => i.id)).toEqual(['a', 'c'])
  })

  it('sorts by sortKey ascending', () => {
    const items: ShoppingItem[] = [
      makeItem({ id: '3', sortKey: makeStringField('z', 100, USER_A) }),
      makeItem({ id: '1', sortKey: makeStringField('a', 100, USER_A) }),
      makeItem({ id: '2', sortKey: makeStringField('m', 100, USER_A) }),
    ]
    const sorted = sortItems(items)
    expect(sorted.map((i) => i.id)).toEqual(['1', '2', '3'])
  })

  it('keeps relative order stable for equal sort keys', () => {
    const items: ShoppingItem[] = [
      makeItem({ id: 'x', sortKey: makeStringField('m', 100, USER_A) }),
      makeItem({ id: 'y', sortKey: makeStringField('m', 100, USER_A) }),
    ]
    const sorted = sortItems(items)
    expect(sorted.map((i) => i.id)).toEqual(['x', 'y'])
  })

  it('does not mutate the input array', () => {
    const items: ShoppingItem[] = [
      makeItem({ id: 'b', sortKey: makeStringField('b', 100, USER_A) }),
      makeItem({ id: 'a', sortKey: makeStringField('a', 100, USER_A) }),
    ]
    const original = [...items]
    sortItems(items)
    expect(items[0].id).toBe(original[0].id)
  })
})
