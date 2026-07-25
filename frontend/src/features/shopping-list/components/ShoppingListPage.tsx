import { Link, useParams } from 'react-router-dom'
import { useShoppingList } from '../hooks/useShoppingList'
import { AddItemForm } from './AddItemForm'
import { ItemList } from './ItemList'
import { ShoppingListPageSkeleton } from './ShoppingListPageSkeleton'

export function ShoppingListPage() {
  const { listId } = useParams<{ listId: string }>()
  const {
    items,
    listName,
    error,
    isLoading,
    addItem,
    updateItem,
    checkItem,
    deleteItem,
    setSection,
    moveItemTo,
  } = useShoppingList(listId!)

  if (isLoading) {
    return <ShoppingListPageSkeleton />
  }

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-ground px-4">
        <div className="bg-danger-tint border border-danger/25 rounded-2xl p-5 text-danger text-body max-w-sm text-center">
          {error}
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-ground pb-28">
      <header className="bg-marigold sticky top-0 z-header">
        <div className="max-w-lg mx-auto px-2 py-2.5 flex items-center gap-1">
          <Link
            to="/lists"
            aria-label="Back to lists"
            className="pressable flex-shrink-0 min-h-touch min-w-touch flex items-center justify-center rounded-full text-ink hover:bg-marigold-deep/25 focus-visible:outline-ink"
          >
            <svg className="w-5 h-5" viewBox="0 0 20 20" fill="none" aria-hidden="true">
              <path
                d="M12.5 4.5L7 10l5.5 5.5"
                stroke="currentColor"
                strokeWidth="2.25"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
          </Link>
          <h1 className="flex-1 text-title font-bold text-ink truncate">{listName}</h1>
        </div>
      </header>

      <main className="max-w-lg mx-auto px-5 py-5">
        <ItemList
          items={items}
          checkItem={checkItem}
          updateItem={updateItem}
          deleteItem={deleteItem}
          setSection={setSection}
          moveItemTo={moveItemTo}
        />
      </main>

      <AddItemForm addItem={addItem} />
    </div>
  )
}
