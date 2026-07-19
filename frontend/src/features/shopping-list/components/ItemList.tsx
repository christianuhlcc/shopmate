import { useMemo } from 'react'
import {
  DndContext,
  KeyboardSensor,
  PointerSensor,
  TouchSensor,
  closestCenter,
  useDroppable,
  useSensor,
  useSensors,
  type DragEndEvent,
} from '@dnd-kit/core'
import { SortableContext, sortableKeyboardCoordinates, verticalListSortingStrategy } from '@dnd-kit/sortable'
import type { ItemField, ShoppingItem } from '../utils/lwwMerge'
import { groupBySection, type SectionBucket } from '../utils/sections'
import { ItemRow } from './ItemRow'

interface ItemListProps {
  items: ShoppingItem[]
  checkItem: (itemId: string, checked: boolean) => void
  updateItem: (itemId: string, field: ItemField, value: string) => void
  deleteItem: (itemId: string) => void
  setSection: (itemId: string, code: string) => void
  moveItemTo: (itemId: string, targetSection: string, targetIndex: number) => void
}

/** Droppable id prefix for a section's pad, so drops onto empty pad space (not onto another item) still resolve to a section. */
const SECTION_DROPPABLE_PREFIX = 'section:'

interface SectionGroupProps {
  bucket: SectionBucket
  checkItem: ItemListProps['checkItem']
  updateItem: ItemListProps['updateItem']
  deleteItem: ItemListProps['deleteItem']
  setSection: ItemListProps['setSection']
}

function SectionGroup({ bucket, checkItem, updateItem, deleteItem, setSection }: SectionGroupProps) {
  const { setNodeRef } = useDroppable({ id: `${SECTION_DROPPABLE_PREFIX}${bucket.code}` })
  const itemIds = useMemo(() => bucket.items.map((i) => i.id), [bucket.items])

  return (
    <div>
      <h2 className="text-label font-semibold text-ink-soft px-1 mb-2">{bucket.label}</h2>
      <SortableContext items={itemIds} strategy={verticalListSortingStrategy}>
        <ul
          ref={setNodeRef}
          className="bg-panel rounded-2xl border border-line divide-y divide-line overflow-hidden"
        >
          {bucket.items.map((item) => (
            <ItemRow
              key={item.id}
              item={item}
              checkItem={checkItem}
              updateItem={updateItem}
              deleteItem={deleteItem}
              setSection={setSection}
            />
          ))}
        </ul>
      </SortableContext>
    </div>
  )
}

export function ItemList({
  items,
  checkItem,
  updateItem,
  deleteItem,
  setSection,
  moveItemTo,
}: ItemListProps) {
  const unchecked = items.filter((i) => !i.checked.value)
  const checked = items.filter((i) => i.checked.value)
  const buckets = useMemo(() => groupBySection(unchecked), [unchecked])

  const sensors = useSensors(
    useSensor(PointerSensor),
    useSensor(TouchSensor, { activationConstraint: { delay: 250, tolerance: 5 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  )

  function handleDragEnd(event: DragEndEvent) {
    const { active, over } = event
    if (!over) return
    const itemId = String(active.id)
    const overId = String(over.id)
    if (itemId === overId) return

    if (overId.startsWith(SECTION_DROPPABLE_PREFIX)) {
      const targetSection = overId.slice(SECTION_DROPPABLE_PREFIX.length)
      const bucket = buckets.find((b) => b.code === targetSection)
      const targetIndex = bucket ? bucket.items.filter((i) => i.id !== itemId).length : 0
      moveItemTo(itemId, targetSection, targetIndex)
      return
    }

    const bucket = buckets.find((b) => b.items.some((i) => i.id === overId))
    if (!bucket) return
    const targetItems = bucket.items.filter((i) => i.id !== itemId)
    const idx = targetItems.findIndex((i) => i.id === overId)
    moveItemTo(itemId, bucket.code, idx >= 0 ? idx : targetItems.length)
  }

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

  return (
    <div className="space-y-4">
      {unchecked.length > 0 && (
        <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
          <div className="space-y-4">
            {buckets.map((bucket) => (
              <SectionGroup
                key={bucket.code}
                bucket={bucket}
                checkItem={checkItem}
                updateItem={updateItem}
                deleteItem={deleteItem}
                setSection={setSection}
              />
            ))}
          </div>
        </DndContext>
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
                setSection={setSection}
                draggable={false}
              />
            ))}
          </ul>
        </section>
      )}
    </div>
  )
}
