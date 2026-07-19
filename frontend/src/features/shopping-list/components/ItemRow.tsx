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
  const isChecked = item.checked.value

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
    <li className="flex items-center gap-1 pl-2 pr-1 py-1 min-h-[3.25rem]">
      {/* Checkbox — 44px hit area around a 26px circle */}
      <button
        type="button"
        onClick={() => checkItem(item.id, !isChecked)}
        aria-label={isChecked ? 'Uncheck item' : 'Check item'}
        className="pressable flex-shrink-0 min-h-touch min-w-touch flex items-center justify-center rounded-full group"
      >
        <span
          className={`h-[1.625rem] w-[1.625rem] rounded-full border-2 flex items-center justify-center transition-colors duration-150 ${
            isChecked
              ? 'bg-marigold border-marigold'
              : 'border-ink-mute/90 group-hover:border-marigold-deep'
          }`}
        >
          {isChecked && (
            <svg className="w-3.5 h-3.5" viewBox="0 0 14 14" fill="none" aria-hidden="true">
              <path
                className="check-draw"
                d="M3 7.5l2.8 2.8L11 4.5"
                stroke="oklch(0.27 0.025 65)"
                strokeWidth="2.25"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
          )}
        </span>
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
            className="w-full text-item text-ink border-b-2 border-marigold-deep outline-none bg-transparent py-1"
            maxLength={100}
          />
        ) : (
          <button
            type="button"
            onClick={handleNameClick}
            className="text-item text-left w-full min-h-touch py-1"
          >
            <span
              className={`item-label block truncate ${
                isChecked
                  ? 'line-through decoration-ink-mute/70 text-ink-mute'
                  : 'text-ink'
              }`}
            >
              {item.name.value || <span className="italic text-ink-mute">unnamed</span>}
            </span>
          </button>
        )}
      </div>

      {/* Quantity */}
      {item.quantity.value && item.quantity.value !== '1' && (
        <span
          className={`flex-shrink-0 text-label font-semibold px-2.5 py-0.5 rounded-full ${
            isChecked
              ? 'text-ink-mute bg-ground'
              : 'text-honey-deep bg-marigold-faint'
          }`}
        >
          {item.quantity.value}
        </span>
      )}

      {/* Delete — 44px hit area */}
      <button
        type="button"
        onClick={() => deleteItem(item.id)}
        aria-label="Delete item"
        className="pressable flex-shrink-0 min-h-touch min-w-touch flex items-center justify-center rounded-full text-ink-mute hover:text-danger hover:bg-danger-tint focus-visible:outline-danger"
      >
        <svg className="w-4 h-4" viewBox="0 0 16 16" fill="none" aria-hidden="true">
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
