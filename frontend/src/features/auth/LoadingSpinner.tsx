export function LoadingSpinner() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-surface-muted">
      <div
        className="h-10 w-10 animate-spin rounded-full border-4 border-primary border-t-transparent"
        role="status"
        aria-label="Loading"
      />
    </div>
  )
}
