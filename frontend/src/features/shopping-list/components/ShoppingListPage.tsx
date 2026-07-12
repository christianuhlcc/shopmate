import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { apiClient } from '../../../api/client'
import { useShoppingList } from '../hooks/useShoppingList'
import { AddItemForm } from './AddItemForm'
import { ItemList } from './ItemList'

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
    moveItem,
  } = useShoppingList(listId!)

  const [shareEmail, setShareEmail] = useState('')
  const [showShare, setShowShare] = useState(false)
  const [sharing, setSharing] = useState(false)
  const [shareError, setShareError] = useState<string | null>(null)

  async function handleShare(e: React.FormEvent) {
    e.preventDefault()
    if (!shareEmail.trim()) return
    setSharing(true)
    setShareError(null)
    const { error: apiError } = await apiClient.POST('/api/lists/{listId}/members', {
      params: { path: { listId: listId! } },
      body: { email: shareEmail.trim() },
    })
    setSharing(false)
    if (apiError) {
      setShareError('Could not share list. Check the email address.')
    } else {
      setShareEmail('')
      setShowShare(false)
    }
  }

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-surface-muted">
        <div className="h-10 w-10 animate-spin rounded-full border-4 border-primary border-t-transparent" />
      </div>
    )
  }

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-surface-muted px-4">
        <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 text-sm">
          {error}
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-surface-muted pb-24">
      <header className="bg-white border-b border-surface-border px-4 py-4 flex items-center justify-between sticky top-0 z-10">
        <h1 className="text-lg font-bold text-gray-900 truncate">{listName}</h1>
        <button
          onClick={() => setShowShare(true)}
          className="px-3 py-1.5 border border-surface-border rounded-lg text-sm font-medium text-gray-600 hover:bg-surface-muted transition-colors"
        >
          Share
        </button>
      </header>

      <main className="max-w-lg mx-auto px-4 py-4">
        <ItemList
          items={items}
          checkItem={checkItem}
          updateItem={updateItem}
          deleteItem={deleteItem}
          moveItem={moveItem}
        />
      </main>

      <AddItemForm addItem={addItem} />

      {showShare && (
        <div
          className="fixed inset-0 bg-black/40 flex items-end sm:items-center justify-center z-50 px-4 pb-4 sm:pb-0"
          onClick={(e) => {
            if (e.target === e.currentTarget) setShowShare(false)
          }}
        >
          <div className="bg-white rounded-2xl shadow-xl p-6 w-full max-w-sm">
            <h2 className="text-lg font-semibold text-gray-900 mb-4">Share list</h2>
            <form onSubmit={handleShare}>
              <input
                type="email"
                value={shareEmail}
                onChange={(e) => setShareEmail(e.target.value)}
                placeholder="Email address"
                className="w-full border border-surface-border rounded-lg px-3 py-2 text-sm mb-4 focus:outline-none focus:ring-2 focus:ring-primary"
                autoFocus
              />
              {shareError && (
                <p className="text-red-600 text-sm mb-3">{shareError}</p>
              )}
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => { setShowShare(false); setShareEmail(''); setShareError(null) }}
                  className="flex-1 px-4 py-2 border border-surface-border rounded-lg text-sm font-medium text-gray-600 hover:bg-surface-muted transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={sharing || !shareEmail.trim()}
                  className="flex-1 px-4 py-2 bg-primary text-white rounded-lg text-sm font-medium hover:bg-primary-dark transition-colors disabled:opacity-50"
                >
                  {sharing ? 'Sharing…' : 'Share'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
