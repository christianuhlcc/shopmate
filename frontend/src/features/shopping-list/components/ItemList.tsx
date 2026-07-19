import type { ItemField, ShoppingItem } from '../utils/lwwMerge'
import { ItemRow } from './ItemRow'

interface ItemListProps {
  items: ShoppingItem[]
  checkItem: (itemId: string, checked: boolean) => void
  updateItem: (itemId: string, field: ItemField, value: string) => void
  deleteItem: (itemId: string) => void
  moveItem: (itemId: string, afterItemId: string | null) => void
}

export function ItemList({
  items,
  checkItem,
  updateItem,
  deleteItem,
  moveItem,
}: ItemListProps) {
  if (items.length === 0) {
    return (
      <div className="text-center py-16 px-6">
        <svg
          className="mx-auto w-16 h-16"
          viewBox="0 0 64 64"
          fill="none"
          aria-hidden="true"
        >
          <rect x="12" y="8" width="40" height="48" rx="8" fill="oklch(0.93 0.07 85)" />
          <path
            d="M22 25l4 4 7-8.5"
            stroke="oklch(0.68 0.145 68)"
            strokeWidth="3"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
          <path
            d="M22 42h20"
            stroke="oklch(0.68 0.145 68)"
            strokeWidth="3"
            strokeLinecap="round"
          />
        </svg>
        <p className="text-item font-semibold text-ink mt-5">No items yet</p>
        <p className="text-body text-ink-soft mt-1">
          Add the first thing you need below — milk, maybe?
        </p>
      </div>
    )
  }

  const unchecked = items.filter((i) => !i.checked.value)
  const checked = items.filter((i) => i.checked.value)

  return (
    <div className="space-y-4">
      {unchecked.length > 0 && (
        <ul className="bg-panel rounded-2xl border border-line divide-y divide-line overflow-hidden">
          {unchecked.map((item) => (
            <ItemRow
              key={item.id}
              item={item}
              checkItem={checkItem}
              updateItem={updateItem}
              deleteItem={deleteItem}
              moveItem={moveItem}
            />
          ))}
        </ul>
      )}

      {checked.length > 0 && (
        <section aria-label="Checked off">
          <h2 className="text-label font-semibold text-ink-soft px-1 mb-2">
            In the cart · {checked.length}
          </h2>
          <ul className="bg-panel/60 rounded-2xl border border-line divide-y divide-line overflow-hidden">
            {checked.map((item) => (
              <ItemRow
                key={item.id}
                item={item}
                checkItem={checkItem}
                updateItem={updateItem}
                deleteItem={deleteItem}
                moveItem={moveItem}
              />
            ))}
          </ul>
        </section>
      )}
    </div>
  )
}
