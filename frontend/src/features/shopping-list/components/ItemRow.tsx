import { useRef, useState } from 'react'
import type { ItemField, ShoppingItem } from '../utils/lwwMerge'

interface ItemRowProps {
  item: ShoppingItem
  checkItem: (itemId: string, checked: boolean) => void
  updateItem: (itemId: string, field: ItemField, value: string) => void
  deleteItem: (itemId: string) => void
  moveItem: (itemId: string, afterItemId: string | null) => void
}

export function ItemRow({
  item,
  checkItem,
  updateItem,
  deleteItem,
}: ItemRowProps) {
  const [editing, setEditing] = useState(false)
  const [editValue, setEditValue] = useState(item.name.value)
  const inputRef = useRef<HTMLInputElement>(null)

  function handleNameClick() {
    setEditValue(item.name.value)
    setEditing(true)
    // Focus on next tick after render
    setTimeout(() => inputRef.current?.focus(), 0)
  }

  function commitEdit() {
    setEditing(false)
    const trimmed = editValue.trim()
    if (trimmed && trimmed !== item.name.value) {
      updateItem(item.id, 'NAME', trimmed)
    }
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Enter') {
      commitEdit()
    } else if (e.key === 'Escape') {
      setEditing(false)
      setEditValue(item.name.value)
    }
  }

  return (
    <li className="bg-white rounded-xl border border-surface-border px-4 py-3 flex items-center gap-3">
      {/* Checkbox */}
      <button
        type="button"
        onClick={() => checkItem(item.id, !item.checked.value)}
        aria-label={item.checked.value ? 'Uncheck item' : 'Check item'}
        className={`flex-shrink-0 h-5 w-5 rounded border-2 flex items-center justify-center transition-colors ${
          item.checked.value
            ? 'bg-primary border-primary'
            : 'border-gray-300 hover:border-primary'
        }`}
      >
        {item.checked.value && (
          <svg
            className="w-3 h-3 text-white"
            viewBox="0 0 12 12"
            fill="none"
            aria-hidden="true"
          >
            <path
              d="M2 6l3 3 5-5"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        )}
      </button>

      {/* Name */}
      <div className="flex-1 min-w-0">
        {editing ? (
          <input
            ref={inputRef}
            type="text"
            value={editValue}
            onChange={(e) => setEditValue(e.target.value)}
            onBlur={commitEdit}
            onKeyDown={handleKeyDown}
            className="w-full text-sm text-gray-900 border-b border-primary outline-none bg-transparent"
            maxLength={100}
          />
        ) : (
          <button
            type="button"
            onClick={handleNameClick}
            className={`text-sm text-left w-full truncate ${
              item.checked.value ? 'line-through text-gray-400' : 'text-gray-900'
            }`}
          >
            {item.name.value || <span className="italic text-gray-400">unnamed</span>}
          </button>
        )}
      </div>

      {/* Quantity */}
      {item.quantity.value && item.quantity.value !== '1' && (
        <span className="flex-shrink-0 text-xs text-gray-400 font-medium px-2 py-0.5 bg-surface-muted rounded">
          {item.quantity.value}
        </span>
      )}

      {/* Delete */}
      <button
        type="button"
        onClick={() => deleteItem(item.id)}
        aria-label="Delete item"
        className="flex-shrink-0 text-gray-300 hover:text-red-500 transition-colors"
      >
        <svg
          className="w-4 h-4"
          viewBox="0 0 16 16"
          fill="none"
          aria-hidden="true"
        >
          <path
            d="M4 4l8 8M12 4l-8 8"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
          />
        </svg>
      </button>
    </li>
  )
}
