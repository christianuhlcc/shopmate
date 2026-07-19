export interface LwwField<T> {
  value: T
  timestamp: number
  modifiedBy: string
}

export interface ShoppingItem {
  id: string
  listId: string
  name: LwwField<string>
  quantity: LwwField<string>
  checked: LwwField<boolean>
  deleted: LwwField<boolean>
  sortKey: LwwField<string>
  section: LwwField<string>
}

export type ItemField = 'NAME' | 'QUANTITY' | 'CHECKED' | 'DELETED' | 'SORT_KEY' | 'SECTION'

export interface ItemChangeEvent {
  itemId: string
  listId: string
  field: ItemField
  value: string
  timestamp: number
  modifiedBy: string
}

/**
 * Last-Write-Wins merge: higher timestamp wins.
 * When timestamps are equal, the lexicographically larger modifiedBy UUID wins
 * (mirrors the Java tie-break: a.modifiedBy.compareTo(b.modifiedBy) > 0 keeps a).
 */
export function mergeLwwField<T>(a: LwwField<T>, b: LwwField<T>): LwwField<T> {
  if (a.timestamp > b.timestamp) return a
  if (b.timestamp > a.timestamp) return b
  // Tie-break: larger UUID string wins (same logic as Java compareTo > 0 keeps a)
  return a.modifiedBy >= b.modifiedBy ? a : b
}

/**
 * Merge two versions of the same item field-by-field (LWW per field).
 * Used to reconcile an authoritative server response into local state:
 * each field independently keeps whichever side has the higher timestamp.
 */
export function mergeItem(existing: ShoppingItem, incoming: ShoppingItem): ShoppingItem {
  return {
    id: existing.id,
    listId: existing.listId,
    name: mergeLwwField(existing.name, incoming.name),
    quantity: mergeLwwField(existing.quantity, incoming.quantity),
    checked: mergeLwwField(existing.checked, incoming.checked),
    deleted: mergeLwwField(existing.deleted, incoming.deleted),
    sortKey: mergeLwwField(existing.sortKey, incoming.sortKey),
    section: mergeLwwField(existing.section, incoming.section),
  }
}

export function parseValue(field: ItemField, value: string): string | boolean {
  if (field === 'CHECKED' || field === 'DELETED') {
    return value === 'true'
  }
  return value
}

function makeDefaultItem(itemId: string, listId: string): ShoppingItem {
  const emptyString: LwwField<string> = { value: '', timestamp: 0, modifiedBy: '' }
  const emptyBool: LwwField<boolean> = { value: false, timestamp: 0, modifiedBy: '' }
  return {
    id: itemId,
    listId,
    name: { ...emptyString },
    quantity: { ...emptyString },
    checked: { ...emptyBool },
    deleted: { ...emptyBool },
    sortKey: { ...emptyString },
    section: { value: 'SONSTIGES', timestamp: 0, modifiedBy: '' },
  }
}

export function applyChange(
  items: ShoppingItem[],
  change: ItemChangeEvent,
): ShoppingItem[] {
  const existing = items.find((i) => i.id === change.itemId)
  const item: ShoppingItem = existing
    ? { ...existing }
    : makeDefaultItem(change.itemId, change.listId)

  const incoming = {
    timestamp: change.timestamp,
    modifiedBy: change.modifiedBy,
  }

  switch (change.field) {
    case 'NAME':
      item.name = mergeLwwField(item.name, {
        value: change.value,
        ...incoming,
      })
      break
    case 'QUANTITY':
      item.quantity = mergeLwwField(item.quantity, {
        value: change.value,
        ...incoming,
      })
      break
    case 'CHECKED':
      item.checked = mergeLwwField(item.checked, {
        value: change.value === 'true',
        ...incoming,
      })
      break
    case 'DELETED':
      item.deleted = mergeLwwField(item.deleted, {
        value: change.value === 'true',
        ...incoming,
      })
      break
    case 'SORT_KEY':
      item.sortKey = mergeLwwField(item.sortKey, {
        value: change.value,
        ...incoming,
      })
      break
    case 'SECTION':
      item.section = mergeLwwField(item.section, {
        value: change.value,
        ...incoming,
      })
      break
  }

  if (existing) {
    return items.map((i) => (i.id === change.itemId ? item : i))
  }
  return [...items, item]
}

export function sortItems(items: ShoppingItem[]): ShoppingItem[] {
  return items
    .filter((i) => !i.deleted.value)
    .slice()
    .sort((a, b) => {
      if (a.sortKey.value < b.sortKey.value) return -1
      if (a.sortKey.value > b.sortKey.value) return 1
      // Tie-break on id so concurrent inserts with equal sort keys render in
      // the same order in every client (must match the backend's tie-break).
      if (a.id < b.id) return -1
      if (a.id > b.id) return 1
      return 0
    })
}
