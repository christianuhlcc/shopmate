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
    <div className="fixed bottom-0 left-0 right-0 bg-marigold z-addbar pb-[env(safe-area-inset-bottom)]">
      <form
        onSubmit={handleSubmit}
        className="flex gap-2 max-w-lg mx-auto px-5 py-3.5"
      >
        <input
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Add an item…"
          aria-label="Add an item"
          className="flex-1 min-h-touch bg-panel border-0 rounded-full px-5 py-2.5 text-item text-ink placeholder:text-ink-mute focus:outline-none focus:ring-2 focus:ring-ink shadow-sm"
          maxLength={100}
        />
        <button
          type="submit"
          disabled={!name.trim()}
          aria-label="Add item"
          className="pressable flex-shrink-0 min-h-touch min-w-touch flex items-center justify-center bg-ink text-marigold rounded-full hover:bg-ink/90 disabled:bg-ink/20 disabled:text-panel focus-visible:outline-ink shadow-sm"
        >
          <svg className="w-5 h-5" viewBox="0 0 20 20" fill="none" aria-hidden="true">
            <path
              d="M10 4v12M4 10h12"
              stroke="currentColor"
              strokeWidth="2.25"
              strokeLinecap="round"
            />
          </svg>
        </button>
      </form>
    </div>
  )
}
