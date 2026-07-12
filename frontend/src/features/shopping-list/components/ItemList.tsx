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
      <div className="text-center py-16 text-gray-400">
        <p className="text-sm">No items yet. Add one below!</p>
      </div>
    )
  }

  return (
    <ul className="space-y-2">
      {items.map((item) => (
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
  )
}
