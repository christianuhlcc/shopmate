import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
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
    setSection,
    moveItemTo,
  } = useShoppingList(listId!)

  const [shareEmail, setShareEmail] = useState('')
  const [showShare, setShowShare] = useState(false)
  const [sharing, setSharing] = useState(false)
  const [shareError, setShareError] = useState<string | null>(null)

  useEffect(() => {
    if (!showShare) return
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') setShowShare(false)
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [showShare])

  async function handleShare(e: React.FormEvent) {
    e.preventDefault()
    if (!shareEmail.trim()) return
    setSharing(true)
    setShareError(null)
    const { error: apiError } = await apiClient.POST('/lists/{listId}/members', {
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
      <div className="min-h-screen bg-ground">
        <header className="bg-marigold sticky top-0 z-header">
          <div className="max-w-lg mx-auto px-2 py-2.5 flex items-center gap-1 min-h-[3.75rem]">
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
            <div className="h-5 w-36 rounded bg-marigold-deep/30 animate-pulse" />
          </div>
        </header>
        <main
          role="status"
          aria-label="Loading list"
          className="max-w-lg mx-auto px-5 py-5"
        >
          <div className="bg-panel rounded-2xl border border-line divide-y divide-line animate-pulse">
            {[0, 1, 2, 3].map((i) => (
              <div key={i} className="flex items-center gap-3 px-4 py-4">
                <div className="h-6 w-6 rounded-full bg-line" />
                <div className="h-4 rounded bg-line" style={{ width: `${55 - i * 8}%` }} />
              </div>
            ))}
          </div>
        </main>
      </div>
    )
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
          <button
            onClick={() => setShowShare(true)}
            className="pressable flex-shrink-0 min-h-touch px-5 py-2 bg-ink text-panel rounded-full text-body font-semibold hover:bg-ink/90 focus-visible:outline-ink mr-2"
          >
            Share
          </button>
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

      {showShare && (
        <div
          className="sheet-backdrop fixed inset-0 bg-ink/40 flex items-end sm:items-center justify-center z-overlay px-4 pb-[max(1rem,env(safe-area-inset-bottom))] sm:pb-0"
          onClick={(e) => {
            if (e.target === e.currentTarget) setShowShare(false)
          }}
        >
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="share-list-title"
            className="sheet-panel bg-panel rounded-2xl shadow-xl p-6 w-full max-w-sm z-sheet"
          >
            <h2 id="share-list-title" className="text-title font-semibold text-ink mb-1">
              Share list
            </h2>
            <p className="text-label text-ink-soft mb-4">
              They'll see every change the moment it happens.
            </p>
            <form onSubmit={handleShare}>
              <input
                type="email"
                value={shareEmail}
                onChange={(e) => setShareEmail(e.target.value)}
                placeholder="Email address"
                className="w-full border border-line rounded-xl px-4 py-3 text-body text-ink placeholder:text-ink-mute mb-4 focus:outline-none focus:ring-2 focus:ring-marigold-deep"
                autoFocus
              />
              {shareError && (
                <p className="text-danger text-label mb-3">{shareError}</p>
              )}
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => { setShowShare(false); setShareEmail(''); setShareError(null) }}
                  className="pressable flex-1 min-h-touch px-4 py-2.5 border border-line rounded-full text-body font-semibold text-ink-soft hover:bg-ground"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={sharing || !shareEmail.trim()}
                  className="pressable flex-1 min-h-touch px-4 py-2.5 bg-marigold text-ink rounded-full text-body font-semibold hover:bg-marigold-deep disabled:opacity-50 disabled:hover:bg-marigold"
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
