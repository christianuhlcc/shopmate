import { useRef, useState } from 'react'
import { useSortable } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import type { ItemField, ShoppingItem } from '../utils/lwwMerge'
import { sectionInfo } from '../utils/sections'
import { SectionSheet } from './SectionSheet'

interface ItemRowProps {
  item: ShoppingItem
  checkItem: (itemId: string, checked: boolean) => void
  updateItem: (itemId: string, field: ItemField, value: string) => void
  deleteItem: (itemId: string) => void
  setSection: (itemId: string, code: string) => void
  /** Checked-off rows aren't part of any section's SortableContext — no handle, no drag. */
  draggable?: boolean
}

export function ItemRow({
  item,
  checkItem,
  updateItem,
  deleteItem,
  setSection,
  draggable = true,
}: ItemRowProps) {
  const [editing, setEditing] = useState(false)
  const [editValue, setEditValue] = useState(item.name.value)
  const [sheetOpen, setSheetOpen] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)
  const isChecked = item.checked.value

  // Harmless when rendered outside a DndContext/SortableContext (the checked
  // panel isn't wrapped in one) — dnd-kit's hooks fall back to inert defaults.
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: item.id,
    disabled: !draggable,
  })
  const dragStyle = {
    transform: CSS.Transform.toString(transform),
    transition: transition ?? undefined,
  }

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

  const section = sectionInfo(item.section.value)

  return (
    <li
      ref={setNodeRef}
      style={dragStyle}
      className={`flex items-center gap-1 pl-1 pr-1 py-1 min-h-[3.25rem] ${
        isDragging ? 'opacity-50' : ''
      }`}
    >
      {/* Drag handle — dedicated so it never collides with name-tap-to-edit or checkbox-tap-to-check */}
      {draggable && (
        <button
          type="button"
          {...attributes}
          {...listeners}
          aria-label={`Reorder ${item.name.value || 'item'}`}
          style={{ touchAction: 'none' }}
          className="pressable flex-shrink-0 min-h-touch min-w-touch flex items-center justify-center rounded-full text-ink-mute hover:text-ink-soft cursor-grab active:cursor-grabbing"
        >
          <svg className="w-3.5 h-3.5" viewBox="0 0 12 16" fill="currentColor" aria-hidden="true">
            <circle cx="3" cy="2" r="1.4" />
            <circle cx="9" cy="2" r="1.4" />
            <circle cx="3" cy="8" r="1.4" />
            <circle cx="9" cy="8" r="1.4" />
            <circle cx="3" cy="14" r="1.4" />
            <circle cx="9" cy="14" r="1.4" />
          </svg>
        </button>
      )}

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

      {/* Section — small tappable affordance opening the reassignment sheet */}
      <button
        type="button"
        onClick={() => setSheetOpen(true)}
        aria-label={`Change section, currently ${section.label}`}
        title={section.label}
        className={`pressable flex-shrink-0 min-h-touch min-w-touch flex items-center justify-center rounded-full border ${
          isChecked
            ? 'border-line text-ink-mute'
            : 'border-line text-ink-soft hover:border-marigold-deep/50'
        }`}
      >
        <svg className="w-4 h-4" viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <path
            d="M8.6 2H3.6a1 1 0 0 0-.7.3L2 3.2a1 1 0 0 0 0 1.4l7.4 7.4a1 1 0 0 0 1.4 0l3.2-3.2a1 1 0 0 0 0-1.4L9.3 2.3A1 1 0 0 0 8.6 2Z"
            stroke="currentColor"
            strokeWidth="1.3"
            strokeLinejoin="round"
            strokeLinecap="round"
          />
          <circle cx="5.5" cy="5.5" r="0.9" fill="currentColor" />
        </svg>
      </button>

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

      {sheetOpen && (
        <SectionSheet
          currentSection={item.section.value}
          onSelect={(code) => {
            setSection(item.id, code)
            setSheetOpen(false)
          }}
          onClose={() => setSheetOpen(false)}
        />
      )}
    </li>
  )
}
