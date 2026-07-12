import { useState } from 'react'

interface AddItemFormProps {
  addItem: (name: string) => void
}

export function AddItemForm({ addItem }: AddItemFormProps) {
  const [name, setName] = useState('')

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    const trimmed = name.trim()
    if (!trimmed) return
    addItem(trimmed)
    setName('')
  }

  return (
    <div className="fixed bottom-0 left-0 right-0 bg-white border-t border-surface-border p-4">
      <form onSubmit={handleSubmit} className="flex gap-2 max-w-lg mx-auto">
        <input
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Add an item…"
          className="flex-1 border border-surface-border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
          maxLength={100}
        />
        <button
          type="submit"
          disabled={!name.trim()}
          className="px-4 py-2 bg-primary text-white rounded-lg text-sm font-medium hover:bg-primary-dark transition-colors disabled:opacity-50"
        >
          Add
        </button>
      </form>
    </div>
  )
}
