// Static shell shown both while the ListsPage chunk is still downloading
// (Suspense fallback in main.tsx) and while its own /lists fetch is in
// flight (ListsPage's isLoading branch) — one shared component so the two
// phases render pixel-identical markup instead of visibly swapping layouts.
export function ListsPageSkeleton() {
  return (
    <div className="min-h-screen bg-ground">
      <header className="bg-marigold sticky top-0 z-header">
        <div className="max-w-lg mx-auto px-5 py-4 flex items-center justify-between">
          <h1 className="text-title font-bold text-ink tracking-tight">ShopMate</h1>
          {/* Stand-ins for the group icon button and the "New list" pill. Without
              them the header is 18px shorter than the loaded one and the whole
              page visibly jumps down the moment the lists arrive. */}
          <div className="flex items-center gap-1">
            <div className="min-h-touch min-w-touch flex items-center justify-center">
              <div className="h-5 w-5 rounded-full bg-marigold-deep/30 animate-pulse" />
            </div>
            <div className="min-h-touch w-[5.75rem] rounded-full bg-marigold-deep/30 animate-pulse" />
          </div>
        </div>
      </header>

      <main className="max-w-lg mx-auto px-5 py-6">
        <div role="status" aria-label="Loading lists" className="space-y-3">
          {[0, 1, 2].map((i) => (
            <div
              key={i}
              className="bg-panel rounded-2xl border border-line p-5 animate-pulse"
            >
              {/* One bar in a 24px line box: a list row renders only its name,
                  at text-item's 1.5rem line height. A second placeholder line
                  here would promise content that never arrives and leave the
                  rows taller than the real ones. */}
              <div className="h-6 flex items-center">
                <div className="h-4 w-2/5 rounded bg-line" />
              </div>
            </div>
          ))}
        </div>
      </main>
    </div>
  )
}
