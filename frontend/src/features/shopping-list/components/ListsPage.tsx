import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { apiClient } from '../../../api/client'

interface ShoppingList {
  id: string
  name: string
  ownerId: string
  members: { id: string; email: string; displayName: string; avatarUrl?: string | null }[]
  createdAt: string
}

export function ListsPage() {
  const [lists, setLists] = useState<ShoppingList[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showCreate, setShowCreate] = useState(false)
  const [newListName, setNewListName] = useState('')
  const [creating, setCreating] = useState(false)
  const navigate = useNavigate()

  useEffect(() => {
    apiClient
      .GET('/api/lists')
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

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault()
    if (!newListName.trim()) return
    setCreating(true)
    const { data, error: apiError } = await apiClient.POST('/api/lists', {
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
    <div className="min-h-screen bg-surface-muted">
      <header className="bg-white border-b border-surface-border px-4 py-4 flex items-center justify-between">
        <h1 className="text-xl font-bold text-gray-900">ShopMate</h1>
        <button
          onClick={() => setShowCreate(true)}
          className="px-4 py-2 bg-primary text-white rounded-lg font-medium hover:bg-primary-dark transition-colors text-sm"
        >
          New list
        </button>
      </header>

      <main className="max-w-lg mx-auto px-4 py-6">
        {isLoading && (
          <div className="flex justify-center py-12">
            <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
          </div>
        )}

        {error && (
          <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 text-sm">
            {error}
          </div>
        )}

        {!isLoading && !error && lists.length === 0 && (
          <div className="text-center py-16 text-gray-400">
            <p className="text-lg mb-2">No lists yet</p>
            <p className="text-sm">Tap "New list" to get started</p>
          </div>
        )}

        <ul className="space-y-3">
          {lists.map((list) => (
            <li key={list.id}>
              <button
                onClick={() => navigate(`/lists/${list.id}`)}
                className="w-full bg-white rounded-xl border border-surface-border p-4 text-left hover:border-primary transition-colors"
              >
                <div className="font-medium text-gray-900">{list.name}</div>
                <div className="text-sm text-gray-400 mt-1">
                  {list.members.length} member{list.members.length !== 1 ? 's' : ''}
                </div>
              </button>
            </li>
          ))}
        </ul>
      </main>

      {showCreate && (
        <div
          className="fixed inset-0 bg-black/40 flex items-end sm:items-center justify-center z-50 px-4 pb-4 sm:pb-0"
          onClick={(e) => {
            if (e.target === e.currentTarget) setShowCreate(false)
          }}
        >
          <div className="bg-white rounded-2xl shadow-xl p-6 w-full max-w-sm">
            <h2 className="text-lg font-semibold text-gray-900 mb-4">New list</h2>
            <form onSubmit={handleCreate}>
              <input
                type="text"
                value={newListName}
                onChange={(e) => setNewListName(e.target.value)}
                placeholder="List name"
                className="w-full border border-surface-border rounded-lg px-3 py-2 text-sm mb-4 focus:outline-none focus:ring-2 focus:ring-primary"
                autoFocus
                maxLength={100}
              />
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => { setShowCreate(false); setNewListName('') }}
                  className="flex-1 px-4 py-2 border border-surface-border rounded-lg text-sm font-medium text-gray-600 hover:bg-surface-muted transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={creating || !newListName.trim()}
                  className="flex-1 px-4 py-2 bg-primary text-white rounded-lg text-sm font-medium hover:bg-primary-dark transition-colors disabled:opacity-50"
                >
                  {creating ? 'Creating…' : 'Create'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
