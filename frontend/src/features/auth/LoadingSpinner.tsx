export function LoadingSpinner() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-ground">
      <div
        className="h-10 w-10 animate-spin rounded-full border-4 border-marigold border-t-transparent"
        role="status"
        aria-label="Loading"
      />
    </div>
  )
}
