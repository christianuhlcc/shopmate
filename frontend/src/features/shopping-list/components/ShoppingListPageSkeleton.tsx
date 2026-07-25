import { Link } from 'react-router-dom'

// Static shell shown both while the ShoppingListPage chunk is still
// downloading (Suspense fallback in main.tsx) and while its own list fetch
// is in flight (useShoppingList's isLoading) — one shared component so the
// two phases render pixel-identical markup instead of visibly swapping
// layouts.
export function ShoppingListPageSkeleton() {
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
