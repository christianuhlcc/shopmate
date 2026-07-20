import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { apiClient } from '../../../api/client'
import { GroupSheet } from '../../group/GroupSheet'

interface ShoppingList {
  id: string
  name: string
  ownerId: string
  groupId: string
  createdAt: string
}

export function ListsPage() {
  const [lists, setLists] = useState<ShoppingList[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showCreate, setShowCreate] = useState(false)
  const [newListName, setNewListName] = useState('')
  const [creating, setCreating] = useState(false)
  const [showGroup, setShowGroup] = useState(false)
  const navigate = useNavigate()

  useEffect(() => {
    apiClient
      .GET('/lists')
      .then(({ data, error: apiError }) => {
        if (apiError || !data) {
          setError('Failed to load lists.')
          return
        }
        setLists(data)
      })
      .catch(() => setError('Failed to load lists.'))
      .finally(() => setIsLoading(false))
  }, [])

  useEffect(() => {
    if (!showCreate) return
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') setShowCreate(false)
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [showCreate])

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault()
    if (!newListName.trim()) return
    setCreating(true)
    const { data, error: apiError } = await apiClient.POST('/lists', {
      body: { name: newListName.trim() },
    })
    setCreating(false)
    if (!apiError && data) {
      setLists((prev) => [...prev, data])
      setNewListName('')
      setShowCreate(false)
      navigate(`/lists/${data.id}`)
    }
  }

  return (
    <div className="min-h-screen bg-ground">
      <header className="bg-marigold sticky top-0 z-header">
        <div className="max-w-lg mx-auto px-5 py-4 flex items-center justify-between">
          <h1 className="text-title font-bold text-ink tracking-tight">ShopMate</h1>
          <div className="flex items-center gap-1">
            <button
              onClick={() => setShowGroup(true)}
              aria-label="Your group"
              className="pressable min-h-touch min-w-touch rounded-full flex items-center justify-center text-ink hover:bg-marigold-deep/25 focus-visible:outline-ink"
            >
              <svg className="w-5 h-5" viewBox="0 0 20 20" fill="none" aria-hidden="true">
                <circle cx="7" cy="6.5" r="2.75" stroke="currentColor" strokeWidth="1.6" />
                <path
                  d="M2 16c0-2.9 2.24-5 5-5s5 2.1 5 5"
                  stroke="currentColor"
                  strokeWidth="1.6"
                  strokeLinecap="round"
                />
                <circle cx="14.5" cy="7.5" r="2.1" stroke="currentColor" strokeWidth="1.6" />
                <path
                  d="M12.5 11c2.35.2 4 2.1 4 4.6"
                  stroke="currentColor"
                  strokeWidth="1.6"
                  strokeLinecap="round"
                />
              </svg>
            </button>
            <button
              onClick={() => setShowCreate(true)}
              className="pressable min-h-touch px-5 py-2 bg-ink text-panel rounded-full text-body font-semibold hover:bg-ink/90 focus-visible:outline-ink"
            >
              New list
            </button>
          </div>
        </div>
      </header>

      <main className="max-w-lg mx-auto px-5 py-6">
        {isLoading && (
          <div role="status" aria-label="Loading lists" className="space-y-3">
            {[0, 1, 2].map((i) => (
              <div
                key={i}
                className="bg-panel rounded-2xl border border-line p-5 animate-pulse"
              >
                <div className="h-4 w-2/5 rounded bg-line" />
                <div className="mt-3 h-3 w-1/4 rounded bg-line/70" />
              </div>
            ))}
          </div>
        )}

        {error && (
          <div className="bg-danger-tint border border-danger/25 rounded-2xl p-4 text-danger text-body">
            {error}
          </div>
        )}

        {!isLoading && !error && lists.length === 0 && (
          <div className="text-center py-16 px-6">
            <EmptyPadGlyph />
            <p className="text-item font-semibold text-ink mt-5">No lists yet</p>
            <p className="text-body text-ink-soft mt-1 max-w-[32ch] mx-auto">
              Start one for the weekly groceries, then share it — everyone edits
              together and changes show up instantly.
            </p>
            <button
              onClick={() => setShowCreate(true)}
              className="pressable mt-6 min-h-touch px-6 py-2.5 bg-marigold text-ink rounded-full text-body font-semibold hover:bg-marigold-deep"
            >
              Create your first list
            </button>
          </div>
        )}

        <ul className="space-y-3">
          {lists.map((list) => (
            <li key={list.id}>
              <button
                onClick={() => navigate(`/lists/${list.id}`)}
                className="pressable w-full bg-panel rounded-2xl border border-line p-5 text-left hover:border-marigold-deep/50 hover:bg-marigold-faint/40 flex items-center gap-4"
              >
                <span className="flex-1 min-w-0">
                  <span className="block text-item font-semibold text-ink truncate">
                    {list.name}
                  </span>
                </span>
                <svg
                  className="w-4 h-4 flex-shrink-0 text-ink-mute"
                  viewBox="0 0 16 16"
                  fill="none"
                  aria-hidden="true"
                >
                  <path
                    d="M6 3.5L10.5 8 6 12.5"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                </svg>
              </button>
            </li>
          ))}
        </ul>
      </main>

      {showCreate && (
        <div
          className="sheet-backdrop fixed inset-0 bg-ink/40 flex items-end sm:items-center justify-center z-overlay px-4 pb-[max(1rem,env(safe-area-inset-bottom))] sm:pb-0"
          onClick={(e) => {
            if (e.target === e.currentTarget) setShowCreate(false)
          }}
        >
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="create-list-title"
            className="sheet-panel bg-panel rounded-2xl shadow-xl p-6 w-full max-w-sm z-sheet"
          >
            <h2 id="create-list-title" className="text-title font-semibold text-ink mb-4">
              New list
            </h2>
            <form onSubmit={handleCreate}>
              <input
                type="text"
                value={newListName}
                onChange={(e) => setNewListName(e.target.value)}
                placeholder="List name"
                className="w-full border border-line rounded-xl px-4 py-3 text-body text-ink placeholder:text-ink-mute mb-4 focus:outline-none focus:ring-2 focus:ring-marigold-deep"
                autoFocus
                maxLength={100}
              />
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => { setShowCreate(false); setNewListName('') }}
                  className="pressable flex-1 min-h-touch px-4 py-2.5 border border-line rounded-full text-body font-semibold text-ink-soft hover:bg-ground"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={creating || !newListName.trim()}
                  className="pressable flex-1 min-h-touch px-4 py-2.5 bg-marigold text-ink rounded-full text-body font-semibold hover:bg-marigold-deep disabled:opacity-50 disabled:hover:bg-marigold"
                >
                  {creating ? 'Creating…' : 'Create'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {showGroup && <GroupSheet onClose={() => setShowGroup(false)} />}
    </div>
  )
}

function EmptyPadGlyph() {
  return (
    <svg
      className="mx-auto w-16 h-16"
      viewBox="0 0 64 64"
      fill="none"
      aria-hidden="true"
    >
      <rect x="12" y="8" width="40" height="48" rx="8" fill="oklch(0.93 0.07 85)" />
      <path
        d="M22 24h20M22 33h14M22 42h17"
        stroke="oklch(0.68 0.145 68)"
        strokeWidth="3"
        strokeLinecap="round"
      />
    </svg>
  )
}
